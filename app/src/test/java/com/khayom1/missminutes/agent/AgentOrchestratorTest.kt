package com.khayom1.missminutes.agent

import com.khayom1.missminutes.plugins.PluginAccessDecision
import com.khayom1.missminutes.plugins.PluginCapability
import com.khayom1.missminutes.plugins.PluginExecutionRequest
import com.khayom1.missminutes.plugins.PluginExecutionResult
import com.khayom1.missminutes.plugins.PluginExecutionStatus
import com.khayom1.missminutes.plugins.PluginToolDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AgentOrchestratorTest {
    private var now = 1_000L

    private fun plan(
        nodes: List<AgentTaskNode>,
        maxSteps: Int = nodes.size,
        deadline: Long = 10_000L,
        id: String = "task_1"
    ) = AgentTaskPlan(id, nodes, maxSteps, deadline)

    private fun node(id: String, dependencies: Set<String> = emptySet(), approval: Boolean = false) =
        AgentTaskNode(
            id = id,
            dependsOn = dependencies,
            action = AgentAction(
                PluginExecutionRequest("device_status", "read_info"),
                approvalRequired = approval
            )
        )

    private class ReadOnlyCatalogExecutor(
        private val action: (PluginExecutionRequest, String) -> PluginExecutionResult
    ) : AgentActionExecutor, AgentActionCatalogProvider {
        override fun availableTools() = listOf(
            PluginToolDescriptor(
                toolName = "plugin_device_status_read_info",
                pluginId = "device_status",
                actionId = "read_info",
                pluginName = "Device status",
                description = "Read device status.",
                capability = PluginCapability.DEVICE_INFORMATION,
                arguments = emptyMap(),
                requiresConfirmation = false,
                connected = true,
                access = PluginAccessDecision.Granted,
                sideEffect = false
            )
        )

        override fun executeToolCall(
            toolName: String,
            argumentsJson: String,
            userConfirmed: Boolean
        ) = PluginExecutionResult(false, "not used", PluginCapability.DEVICE_INFORMATION)

        override fun execute(
            request: PluginExecutionRequest,
            idempotencyKey: String
        ) = action(request, idempotencyKey)
    }

    private fun orchestrator(
        store: AgentTaskStateStore = InMemoryAgentTaskStateStore(),
        action: (PluginExecutionRequest, String) -> PluginExecutionResult
    ) = AgentOrchestrator(store, ReadOnlyCatalogExecutor(action), clockMillis = { now })

    private class ConfirmationFloorExecutor : AgentActionExecutor, AgentActionCatalogProvider {
        val requests = mutableListOf<PluginExecutionRequest>()

        override fun availableTools() = listOf(
            PluginToolDescriptor(
                toolName = "plugin_device_status_read_info",
                pluginId = "device_status",
                actionId = "read_info",
                pluginName = "Device status",
                description = "Read device status.",
                capability = PluginCapability.DEVICE_INFORMATION,
                arguments = emptyMap(),
                requiresConfirmation = true,
                connected = true,
                access = PluginAccessDecision.Granted,
                sideEffect = true
            )
        )

        override fun executeToolCall(
            toolName: String,
            argumentsJson: String,
            userConfirmed: Boolean
        ) = PluginExecutionResult(false, "not used", PluginCapability.DEVICE_INFORMATION)

        override fun execute(
            request: PluginExecutionRequest,
            idempotencyKey: String
        ): PluginExecutionResult {
            requests += request
            return PluginExecutionResult(true, "done", PluginCapability.DEVICE_INFORMATION)
        }
    }

    @Test
    fun executesGraphInDependencyOrderAndUsesStableIdempotencyKeys() {
        val calls = mutableListOf<String>()
        val engine = orchestrator { _, key ->
            calls += key
            PluginExecutionResult(true, "ok", PluginCapability.DEVICE_INFORMATION)
        }
        engine.create(plan(listOf(node("first"), node("second", setOf("first")))))

        val first = engine.advance("task_1") as AgentAdvanceResult.Progress
        assertEquals("first", first.nodeId)
        assertEquals(AgentTaskStatus.READY, first.state.status)
        val second = engine.advance("task_1") as AgentAdvanceResult.Progress
        assertEquals("second", second.nodeId)
        assertEquals(AgentTaskStatus.SUCCEEDED, second.state.status)
        assertEquals(listOf("task_1:first", "task_1:second"), calls)
        assertTrue((engine.advance("task_1") as AgentAdvanceResult.Finished).state.status == AgentTaskStatus.SUCCEEDED)
    }

    @Test
    fun restoresMostRecentlyUpdatedTaskFromTheStore() {
        val engine = orchestrator { _, _ ->
            PluginExecutionResult(true, "ok", PluginCapability.DEVICE_INFORMATION)
        }
        engine.create(plan(listOf(node("first"))))
        now += 1_000L
        engine.create(plan(listOf(node("second")), id = "task_2"))
        assertEquals("task_2", engine.latestTask()?.plan?.id)
    }

    @Test
    fun sensitiveActionWaitsForExplicitApproval() {
        var calls = 0
        var confirmed = false
        val engine = orchestrator { request, _ ->
            calls++
            confirmed = request.userConfirmed
            PluginExecutionResult(true, "done", PluginCapability.DEVICE_INFORMATION)
        }
        engine.create(plan(listOf(node("sensitive", approval = true))))
        val blocked = engine.advance("task_1") as AgentAdvanceResult.BlockedForApproval
        assertEquals("sensitive", blocked.nodeId)
        assertEquals(0, calls)

        engine.approve("task_1", "sensitive")
        val result = engine.advance("task_1") as AgentAdvanceResult.Progress
        assertEquals(AgentTaskStatus.SUCCEEDED, result.state.status)
        assertEquals(1, calls)
        assertTrue(confirmed)
        assertFalse(result.state.nodes.getValue("sensitive").approved)
        assertEquals(null, result.state.nodes.getValue("sensitive").approvedAtMillis)
    }

    @Test
    fun modelSuppliedConfirmationCannotBypassCompiledHandlerConfirmationFloor() {
        val executor = ConfirmationFloorExecutor()
        val engine = AgentOrchestrator(InMemoryAgentTaskStateStore(), executor, clockMillis = { now })
        val request = PluginExecutionRequest(
            pluginId = "device_status",
            actionId = "read_info",
            userConfirmed = true
        )
        engine.create(plan(listOf(
            AgentTaskNode("sensitive", action = AgentAction(request, approvalRequired = false))
        )))

        assertFalse(engine.state("task_1")!!.plan.nodes.single().action.request.userConfirmed)
        val blocked = engine.advance("task_1") as AgentAdvanceResult.BlockedForApproval
        assertEquals("sensitive", blocked.nodeId)
        assertTrue(executor.requests.isEmpty())

        engine.approve("task_1", "sensitive")
        val completed = engine.advance("task_1") as AgentAdvanceResult.Progress
        assertTrue(executor.requests.single().userConfirmed)
        assertFalse(completed.state.nodes.getValue("sensitive").approved)
    }

    @Test
    fun expiredApprovalMustBeGrantedAgainBeforeExecution() {
        var calls = 0
        val engine = orchestrator { request, _ ->
            calls++
            assertTrue(request.userConfirmed)
            PluginExecutionResult(true, "done", PluginCapability.DEVICE_INFORMATION)
        }
        engine.create(plan(listOf(node("sensitive", approval = true)), deadline = 100_000L))
        engine.advance("task_1")
        engine.approve("task_1", "sensitive")

        now += 15_001L
        val blockedAgain = engine.advance("task_1") as AgentAdvanceResult.BlockedForApproval
        assertEquals("sensitive", blockedAgain.nodeId)
        assertFalse(blockedAgain.state.nodes.getValue("sensitive").approved)
        assertEquals(0, calls)
    }

    @Test
    fun pausingTaskInvalidatesAnyPendingOneShotApproval() {
        var calls = 0
        val engine = orchestrator { _, _ ->
            calls++
            PluginExecutionResult(true, "done", PluginCapability.DEVICE_INFORMATION)
        }
        engine.create(plan(listOf(node("sensitive", approval = true))))
        engine.advance("task_1")
        engine.approve("task_1", "sensitive")
        engine.pause("task_1")
        engine.resume("task_1")

        val blockedAgain = engine.advance("task_1") as AgentAdvanceResult.BlockedForApproval
        assertEquals("sensitive", blockedAgain.nodeId)
        assertEquals(0, calls)
    }

    @Test
    fun pauseResumeAndCancelAreEnforced() {
        var calls = 0
        val engine = orchestrator { _, _ ->
            calls++
            PluginExecutionResult(true, "ok", PluginCapability.DEVICE_INFORMATION)
        }
        engine.create(plan(listOf(node("one"), node("two", setOf("one")))))
        engine.pause("task_1")
        assertEquals(
            AgentTaskStatus.PAUSED,
            (engine.advance("task_1") as AgentAdvanceResult.Finished).state.status
        )
        assertEquals(0, calls)
        engine.resume("task_1")
        engine.cancel("task_1")
        assertEquals(AgentTaskStatus.CANCELLED, engine.state("task_1")?.status)
        assertEquals(0, calls)
    }

    @Test
    fun rejectsCyclesUnknownDependenciesAndDuplicateIds() {
        val engine = orchestrator { _, _ ->
            PluginExecutionResult(true, "ok", PluginCapability.DEVICE_INFORMATION)
        }
        val cycle = plan(listOf(node("a", setOf("b")), node("b", setOf("a"))))
        assertIllegalArgument { engine.create(cycle) }

        val unknown = plan(listOf(node("a", setOf("missing"))), id = "task_2")
        assertIllegalArgument { engine.create(unknown) }

        val duplicate = plan(listOf(node("same"), node("same")), id = "task_3")
        assertIllegalArgument { engine.create(duplicate) }
    }

    @Test
    fun deadlineAndStepBudgetStopExecution() {
        var calls = 0
        val engine = orchestrator { _, _ ->
            calls++
            PluginExecutionResult(true, "ok", PluginCapability.DEVICE_INFORMATION)
        }
        engine.create(plan(listOf(node("one")), deadline = 1_001L))
        now = 1_001L
        assertEquals(
            AgentTaskStatus.EXPIRED,
            (engine.advance("task_1") as AgentAdvanceResult.Finished).state.status
        )
        assertEquals(0, calls)
    }

    @Test
    fun cancellingRunningActionInterruptsItAndRequiresRecoveryVerification() {
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val engine = orchestrator { _, _ ->
            started.countDown()
            try {
                CountDownLatch(1).await()
            } catch (error: InterruptedException) {
                interrupted.countDown()
                throw error
            }
            PluginExecutionResult(true, "unexpected completion", PluginCapability.DEVICE_INFORMATION)
        }
        engine.create(plan(listOf(node("external"))))

        val worker = Thread { engine.advance("task_1") }
        worker.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))

        val cancelled = engine.cancel("task_1")
        assertTrue(interrupted.await(2, TimeUnit.SECONDS))
        worker.join(2_000L)

        assertFalse(worker.isAlive)
        assertEquals(AgentTaskStatus.RECOVERY_REQUIRED, cancelled.status)
        assertEquals(AgentTaskStatus.RECOVERY_REQUIRED, engine.state("task_1")?.status)
        assertEquals(
            AgentNodeStatus.RECOVERY_REQUIRED,
            engine.state("task_1")?.nodes?.getValue("external")?.status
        )
    }

    @Test
    fun interruptedRunningActionRequiresReviewAndIsNeverReplayed() {
        var calls = 0
        val store = InMemoryAgentTaskStateStore()
        val taskPlan = plan(listOf(node("external")))
        val running = AgentTaskState(
            plan = taskPlan,
            nodes = mapOf(
                "external" to AgentNodeState(
                    nodeId = "external",
                    status = AgentNodeStatus.RUNNING,
                    idempotencyKey = "task_1:external"
                )
            ),
            status = AgentTaskStatus.RUNNING,
            updatedAtMillis = now
        )
        assertTrue(store.create(running))
        val engine = orchestrator(store) { _, _ ->
            calls++
            PluginExecutionResult(true, "should not execute", PluginCapability.DEVICE_INFORMATION)
        }
        val result = engine.advance("task_1") as AgentAdvanceResult.Finished
        assertEquals(AgentTaskStatus.RECOVERY_REQUIRED, result.state.status)
        assertEquals(AgentNodeStatus.RECOVERY_REQUIRED, result.state.nodes.getValue("external").status)
        assertEquals(0, calls)
    }

    @Test
    fun failedDispatchedActionRequiresRecoveryAndDoesNotRunDependents() {
        var calls = 0
        val engine = orchestrator { _, _ ->
            calls++
            PluginExecutionResult(false, "handler failed", PluginCapability.DEVICE_INFORMATION)
        }
        engine.create(plan(listOf(node("first"), node("second", setOf("first")))))
        val result = engine.advance("task_1") as AgentAdvanceResult.Finished
        assertEquals(AgentTaskStatus.RECOVERY_REQUIRED, result.state.status)
        assertEquals(AgentNodeStatus.RECOVERY_REQUIRED, result.state.nodes.getValue("first").status)
        assertEquals(1, calls)
        engine.advance("task_1")
        assertEquals(1, calls)
    }

    @Test
    fun timedOutCancelledAndRejectedOutputActionsRequireRecovery() {
        listOf(
            PluginExecutionStatus.TIMED_OUT,
            PluginExecutionStatus.CANCELLED,
            PluginExecutionStatus.OUTPUT_REJECTED
        ).forEach { status ->
            var calls = 0
            val engine = orchestrator { _, _ ->
                calls++
                PluginExecutionResult(
                    false,
                    "uncertain outcome",
                    PluginCapability.DEVICE_INFORMATION,
                    status
                )
            }
            engine.create(plan(listOf(node("external"))))

            val result = engine.advance("task_1") as AgentAdvanceResult.Finished
            assertEquals(status.name, AgentTaskStatus.RECOVERY_REQUIRED, result.state.status)
            assertEquals(status.name, AgentNodeStatus.RECOVERY_REQUIRED, result.state.nodes.getValue("external").status)
            assertEquals(1, calls)
            engine.advance("task_1")
            assertEquals(1, calls)
        }
    }


    @Test
    fun stepBudgetPreventsDispatchOfAdditionalNodes() {
        var calls = 0
        val engine = orchestrator { _, _ ->
            calls++
            PluginExecutionResult(true, "ok", PluginCapability.DEVICE_INFORMATION)
        }
        engine.create(plan(listOf(node("first"), node("second")), maxSteps = 1))
        engine.advance("task_1")
        val stopped = engine.advance("task_1") as AgentAdvanceResult.Finished
        assertEquals(AgentTaskStatus.FAILED, stopped.state.status)
        assertEquals(1, calls)
    }

    @Test
    fun missingCatalogRequiresApprovalBeforeAnyActionIsDispatched() {
        var calls = 0
        val executor = AgentActionExecutor { _, _ ->
            calls++
            PluginExecutionResult(true, "unexpected", PluginCapability.DEVICE_INFORMATION)
        }
        val engine = AgentOrchestrator(
            InMemoryAgentTaskStateStore(),
            executor,
            clockMillis = { now }
        )
        engine.create(plan(listOf(node("uncatalogued"))))

        val blocked = engine.advance("task_1") as AgentAdvanceResult.BlockedForApproval

        assertEquals("uncatalogued", blocked.nodeId)
        assertEquals(AgentTaskStatus.WAITING_APPROVAL, blocked.state.status)
        assertEquals(0, calls)
    }

    @Test
    fun actionMissingFromConnectedCatalogRequiresApprovalBeforeDispatch() {
        var calls = 0
        val executor = object : AgentActionExecutor, AgentActionCatalogProvider {
            override fun availableTools(): List<PluginToolDescriptor> = emptyList()

            override fun executeToolCall(
                toolName: String,
                argumentsJson: String,
                userConfirmed: Boolean
            ) = PluginExecutionResult(false, "not used", PluginCapability.DEVICE_INFORMATION)

            override fun execute(
                request: PluginExecutionRequest,
                idempotencyKey: String
            ): PluginExecutionResult {
                calls++
                return PluginExecutionResult(true, "unexpected", PluginCapability.DEVICE_INFORMATION)
            }
        }
        val engine = AgentOrchestrator(
            InMemoryAgentTaskStateStore(),
            executor,
            clockMillis = { now }
        )
        engine.create(plan(listOf(node("missing_from_catalog"))))

        val blocked = engine.advance("task_1") as AgentAdvanceResult.BlockedForApproval

        assertEquals("missing_from_catalog", blocked.nodeId)
        assertEquals(AgentTaskStatus.WAITING_APPROVAL, blocked.state.status)
        assertEquals(0, calls)
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
