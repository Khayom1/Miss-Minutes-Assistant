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
    fun integerBoundsAndBooleanTypesAreValidated() {
        var calls = 0
        val numericManifest = PluginManifest(
            id = "typed_plugin",
            version = "1.0.0",
            displayName = "Typed plugin",
            description = "Validates numeric and boolean arguments.",
            capabilities = setOf(PluginCapability.DEVICE_INFORMATION),
            actions = listOf(
                PluginActionSpec(
                    id = "typed_action",
                    capability = PluginCapability.DEVICE_INFORMATION,
                    arguments = mapOf(
                        "count" to PluginFieldSpec("count", PluginValueType.INTEGER, minimum = 1, maximum = 3),
                        "enabled" to PluginFieldSpec("enabled", PluginValueType.BOOLEAN)
                    )
                )
            )
        )
        val handler = object : PluginActionHandler {
            override val actionId = "typed_action"
            override val capability = PluginCapability.DEVICE_INFORMATION
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                calls++
                return PluginActionOutput(true, "ok")
            }
        }
        val registry = PluginRegistry(
            handlers = listOf(handler),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {}
        )
        registry.install(numericManifest)
        val invalidNumber = registry.execute(
            PluginExecutionRequest("typed_plugin", "typed_action", mapOf(
                "count" to PluginValue.Number(4), "enabled" to PluginValue.Flag(true)
            ))
        )
        val invalidBoolean = registry.execute(
            PluginExecutionRequest("typed_plugin", "typed_action", mapOf(
                "count" to PluginValue.Number(2), "enabled" to PluginValue.Text("true")
            ))
        )
        val valid = registry.execute(
            PluginExecutionRequest("typed_plugin", "typed_action", mapOf(
                "count" to PluginValue.Number(2), "enabled" to PluginValue.Flag(false)
            ))
        )
        assertFalse(invalidNumber.success)
        assertFalse(invalidBoolean.success)
        assertTrue(valid.success)
        assertEquals(1, calls)
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
            PluginExecutionRequest(
                "test_plugin",
                "read_info",
                arguments = mapOf("locale" to PluginValue.Text("en")),
                userConfirmed = true
            )
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

    @Test
    fun revokingConsentImmediatelyBlocksFurtherExecutionAndWritesAudit() {
        var currentGrant = true
        val events = mutableListOf<PluginAuditEntry>()
        val consent = object : MutablePluginConsentStore {
            override fun isGranted(pluginId: String) = currentGrant
            override fun setGranted(pluginId: String, granted: Boolean) {
                currentGrant = granted
            }
        }
        var calls = 0
        val registry = PluginRegistry(
            handlers = listOf(handler { calls++; PluginActionOutput(true, "ok") }),
            consentStore = consent,
            auditLog = PluginAuditLog { events += it }
        )
        registry.install(manifest)
        assertTrue(registry.execute(
            PluginExecutionRequest("test_plugin", "read_info", mapOf("locale" to PluginValue.Text("en")))
        ).success)

        assertTrue(registry.revokeConsent("test_plugin"))
        assertFalse(consent.isGranted("test_plugin"))
        assertFalse(registry.execute(
            PluginExecutionRequest("test_plugin", "read_info", mapOf("locale" to PluginValue.Text("en")))
        ).success)
        assertTrue(events.any { it.actionId == "consent" && it.outcome == "consent_revoked" })
        assertEquals(1, calls)
    }

    @Test
    fun uninstallRevokesPersistedConsentBeforeRemovingManifestAndAuditsLifecycle() {
        var currentGrant = true
        val events = mutableListOf<PluginAuditEntry>()
        val consent = object : MutablePluginConsentStore {
            override fun isGranted(pluginId: String) = currentGrant
            override fun setGranted(pluginId: String, grantedValue: Boolean) {
                currentGrant = grantedValue
            }
        }
        val registry = PluginRegistry(
            handlers = listOf(handler { PluginActionOutput(true, "ok") }),
            consentStore = consent,
            auditLog = PluginAuditLog { events += it }
        )
        registry.install(manifest)
        assertTrue(registry.uninstall("test_plugin"))
        assertFalse(consent.isGranted("test_plugin"))
        assertTrue(registry.installedPlugins().isEmpty())
        assertFalse(registry.execute(
            PluginExecutionRequest("test_plugin", "read_info", mapOf("locale" to PluginValue.Text("en")))
        ).success)
        assertEquals("uninstalled", events.last().outcome)
        assertFalse(registry.uninstall("test_plugin"))
    }

    @Test
    fun uninstallFailsClosedWhenConsentStoreCannotDurablyRevoke() {
        val registry = PluginRegistry(
            handlers = listOf(handler { PluginActionOutput(true, "ok") }),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {}
        )
        registry.install(manifest)
        assertFalse(registry.uninstall("test_plugin"))
        assertEquals(listOf(manifest), registry.installedPlugins())
    }

    @Test
    fun grantingConsentRequiresDurableAuditAndFailsClosedWhenAuditIsUnavailable() {
        var currentGrant = false
        val events = mutableListOf<PluginAuditEntry>()
        val consent = object : MutablePluginConsentStore {
            override fun isGranted(pluginId: String) = currentGrant
            override fun setGranted(pluginId: String, granted: Boolean) {
                currentGrant = granted
            }
        }
        val registry = PluginRegistry(
            handlers = listOf(handler { PluginActionOutput(true, "ok") }),
            consentStore = consent,
            auditLog = PluginAuditLog { events += it }
        )
        registry.install(manifest)
        assertTrue(registry.grantConsent("test_plugin"))
        assertTrue(consent.isGranted("test_plugin"))
        assertTrue(events.any { it.actionId == "consent" && it.outcome == "consent_granted" })

        val blockedConsent = object : MutablePluginConsentStore {
            var granted = false
            override fun isGranted(pluginId: String) = granted
            override fun setGranted(pluginId: String, granted: Boolean) {
                this.granted = granted
            }
        }
        val blocked = PluginRegistry(
            handlers = listOf(handler { PluginActionOutput(true, "ok") }),
            consentStore = blockedConsent,
            auditLog = PluginAuditLog { throw IllegalStateException("audit unavailable") }
        )
        blocked.install(manifest)
        assertFalse(blocked.grantConsent("test_plugin"))
        assertFalse(blockedConsent.isGranted("test_plugin"))
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
