package com.khayom1.missminutes.agent

import com.khayom1.missminutes.plugins.PluginCapability
import com.khayom1.missminutes.plugins.PluginExecutionRequest
import com.khayom1.missminutes.plugins.PluginExecutionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private fun orchestrator(
        store: AgentTaskStateStore = InMemoryAgentTaskStateStore(),
        action: (PluginExecutionRequest, String) -> PluginExecutionResult
    ) = AgentOrchestrator(store, AgentActionExecutor(action), clockMillis = { now })

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
    fun failedActionStopsGraphAndDoesNotRunDependents() {
        var calls = 0
        val engine = orchestrator { _, _ ->
            calls++
            PluginExecutionResult(false, "denied", PluginCapability.DEVICE_INFORMATION)
        }
        engine.create(plan(listOf(node("first"), node("second", setOf("first")))))
        val result = engine.advance("task_1") as AgentAdvanceResult.Progress
        assertEquals(AgentTaskStatus.FAILED, result.state.status)
        assertEquals(1, calls)
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

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException.")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
