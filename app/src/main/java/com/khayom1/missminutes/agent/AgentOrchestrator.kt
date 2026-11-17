package com.khayom1.missminutes.agent

import com.khayom1.missminutes.plugins.PluginExecutionRequest
import com.khayom1.missminutes.plugins.PluginExecutionResult

/** A single, explicitly allowlisted plugin invocation in an inspectable task graph. */
data class AgentAction(
    val request: PluginExecutionRequest,
    val approvalRequired: Boolean = false
)

data class AgentTaskNode(
    val id: String,
    val dependsOn: Set<String> = emptySet(),
    val action: AgentAction
)

data class AgentTaskPlan(
    val id: String,
    val nodes: List<AgentTaskNode>,
    val maxSteps: Int = minOf(12, nodes.size),
    val deadlineAtMillis: Long
)

enum class AgentNodeStatus {
    PENDING,
    WAITING_APPROVAL,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    RECOVERY_REQUIRED
}

enum class AgentTaskStatus {
    READY,
    PAUSED,
    WAITING_APPROVAL,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED,
    RECOVERY_REQUIRED
}

data class AgentNodeState(
    val nodeId: String,
    val status: AgentNodeStatus = AgentNodeStatus.PENDING,
    val message: String? = null,
    val idempotencyKey: String,
    val approved: Boolean = false
)

data class AgentTaskState(
    val plan: AgentTaskPlan,
    val nodes: Map<String, AgentNodeState>,
    val status: AgentTaskStatus,
    val completedSteps: Int = 0,
    val updatedAtMillis: Long
)

sealed interface AgentAdvanceResult {
    data class Progress(val state: AgentTaskState, val nodeId: String) : AgentAdvanceResult
    data class BlockedForApproval(val state: AgentTaskState, val nodeId: String) : AgentAdvanceResult
    data class Finished(val state: AgentTaskState) : AgentAdvanceResult
    data class Rejected(val reason: String) : AgentAdvanceResult
}

fun interface AgentActionExecutor {
    fun execute(request: PluginExecutionRequest, idempotencyKey: String): PluginExecutionResult
}

/**
 * Durable storage is injected by the platform layer. Implementations must commit each
 * state transition atomically. The engine never assumes that in-memory state is durable.
 */
interface AgentTaskStateStore {
    fun create(state: AgentTaskState): Boolean
    fun load(taskId: String): AgentTaskState?
    fun save(state: AgentTaskState)
}

class InMemoryAgentTaskStateStore : AgentTaskStateStore {
    private val states = mutableMapOf<String, AgentTaskState>()

    @Synchronized
    override fun create(state: AgentTaskState): Boolean {
        if (states.containsKey(state.plan.id)) return false
        states[state.plan.id] = state
        return true
    }

    @Synchronized
    override fun load(taskId: String): AgentTaskState? = states[taskId]

    @Synchronized
    override fun save(state: AgentTaskState) {
        check(states.containsKey(state.plan.id)) { "Unknown agent task." }
        states[state.plan.id] = state
    }
}

/**
 * Bounded, sequential task-graph runner. It does not interpret model output or execute
 * arbitrary code: every node contains a typed request for the closed-world plugin registry.
 */
class AgentOrchestrator(
    private val store: AgentTaskStateStore,
    private val executor: AgentActionExecutor,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxPlanNodes: Int = 24
) {
    init {
        require(maxPlanNodes in 1..64)
    }

    @Synchronized
    fun create(plan: AgentTaskPlan): AgentTaskState {
        validate(plan)
        val now = clockMillis()
        require(plan.deadlineAtMillis > now && plan.deadlineAtMillis - now <= MAX_TASK_DURATION_MILLIS) { "Task deadline must be within the 24-hour execution window." }
        val nodes = plan.nodes.associate { node ->
            node.id to AgentNodeState(node.id, idempotencyKey = "${plan.id}:${node.id}")
        }
        val state = AgentTaskState(plan, nodes, AgentTaskStatus.READY, updatedAtMillis = now)
        check(store.create(state)) { "Task id already exists." }
        return state
    }

    @Synchronized
    fun state(taskId: String): AgentTaskState? = store.load(taskId)

    @Synchronized
    fun pause(taskId: String): AgentTaskState = update(taskId) { current ->
        if (current.status in TERMINAL) current else current.copy(status = AgentTaskStatus.PAUSED)
    }

    @Synchronized
    fun resume(taskId: String): AgentTaskState = update(taskId) { current ->
        if (current.status != AgentTaskStatus.PAUSED) current
        else current.copy(status = deriveStatus(current.nodes, current.completedSteps))
    }

    @Synchronized
    fun cancel(taskId: String): AgentTaskState = update(taskId) { current ->
        if (current.status in TERMINAL) current
        else {
            val nodes = current.nodes.mapValues { (_, node) ->
                if (node.status == AgentNodeStatus.PENDING ||
                    node.status == AgentNodeStatus.WAITING_APPROVAL
                ) node.copy(status = AgentNodeStatus.CANCELLED, message = "Cancelled by user.")
                else node
            }
            current.copy(nodes = nodes, status = AgentTaskStatus.CANCELLED)
        }
    }

    @Synchronized
    fun approve(taskId: String, nodeId: String): AgentTaskState = update(taskId) { current ->
        require(current.status != AgentTaskStatus.PAUSED && current.status !in TERMINAL) {
            "Task cannot accept approval in its current state."
        }
        val planNode = current.plan.nodes.firstOrNull { it.id == nodeId }
            ?: throw IllegalArgumentException("Unknown task node.")
        require(planNode.action.approvalRequired) { "This action does not require approval." }
        val node = current.nodes.getValue(nodeId)
        require(node.status == AgentNodeStatus.WAITING_APPROVAL) { "Node is not waiting for approval." }
        val nodes = current.nodes + (nodeId to node.copy(status = AgentNodeStatus.PENDING, message = "Approved by user.", approved = true))
        current.copy(nodes = nodes, status = AgentTaskStatus.READY)
    }

    /** Executes at most one node. Call repeatedly from a lifecycle-aware worker. */
    @Synchronized
    fun advance(taskId: String): AgentAdvanceResult {
        var current = store.load(taskId) ?: return AgentAdvanceResult.Rejected("Unknown task.")
        if (current.status == AgentTaskStatus.PAUSED) return AgentAdvanceResult.Finished(current)
        if (current.status in TERMINAL) return AgentAdvanceResult.Finished(current)

        val now = clockMillis()
        if (now >= current.plan.deadlineAtMillis) {
            current = current.copy(status = AgentTaskStatus.EXPIRED, updatedAtMillis = now)
            store.save(current)
            return AgentAdvanceResult.Finished(current)
        }
        if (current.completedSteps >= current.plan.maxSteps) {
            current = current.copy(status = AgentTaskStatus.FAILED, updatedAtMillis = now)
            store.save(current)
            return AgentAdvanceResult.Finished(current)
        }

        val interrupted = current.nodes.values.firstOrNull { it.status == AgentNodeStatus.RUNNING }
        if (interrupted != null) {
            val nodes = current.nodes + (interrupted.nodeId to interrupted.copy(
                status = AgentNodeStatus.RECOVERY_REQUIRED,
                message = "Execution was interrupted; verify the external effect before any manual retry."
            ))
            current = current.copy(nodes = nodes, status = AgentTaskStatus.RECOVERY_REQUIRED, updatedAtMillis = now)
            store.save(current)
            return AgentAdvanceResult.Finished(current)
        }

        val recovery = current.nodes.values.firstOrNull { it.status == AgentNodeStatus.RECOVERY_REQUIRED }
        if (recovery != null) {
            current = current.copy(status = AgentTaskStatus.RECOVERY_REQUIRED, updatedAtMillis = now)
            store.save(current)
            return AgentAdvanceResult.Finished(current)
        }

        val pending = current.plan.nodes.firstOrNull { node ->
            current.nodes.getValue(node.id).status == AgentNodeStatus.PENDING &&
                node.dependsOn.all { current.nodes.getValue(it).status == AgentNodeStatus.SUCCEEDED }
        }
        if (pending == null) {
            val waiting = current.nodes.values.firstOrNull { it.status == AgentNodeStatus.WAITING_APPROVAL }
            if (waiting != null) {
                current = current.copy(status = AgentTaskStatus.WAITING_APPROVAL, updatedAtMillis = now)
                store.save(current)
                return AgentAdvanceResult.BlockedForApproval(current, waiting.nodeId)
            }
            val failedDependency = current.plan.nodes.firstOrNull { node ->
                current.nodes.getValue(node.id).status == AgentNodeStatus.PENDING &&
                    node.dependsOn.any { current.nodes.getValue(it).status in setOf(
                        AgentNodeStatus.FAILED, AgentNodeStatus.CANCELLED, AgentNodeStatus.RECOVERY_REQUIRED
                    ) }
            }
            if (failedDependency != null) {
                val failed = current.nodes + (failedDependency.id to current.nodes.getValue(failedDependency.id)
                    .copy(status = AgentNodeStatus.FAILED, message = "A dependency did not succeed."))
                current = current.copy(nodes = failed, status = AgentTaskStatus.FAILED, updatedAtMillis = now)
                store.save(current)
                return AgentAdvanceResult.Finished(current)
            }
            current = current.copy(
                status = if (current.nodes.values.all { it.status == AgentNodeStatus.SUCCEEDED }) {
                    AgentTaskStatus.SUCCEEDED
                } else AgentTaskStatus.FAILED,
                updatedAtMillis = now
            )
            store.save(current)
            return AgentAdvanceResult.Finished(current)
        }

        val pendingState = current.nodes.getValue(pending.id)
        if (pending.action.approvalRequired && !pendingState.approved) {
            val nodes = current.nodes + (pending.id to current.nodes.getValue(pending.id)
                .copy(status = AgentNodeStatus.WAITING_APPROVAL, message = "User approval required."))
            current = current.copy(nodes = nodes, status = AgentTaskStatus.WAITING_APPROVAL, updatedAtMillis = now)
            store.save(current)
            return AgentAdvanceResult.BlockedForApproval(current, pending.id)
        }

        val running = current.nodes + (pending.id to current.nodes.getValue(pending.id)
            .copy(status = AgentNodeStatus.RUNNING, message = null))
        current = current.copy(nodes = running, status = AgentTaskStatus.RUNNING, updatedAtMillis = now)
        store.save(current)

        val result = try {
            executor.execute(
                pending.action.request.copy(userConfirmed = pendingState.approved || pending.action.request.userConfirmed),
                running.getValue(pending.id).idempotencyKey
            )
        } catch (_: Exception) {
            PluginExecutionResult(false, "Action failed safely.", pending.action.requestCapability())
        }

        val finalNode = running.getValue(pending.id).copy(
            status = if (result.success) AgentNodeStatus.SUCCEEDED else AgentNodeStatus.FAILED,
            message = result.message.take(MAX_RESULT_CHARACTERS)
        )
        val finalNodes = running + (pending.id to finalNode)
        val completed = current.completedSteps + 1
        val nextStatus = when {
            !result.success -> AgentTaskStatus.FAILED
            finalNodes.values.all { it.status == AgentNodeStatus.SUCCEEDED } -> AgentTaskStatus.SUCCEEDED
            else -> AgentTaskStatus.READY
        }
        current = current.copy(
            nodes = finalNodes,
            status = nextStatus,
            completedSteps = completed,
            updatedAtMillis = clockMillis()
        )
        store.save(current)
        return AgentAdvanceResult.Progress(current, pending.id)
    }

    private fun update(taskId: String, transform: (AgentTaskState) -> AgentTaskState): AgentTaskState {
        val current = store.load(taskId) ?: throw IllegalArgumentException("Unknown task.")
        val updated = transform(current).copy(updatedAtMillis = clockMillis())
        store.save(updated)
        return updated
    }

    private fun validate(plan: AgentTaskPlan) {
        require(ID.matches(plan.id)) { "Invalid task id." }
        require(plan.nodes.isNotEmpty() && plan.nodes.size <= maxPlanNodes) { "Task graph size is outside its budget." }
        require(plan.maxSteps in 1..plan.nodes.size) { "Step budget must fit the task graph." }
        require(plan.nodes.map { it.id }.distinct().size == plan.nodes.size) { "Duplicate task node id." }
        val ids = plan.nodes.map { it.id }.toSet()
        plan.nodes.forEach { node ->
            require(ID.matches(node.id)) { "Invalid task node id." }
            require(node.id !in node.dependsOn) { "A node cannot depend on itself." }
            require(node.dependsOn.all { it in ids }) { "Task graph contains an unknown dependency." }
            require(node.action.request.pluginId.isNotBlank() && node.action.request.actionId.isNotBlank())
        }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val byId = plan.nodes.associateBy { it.id }
        fun visit(id: String) {
            if (id in visited) return
            require(visiting.add(id)) { "Task graph contains a dependency cycle." }
            byId.getValue(id).dependsOn.forEach(::visit)
            visiting.remove(id)
            visited.add(id)
        }
        ids.forEach(::visit)
    }

    private fun deriveStatus(nodes: Map<String, AgentNodeState>, completed: Int): AgentTaskStatus = when {
        nodes.values.any { it.status == AgentNodeStatus.RECOVERY_REQUIRED } -> AgentTaskStatus.RECOVERY_REQUIRED
        nodes.values.any { it.status == AgentNodeStatus.WAITING_APPROVAL } -> AgentTaskStatus.WAITING_APPROVAL
        nodes.values.all { it.status == AgentNodeStatus.SUCCEEDED } -> AgentTaskStatus.SUCCEEDED
        completed > 0 -> AgentTaskStatus.READY
        else -> AgentTaskStatus.READY
    }

    private fun AgentAction.requestCapability() =
        com.khayom1.missminutes.plugins.PluginCapability.DEVICE_INFORMATION

    private companion object {
        val ID = Regex("^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}$")
        const val MAX_RESULT_CHARACTERS = 1_000
        const val MAX_TASK_DURATION_MILLIS = 24 * 60 * 60 * 1_000L
        val TERMINAL = setOf(
            AgentTaskStatus.SUCCEEDED,
            AgentTaskStatus.FAILED,
            AgentTaskStatus.CANCELLED,
            AgentTaskStatus.EXPIRED,
            AgentTaskStatus.RECOVERY_REQUIRED
        )
    }
}
