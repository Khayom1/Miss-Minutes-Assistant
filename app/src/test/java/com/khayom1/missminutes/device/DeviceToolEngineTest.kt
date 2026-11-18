package com.khayom1.missminutes.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceToolEngineTest {
    @Test
    fun capabilityIsDeniedUntilIndividuallyGranted() {
        val audit = mutableListOf<DeviceToolAuditEntry>()
        var executions = 0
        val engine = DeviceToolEngine(
            authorization = DeviceToolAuthorization { false },
            host = DeviceToolHost { executions++; "ok" },
            auditLog = DeviceToolAuditLog { audit += it }
        )

        val result = engine.execute(DeviceToolRequest(DeviceToolCommand.ReadDeviceInfo))

        assertFalse(result.success)
        assertEquals(0, executions)
        assertEquals("denied", audit.single().outcome)
    }

    @Test
    fun externalActionRequiresExplicitConfirmation() {
        val engine = engine(granted = true)
        val result = engine.execute(DeviceToolRequest(DeviceToolCommand.OpenWifiSettings))

        assertFalse(result.success)
        assertTrue(result.message.contains("confirmation"))
    }

    @Test
    fun confirmedExternalActionRuns() {
        val engine = engine(granted = true)
        val result = engine.execute(
            DeviceToolRequest(DeviceToolCommand.OpenWifiSettings, userConfirmed = true)
        )

        assertTrue(result.success)
    }

    @Test
    fun shareTextRejectsOversizedPayloadBeforeHostExecution() {
        var executions = 0
        val engine = DeviceToolEngine(
            authorization = DeviceToolAuthorization { true },
            host = DeviceToolHost { executions++; "ok" },
            auditLog = DeviceToolAuditLog {}
        )

        val result = engine.execute(
            DeviceToolRequest(DeviceToolCommand.ShareText("x".repeat(1_001)), userConfirmed = true)
        )

        assertFalse(result.success)
        assertEquals(0, executions)
    }

    @Test
    fun reminderRejectsDatesMoreThanOneYearAheadBeforeHostExecution() {
        val now = 1_000_000L
        var executions = 0
        val engine = DeviceToolEngine(
            authorization = DeviceToolAuthorization { true },
            host = DeviceToolHost { executions++; "ok" },
            auditLog = DeviceToolAuditLog {},
            clockMillis = { now }
        )
        val result = engine.execute(
            DeviceToolRequest(
                DeviceToolCommand.CreateReminder(
                    title = "Far future",
                    startEpochMillis = now + 365L * 24 * 60 * 60 * 1_000 + 1
                ),
                userConfirmed = true
            )
        )
        assertFalse(result.success)
        assertTrue(result.message.contains("365 days"))
        assertEquals(0, executions)
    }

    @Test
    fun reminderAcceptsTheMaximumSupportedFutureBoundary() {
        val now = 1_000_000L
        var executions = 0
        val engine = DeviceToolEngine(
            authorization = DeviceToolAuthorization { true },
            host = DeviceToolHost { executions++; "created" },
            auditLog = DeviceToolAuditLog {},
            clockMillis = { now }
        )
        val result = engine.execute(
            DeviceToolRequest(
                DeviceToolCommand.CreateReminder(
                    title = "Boundary",
                    startEpochMillis = now + 365L * 24 * 60 * 60 * 1_000
                ),
                userConfirmed = true
            )
        )
        assertTrue(result.success)
        assertEquals(1, executions)
    }

    @Test
    fun reminderRejectsInvalidTitleAndPastTimeBeforeHostExecution() {
        val now = 1_000_000L
        var executions = 0
        val engine = DeviceToolEngine(
            authorization = DeviceToolAuthorization { true },
            host = DeviceToolHost { executions++; "unexpected" },
            auditLog = DeviceToolAuditLog {},
            clockMillis = { now }
        )
        val blankTitle = engine.execute(
            DeviceToolRequest(
                DeviceToolCommand.CreateReminder(title = " ", startEpochMillis = now + 10_000),
                userConfirmed = true
            )
        )
        val pastTime = engine.execute(
            DeviceToolRequest(
                DeviceToolCommand.CreateReminder(title = "Past", startEpochMillis = now - 60_001),
                userConfirmed = true
            )
        )
        assertFalse(blankTitle.success)
        assertFalse(pastTime.success)
        assertEquals(0, executions)
    }

    @Test
    fun rateLimitBoundsCallsWithinOneMinute() {
        var now = 10_000L
        var executions = 0
        val engine = DeviceToolEngine(
            authorization = DeviceToolAuthorization { true },
            host = DeviceToolHost { executions++; "ok" },
            auditLog = DeviceToolAuditLog {},
            clockMillis = { now },
            maxCallsPerMinute = 2
        )

        repeat(2) {
            assertTrue(engine.execute(DeviceToolRequest(DeviceToolCommand.ReadDeviceInfo)).success)
        }
        assertFalse(engine.execute(DeviceToolRequest(DeviceToolCommand.ReadDeviceInfo)).success)
        assertEquals(2, executions)

        now += 60_000L
        assertTrue(engine.execute(DeviceToolRequest(DeviceToolCommand.ReadDeviceInfo)).success)
        assertEquals(3, executions)
    }

    @Test
    fun actionIsBlockedWhenAuditLogCannotRecordRequest() {
        var executions = 0
        val engine = DeviceToolEngine(
            authorization = DeviceToolAuthorization { true },
            host = DeviceToolHost { executions++; "ok" },
            auditLog = DeviceToolAuditLog { throw IllegalStateException("disk unavailable") }
        )

        val result = engine.execute(DeviceToolRequest(DeviceToolCommand.ReadDeviceInfo))

        assertFalse(result.success)
        assertTrue(result.message.contains("Audit logging is unavailable"))
        assertEquals(0, executions)
    }

    @Test
    fun slowHostIsReportedAsOverBudget() {
        val engine = DeviceToolEngine(
            authorization = DeviceToolAuthorization { true },
            host = DeviceToolHost { Thread.sleep(20); "late" },
            auditLog = DeviceToolAuditLog {},
            executionBudgetMillis = 1L
        )

        val result = engine.execute(DeviceToolRequest(DeviceToolCommand.ReadDeviceInfo))

        assertFalse(result.success)
        assertTrue(result.message.contains("execution budget"))
    }

    private fun engine(granted: Boolean) = DeviceToolEngine(
        authorization = DeviceToolAuthorization { granted },
        host = DeviceToolHost { "ok" },
        auditLog = DeviceToolAuditLog {}
    )
}
