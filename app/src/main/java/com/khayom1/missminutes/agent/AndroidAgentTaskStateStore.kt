package com.khayom1.missminutes.agent

import android.content.Context
import com.khayom1.missminutes.memory.ConversationCipher
import com.khayom1.missminutes.plugins.PluginExecutionRequest
import com.khayom1.missminutes.plugins.PluginValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android persistence for agent task graphs. The complete bounded task snapshot is
 * encrypted with the app's Android Keystore AES-GCM cipher before SharedPreferences.
 */
class AndroidAgentTaskStateStore(
    context: Context,
    private val cipher: ConversationCipher
) : AgentTaskStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    override fun create(state: AgentTaskState): Boolean {
        val states = readStates()
        if (states.containsKey(state.plan.id) || states.size >= MAX_TASKS) return false
        writeStates(states + (state.plan.id to state))
        return true
    }

    @Synchronized
    override fun load(taskId: String): AgentTaskState? = readStates()[taskId]

    @Synchronized
    override fun all(): List<AgentTaskState> = readStates().values.sortedByDescending { it.updatedAtMillis }

    @Synchronized
    override fun save(state: AgentTaskState) {
        val states = readStates()
        check(states.containsKey(state.plan.id)) { "Unknown agent task." }
        writeStates(states + (state.plan.id to state))
    }

    private fun readStates(): Map<String, AgentTaskState> {
        val encrypted = preferences.getString(STORAGE_KEY, null) ?: return emptyMap()
        val plaintext = cipher.decrypt(CIPHER_OWNER, encrypted)
        require(plaintext.length <= MAX_STORAGE_CHARACTERS) { "Agent task storage exceeds its size limit." }
        val root = JSONObject(plaintext)
        require(root.length() <= MAX_TASKS) { "Agent task storage exceeds its task limit." }
        return buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                require(TASK_ID.matches(key))
                val state = decodeState(root.getJSONObject(key))
                require(state.plan.id == key)
                put(key, state)
            }
        }
    }

    private fun writeStates(states: Map<String, AgentTaskState>) {
        require(states.size <= MAX_TASKS)
        val root = JSONObject()
        states.forEach { (id, state) ->
            require(TASK_ID.matches(id) && state.plan.id == id)
            root.put(id, encodeState(state))
        }
        val plaintext = root.toString()
        require(plaintext.length <= MAX_STORAGE_CHARACTERS)
        val encrypted = cipher.encrypt(CIPHER_OWNER, plaintext)
        check(preferences.edit().putString(STORAGE_KEY, encrypted).commit()) {
            "Unable to persist encrypted agent task state."
        }
    }

    private fun encodeState(state: AgentTaskState): JSONObject {
        val nodes = JSONArray()
        state.plan.nodes.forEach { node ->
            val args = JSONObject()
            node.action.request.arguments.forEach { (name, value) ->
                args.put(name, when (value) {
                    is PluginValue.Text -> JSONObject().put("type", "text").put("value", value.value)
                    is PluginValue.Number -> JSONObject().put("type", "number").put("value", value.value)
                    is PluginValue.Flag -> JSONObject().put("type", "flag").put("value", value.value)
                })
            }
            val dependencies = JSONArray().apply { node.dependsOn.forEach { put(it) } }
            nodes.put(
                JSONObject()
                    .put("id", node.id)
                    .put("dependsOn", dependencies)
                    .put("pluginId", node.action.request.pluginId)
                    .put("actionId", node.action.request.actionId)
                    .put("arguments", args)
                    .put("userConfirmed", node.action.request.userConfirmed)
                    .put("approvalRequired", node.action.approvalRequired)
            )
        }
        val nodeStates = JSONArray()
        state.nodes.values.forEach { node ->
            nodeStates.put(
                JSONObject()
                    .put("nodeId", node.nodeId)
                    .put("status", node.status.name)
                    .put("message", node.message ?: JSONObject.NULL)
                    .put("idempotencyKey", node.idempotencyKey)
                    .put("approved", node.approved)
            )
        }
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("planId", state.plan.id)
            .put("maxSteps", state.plan.maxSteps)
            .put("deadlineAtMillis", state.plan.deadlineAtMillis)
            .put("nodes", nodes)
            .put("nodeStates", nodeStates)
            .put("status", state.status.name)
            .put("completedSteps", state.completedSteps)
            .put("updatedAtMillis", state.updatedAtMillis)
    }

    private fun decodeState(json: JSONObject): AgentTaskState {
        require(json.getInt("schemaVersion") == SCHEMA_VERSION)
        val planId = json.getString("planId")
        val planNodes = json.getJSONArray("nodes")
        require(planNodes.length() in 1..MAX_PLAN_NODES)
        val nodes = (0 until planNodes.length()).map { index ->
            val item = planNodes.getJSONObject(index)
            val dependencyArray = item.getJSONArray("dependsOn")
            require(dependencyArray.length() <= MAX_PLAN_NODES)
            val dependencies = (0 until dependencyArray.length()).map { dependencyArray.getString(it) }.toSet()
            val argsJson = item.getJSONObject("arguments")
            require(argsJson.length() <= MAX_ARGUMENTS)
            val arguments = buildMap {
                val keys = argsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = argsJson.getJSONObject(key)
                    put(key, when (value.getString("type")) {
                        "text" -> PluginValue.Text(value.getString("value").also {
                            require(it.length <= MAX_ARGUMENT_CHARACTERS)
                        })
                        "number" -> PluginValue.Number(value.getLong("value"))
                        "flag" -> PluginValue.Flag(value.getBoolean("value"))
                        else -> throw IllegalArgumentException("Unsupported stored plugin argument type.")
                    })
                }
            }
            AgentTaskNode(
                id = item.getString("id"),
                dependsOn = dependencies,
                action = AgentAction(
                    request = PluginExecutionRequest(
                        pluginId = item.getString("pluginId"),
                        actionId = item.getString("actionId"),
                        arguments = arguments,
                        userConfirmed = item.getBoolean("userConfirmed")
                    ),
                    approvalRequired = item.getBoolean("approvalRequired")
                )
            )
        }
        val stateArray = json.getJSONArray("nodeStates")
        require(stateArray.length() == nodes.size)
        val nodeStates = buildMap {
            for (index in 0 until stateArray.length()) {
                val item = stateArray.getJSONObject(index)
                val messageValue = item.opt("message")
                val message = if (messageValue == null || messageValue == JSONObject.NULL) null else messageValue.toString()
                val nodeId = item.getString("nodeId")
                put(
                    nodeId,
                    AgentNodeState(
                        nodeId = nodeId,
                        status = AgentNodeStatus.valueOf(item.getString("status")),
                        message = message,
                        idempotencyKey = item.getString("idempotencyKey"),
                        approved = item.optBoolean("approved", false)
                    )
                )
            }
        }
        require(nodeStates.keys == nodes.map { it.id }.toSet())
        require(nodeStates.size == nodes.size)
        val maxSteps = json.getInt("maxSteps")
        require(maxSteps in 1..nodes.size)
        val completedSteps = json.getInt("completedSteps")
        require(completedSteps in 0..maxSteps)
        return AgentTaskState(
            plan = AgentTaskPlan(
                id = planId,
                nodes = nodes,
                maxSteps = maxSteps,
                deadlineAtMillis = json.getLong("deadlineAtMillis")
            ),
            nodes = nodeStates,
            status = AgentTaskStatus.valueOf(json.getString("status")),
            completedSteps = completedSteps,
            updatedAtMillis = json.getLong("updatedAtMillis")
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "miss_minutes_agent_tasks"
        const val STORAGE_KEY = "encrypted_task_graphs_v1"
        const val CIPHER_OWNER = "agent_task_state_v1"
        const val SCHEMA_VERSION = 1
        const val MAX_TASKS = 32
        const val MAX_PLAN_NODES = 24
        const val MAX_ARGUMENTS = 8
        const val MAX_ARGUMENT_CHARACTERS = 2_000
        const val MAX_STORAGE_CHARACTERS = 128_000
        val TASK_ID = Regex("^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}$")
    }
}
