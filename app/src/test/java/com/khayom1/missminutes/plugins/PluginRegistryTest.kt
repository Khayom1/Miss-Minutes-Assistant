package com.khayom1.missminutes.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRegistryTest {
    private val manifest = PluginManifest(
        id = "test_plugin",
        version = "1.0.0",
        displayName = "Test plugin",
        description = "Test-only allowlisted action.",
        capabilities = setOf(PluginCapability.DEVICE_INFORMATION),
        actions = listOf(
            PluginActionSpec(
                id = "read_info",
                capability = PluginCapability.DEVICE_INFORMATION,
                arguments = mapOf(
                    "locale" to PluginFieldSpec(
                        name = "locale",
                        type = PluginValueType.STRING,
                        maxLength = 8,
                        allowedValues = setOf("en", "ru", "tg")
                    )
                )
            )
        )
    )

    @Test
    fun unconsentedPluginCannotExecute() {
        var calls = 0
        val registry = registry(consented = false) { calls++; PluginActionOutput(true, "ok") }
        assertTrue(registry.install(manifest))
        assertFalse(registry.execute(PluginExecutionRequest("test_plugin", "read_info")).success)
        assertEquals(0, calls)
    }

    @Test
    fun consentedAllowlistedActionExecutes() {
        var calls = 0
        val registry = registry(consented = true) { calls++; PluginActionOutput(true, "ok") }
        assertTrue(registry.install(manifest))
        val result = registry.execute(
            PluginExecutionRequest("test_plugin", "read_info", mapOf("locale" to PluginValue.Text("tg")))
        )
        assertTrue(result.success)
        assertEquals(1, calls)
    }

    @Test
    fun unknownActionAndUnknownArgumentsAreRejectedBeforeHandler() {
        var calls = 0
        val registry = registry(consented = true) { calls++; PluginActionOutput(true, "ok") }
        registry.install(manifest)
        assertFalse(registry.execute(PluginExecutionRequest("test_plugin", "shell")).success)
        assertFalse(
            registry.execute(
                PluginExecutionRequest("test_plugin", "read_info", mapOf("command" to PluginValue.Text("id")))
            ).success
        )
        assertEquals(0, calls)
    }

    @Test
    fun argumentTypeLengthAndEnumAreValidated() {
        var calls = 0
        val registry = registry(consented = true) { calls++; PluginActionOutput(true, "ok") }
        registry.install(manifest)
        val wrongType = registry.execute(
            PluginExecutionRequest("test_plugin", "read_info", mapOf("locale" to PluginValue.Number(2)))
        )
        val tooLong = registry.execute(
            PluginExecutionRequest("test_plugin", "read_info", mapOf("locale" to PluginValue.Text("en-US-too-long")))
        )
        val notAllowed = registry.execute(
            PluginExecutionRequest("test_plugin", "read_info", mapOf("locale" to PluginValue.Text("xx")))
        )
        assertFalse(wrongType.success)
        assertFalse(tooLong.success)
        assertFalse(notAllowed.success)
        assertEquals(0, calls)
    }

    @Test
    fun manifestCannotDeclareUncompiledOrMismatchedCapability() {
        val registry = registry(consented = true) { PluginActionOutput(true, "ok") }
        val bad = manifest.copy(actions = listOf(PluginActionSpec("arbitrary", PluginCapability.BATTERY_STATUS)))
        try {
            registry.install(bad)
            throw AssertionError("Expected an uncompiled action to be rejected.")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun confirmationAndConsentAreIndependentGates() {
        var calls = 0
        val registry = PluginRegistry(
            handlers = listOf(handler { calls++; PluginActionOutput(true, "ok") }),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {}
        )
        val confirmedManifest = manifest.copy(
            actions = manifest.actions.map { it.copy(requiresConfirmation = true) }
        )
        registry.install(confirmedManifest)
        val denied = registry.execute(PluginExecutionRequest("test_plugin", "read_info"))
        val allowed = registry.execute(
            PluginExecutionRequest("test_plugin", "read_info", userConfirmed = true)
        )
        assertFalse(denied.success)
        assertTrue(allowed.success)
        assertEquals(1, calls)
    }

    @Test
    fun rateLimitAndAuditFailureFailClosed() {
        var now = 1_000L
        var calls = 0
        val registry = PluginRegistry(
            handlers = listOf(handler { calls++; PluginActionOutput(true, "ok") }),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {},
            clockMillis = { now },
            globalCallsPerMinute = 1
        )
        registry.install(manifest)
        fun request() = PluginExecutionRequest(
            "test_plugin", "read_info", mapOf("locale" to PluginValue.Text("en"))
        )
        assertTrue(registry.execute(request()).success)
        assertFalse(registry.execute(request()).success)
        now += 60_000
        assertTrue(registry.execute(request()).success)
        assertEquals(2, calls)

        val blocked = PluginRegistry(
            handlers = listOf(handler { calls++; PluginActionOutput(true, "ok") }),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog { throw IllegalStateException("log unavailable") }
        )
        blocked.install(manifest)
        assertFalse(blocked.execute(request()).success)
        assertEquals(2, calls)
    }

    @Test
    fun oversizedOutputAndSlowHandlerAreRejected() {
        val oversized = registry(consented = true) { PluginActionOutput(true, "x".repeat(4_001)) }
        oversized.install(manifest)
        assertFalse(oversized.execute(PluginExecutionRequest("test_plugin", "read_info", mapOf("locale" to PluginValue.Text("en")))).success)

        val slow = PluginRegistry(
            handlers = listOf(handler { Thread.sleep(20); PluginActionOutput(true, "late") }),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {}
        )
        slow.install(manifest.copy(maxExecutionMillis = 1))
        assertFalse(slow.execute(PluginExecutionRequest("test_plugin", "read_info", mapOf("locale" to PluginValue.Text("en")))).success)
    }

    private fun registry(consented: Boolean, action: () -> PluginActionOutput) = PluginRegistry(
        handlers = listOf(handler(action)),
        consentStore = PluginConsentStore { consented },
        auditLog = PluginAuditLog {}
    )

    private fun handler(action: () -> PluginActionOutput) = object : PluginActionHandler {
        override val actionId = "read_info"
        override val capability = PluginCapability.DEVICE_INFORMATION
        override fun execute(arguments: Map<String, PluginValue>) = action()
    }
}
