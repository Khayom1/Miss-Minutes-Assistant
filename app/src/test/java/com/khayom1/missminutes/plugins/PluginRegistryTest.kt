package com.khayom1.missminutes.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class PluginRegistryTest {
    private val testAccessChecker = PluginAccessChecker { _, _ -> PluginAccessDecision.Granted }
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
            override val argumentSchema = numericManifest.actions.single().arguments
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                calls++
                return PluginActionOutput(true, "ok")
            }
        }
        val registry = PluginRegistry(
            handlers = listOf(handler),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker,
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
    fun importedManifestCannotWidenCompiledHandlerArgumentSchema() {
        val registry = registry(consented = true) { PluginActionOutput(true, "ok") }
        val widened = manifest.copy(
            actions = manifest.actions.map { action ->
                action.copy(
                    arguments = mapOf(
                        "locale" to PluginFieldSpec(
                            name = "locale",
                            type = PluginValueType.STRING,
                            maxLength = 256
                        )
                    )
                )
            }
        )

        try {
            registry.install(widened)
            throw AssertionError("Expected a widened imported schema to be rejected.")
        } catch (_: IllegalArgumentException) {
            // The compiled handler schema remains authoritative.
        }
    }

    @Test
    fun writeCapabilitiesRequireConfirmationEvenWhenManifestOmitsIt() {
        listOf(
            PluginCapability.CALENDAR_WRITE,
            PluginCapability.ALARM_SCHEDULE,
            PluginCapability.GITHUB_REPOSITORY_WRITE
        ).forEachIndexed { index, writeCapability ->
            val pluginId = "write_plugin_$index"
            val compiledActionId = "write_action_$index"
            var calls = 0
            val handler = object : PluginActionHandler {
                override val actionId = compiledActionId
                override val capability = writeCapability
                override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                    calls++
                    return PluginActionOutput(true, "written")
                }
            }
            val registry = PluginRegistry(
                handlers = listOf(handler),
                consentStore = PluginConsentStore { true },
                auditLog = PluginAuditLog {},
                accessChecker = testAccessChecker
            )
            val manifest = PluginManifest(
                id = pluginId,
                version = "1.0.0",
                displayName = "Write test",
                description = "A write capability must require confirmation.",
                capabilities = setOf(writeCapability),
                actions = listOf(PluginActionSpec(compiledActionId, writeCapability))
            )
            assertTrue(registry.install(manifest))
            assertTrue(registry.connectedTools().single().requiresConfirmation)

            val request = PluginExecutionRequest(pluginId, compiledActionId)
            assertEquals(
                PluginExecutionStatus.CONFIRMATION_REQUIRED,
                registry.execute(request).status
            )
            assertEquals(0, calls)
            assertTrue(registry.execute(request.copy(userConfirmed = true)).success)
            assertEquals(1, calls)
        }
    }

    @Test
    fun confirmationAndConsentAreIndependentGates() {
        var calls = 0
        val registry = PluginRegistry(
            handlers = listOf(handler { calls++; PluginActionOutput(true, "ok") }),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker,
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
            accessChecker = testAccessChecker,
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
            auditLog = PluginAuditLog { throw IllegalStateException("log unavailable") },
            accessChecker = testAccessChecker,
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
            auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker,
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
            auditLog = PluginAuditLog { events += it },
            accessChecker = testAccessChecker,
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
            auditLog = PluginAuditLog { events += it },
            accessChecker = testAccessChecker,
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
            auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker,
        )
        registry.install(manifest)
        assertFalse(registry.uninstall("test_plugin"))
        assertEquals(listOf(manifest), registry.installedPlugins())
    }


    @Test
    fun connectPersistsGrantAndPublishesConnectedTools() {
        var granted = false
        val events = mutableListOf<PluginAuditEntry>()
        val consent = object : MutablePluginConsentStore {
            override fun isGranted(pluginId: String) = granted
            override fun setGranted(pluginId: String, value: Boolean) { granted = value }
        }
        val registry = PluginRegistry(
            handlers = listOf(handler { PluginActionOutput(true, "ok") }),
            consentStore = consent,
            auditLog = PluginAuditLog { events += it },
            accessChecker = testAccessChecker,
        )
        registry.install(manifest)
        assertFalse(registry.isConnected("test_plugin"))
        assertTrue(registry.connect("test_plugin"))
        assertTrue(AndroidLikeConsentProbe(consent).isGranted("test_plugin"))
        assertEquals(1, registry.connectedTools().size)
        assertTrue(events.any { it.actionId == "connection" && it.outcome == "connected" })
    }

    @Test
    fun missingRuntimeAccessIsReturnedAndBlocksHandlerExecution() {
        var calls = 0
        val permission = PluginAccessDecision.PermissionRequired(
            id = "android.calendar",
            label = "Calendar access",
            explanation = "Calendar permission is required."
        )
        val registry = PluginRegistry(
            handlers = listOf(handler { calls++; PluginActionOutput(true, "ok") }),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {},
            accessChecker = PluginAccessChecker { _, _ -> permission }
        )
        registry.install(manifest)
        val descriptor = registry.connectedTools().single()
        assertEquals(permission, descriptor.access)
        val result = registry.execute(
            PluginExecutionRequest("test_plugin", "read_info", mapOf("locale" to PluginValue.Text("en")))
        )
        assertEquals(PluginExecutionStatus.PERMISSION_REQUIRED, result.status)
        assertEquals(0, calls)
    }

    @Test
    fun absentAccessCheckerFailsClosed() {
        var calls = 0
        val registry = PluginRegistry(
            handlers = listOf(handler { calls++; PluginActionOutput(true, "ok") }),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {}
        )
        registry.install(manifest)
        val result = registry.execute(
            PluginExecutionRequest("test_plugin", "read_info", mapOf("locale" to PluginValue.Text("en")))
        )
        assertEquals(PluginExecutionStatus.ACCESS_UNAVAILABLE, result.status)
        assertEquals(0, calls)
    }

    @Test
    fun androidCamelCaseArgumentNamesAreAcceptedAndStillValidatedAgainstCompiledSchema() {
        val androidArguments = mapOf(
            "startEpochMillis" to PluginFieldSpec(
                name = "startEpochMillis",
                type = PluginValueType.INTEGER,
                minimum = 1L
            ),
            "packageName" to PluginFieldSpec(
                name = "packageName",
                type = PluginValueType.STRING,
                maxLength = 200
            )
        )
        var calls = 0
        val handler = object : PluginActionHandler {
            override val actionId = "android_action"
            override val capability = PluginCapability.DEVICE_INFORMATION
            override val argumentSchema = androidArguments

            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                calls++
                assertEquals(123L, (arguments["startEpochMillis"] as PluginValue.Number).value)
                assertEquals("com.example.app", (arguments["packageName"] as PluginValue.Text).value)
                return PluginActionOutput(true, "accepted")
            }
        }
        val registry = PluginRegistry(
            handlers = listOf(handler),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker
        )
        val manifest = PluginManifest(
            id = "android_tools",
            version = "1.0.0",
            displayName = "Android tools",
            description = "Android actions with typed camelCase arguments.",
            capabilities = setOf(PluginCapability.DEVICE_INFORMATION),
            actions = listOf(
                PluginActionSpec(
                    id = "android_action",
                    capability = PluginCapability.DEVICE_INFORMATION,
                    arguments = androidArguments
                )
            )
        )

        assertTrue(registry.install(manifest))
        val result = registry.execute(
            PluginExecutionRequest(
                pluginId = "android_tools",
                actionId = "android_action",
                arguments = mapOf(
                    "startEpochMillis" to PluginValue.Number(123L),
                    "packageName" to PluginValue.Text("com.example.app")
                )
            )
        )

        assertTrue(result.success)
        assertEquals("accepted", result.message)
        assertEquals(1, calls)

        val invalidSchema = androidArguments + (
            "InvalidName" to PluginFieldSpec("InvalidName", PluginValueType.STRING)
        )
        val invalidRegistry = PluginRegistry(
            handlers = listOf(object : PluginActionHandler {
                override val actionId = "invalid_android_action"
                override val capability = PluginCapability.DEVICE_INFORMATION
                override val argumentSchema = invalidSchema
                override fun execute(arguments: Map<String, PluginValue>) = PluginActionOutput(true, "unexpected")
            }),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker
        )
        val invalidManifest = manifest.copy(
            actions = listOf(
                PluginActionSpec(
                    id = "invalid_android_action",
                    capability = PluginCapability.DEVICE_INFORMATION,
                    arguments = invalidSchema
                )
            )
        )
        assertTrue(runCatching { invalidRegistry.install(invalidManifest) }.isFailure)
    }

    private fun registry(consented: Boolean, action: () -> PluginActionOutput) = PluginRegistry(
        handlers = listOf(handler(action)),
        consentStore = PluginConsentStore { consented },
        auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker,
    )

    private class AndroidLikeConsentProbe(private val store: PluginConsentStore) { fun isGranted(id: String) = store.isGranted(id) }

    private fun handler(action: () -> PluginActionOutput) = object : PluginActionHandler {
        override val actionId = "read_info"
        override val capability = PluginCapability.DEVICE_INFORMATION
        override val argumentSchema = manifest.actions.single().arguments
        override fun execute(arguments: Map<String, PluginValue>) = action()
    }

    @Test
    fun compiledHandlerConfirmationCannotBeDisabledByImportedManifest() {
        var calls = 0
        val protectedHandler = object : PluginActionHandler {
            override val actionId = "read_info"
            override val capability = PluginCapability.DEVICE_INFORMATION
            override val argumentSchema = manifest.actions.single().arguments
            override val requiresConfirmation = true
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                calls++
                return PluginActionOutput(true, "completed")
            }
        }
        val registry = PluginRegistry(
            handlers = listOf(protectedHandler),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker
        )
        val importedManifest = manifest.copy(
            actions = manifest.actions.map { it.copy(requiresConfirmation = false) }
        )
        assertTrue(registry.install(importedManifest))
        assertTrue(registry.connectedTools().single().requiresConfirmation)

        val request = PluginExecutionRequest(
            "test_plugin",
            "read_info",
            mapOf("locale" to PluginValue.Text("en"))
        )
        val denied = registry.execute(request)
        assertEquals(PluginExecutionStatus.CONFIRMATION_REQUIRED, denied.status)
        assertEquals(0, calls)

        val approved = registry.execute(request.copy(userConfirmed = true))
        assertEquals(PluginExecutionStatus.SUCCEEDED, approved.status)
        assertEquals(1, calls)
    }

    @Test
    fun duplicatePluginIdentityCannotReplaceAnInstalledManifest() {
        val registry = registry(consented = true) { PluginActionOutput(true, "ok") }
        assertTrue(registry.install(manifest))

        val replacement = manifest.copy(
            displayName = "Untrusted replacement",
            description = "This metadata must not replace the installed identity."
        )

        assertFalse(registry.install(replacement))
        assertEquals(manifest, registry.installedPlugins().single())
    }

    @Test
    fun uninstallClearsOnlyTheRemovedPluginsRateLimitState() {
        var now = 1_000L
        var granted = true
        val consent = object : MutablePluginConsentStore {
            override fun isGranted(pluginId: String) = granted
            override fun setGranted(pluginId: String, value: Boolean) {
                granted = value
            }
        }
        val registry = PluginRegistry(
            handlers = listOf(handler { PluginActionOutput(true, "ok") }),
            consentStore = consent,
            auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker,
            clockMillis = { now },
            globalCallsPerMinute = 12
        )
        val limitedManifest = manifest.copy(maxCallsPerMinute = 1)
        val request = PluginExecutionRequest(
            "test_plugin",
            "read_info",
            mapOf("locale" to PluginValue.Text("en"))
        )

        assertTrue(registry.install(limitedManifest))
        assertEquals(PluginExecutionStatus.SUCCEEDED, registry.execute(request).status)
        assertEquals(PluginExecutionStatus.RATE_LIMITED, registry.execute(request).status)

        assertTrue(registry.uninstall("test_plugin"))
        assertTrue(registry.install(limitedManifest))
        assertTrue(registry.connect("test_plugin"))
        assertEquals(PluginExecutionStatus.SUCCEEDED, registry.execute(request).status)
    }

    @Test
    fun handlerExceptionsAreIsolatedAndDoNotPreventOtherActions() {
        val healthyAction = PluginActionSpec(
            id = "read_secondary",
            capability = PluginCapability.DEVICE_INFORMATION
        )
        val healthyHandler = object : PluginActionHandler {
            override val actionId = "read_secondary"
            override val capability = PluginCapability.DEVICE_INFORMATION
            override fun execute(arguments: Map<String, PluginValue>) =
                PluginActionOutput(true, "healthy action completed")
        }
        val registry = PluginRegistry(
            handlers = listOf(
                handler { throw IllegalStateException("private handler detail") },
                healthyHandler
            ),
            consentStore = PluginConsentStore { true },
            auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker
        )
        assertTrue(registry.install(manifest.copy(actions = manifest.actions + healthyAction)))

        val failed = registry.execute(
            PluginExecutionRequest(
                "test_plugin",
                "read_info",
                mapOf("locale" to PluginValue.Text("en"))
            )
        )
        assertEquals(PluginExecutionStatus.FAILED, failed.status)
        assertFalse(failed.message.contains("private handler detail"))

        val succeeded = registry.execute(
            PluginExecutionRequest("test_plugin", "read_secondary")
        )
        assertEquals(PluginExecutionStatus.SUCCEEDED, succeeded.status)
        assertEquals("healthy action completed", succeeded.message)
    }

    @Test
    fun consentStorageFailureFailsClosedWithoutCrashingCatalogOrExecution() {
        var calls = 0
        val registry = PluginRegistry(
            handlers = listOf(handler { calls++; PluginActionOutput(true, "ok") }),
            consentStore = PluginConsentStore { throw IllegalStateException("storage unavailable") },
            auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker
        )
        assertTrue(registry.install(manifest))

        assertFalse(registry.isConnected("test_plugin"))
        assertFalse(registry.toolCatalog().single().connected)
        val result = registry.execute(
            PluginExecutionRequest(
                "test_plugin",
                "read_info",
                mapOf("locale" to PluginValue.Text("en"))
            )
        )
        assertEquals(PluginExecutionStatus.ACCESS_UNAVAILABLE, result.status)
        assertEquals(0, calls)
    }

    @Test
    fun revokingConsentCancelsInflightHandlerAndReturnsTypedCancellation() {
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        var granted = true
        val consent = object : MutablePluginConsentStore {
            override fun isGranted(pluginId: String) = granted
            override fun setGranted(pluginId: String, value: Boolean) {
                granted = value
            }
        }
        val registry = PluginRegistry(
            handlers = listOf(handler {
                started.countDown()
                try {
                    CountDownLatch(1).await()
                    PluginActionOutput(true, "unexpected completion")
                } catch (error: InterruptedException) {
                    interrupted.countDown()
                    throw error
                }
            }),
            consentStore = consent,
            auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker
        )
        assertTrue(registry.install(manifest.copy(maxExecutionMillis = 5_000L)))
        val result = AtomicReference<PluginExecutionResult?>()
        val worker = Thread {
            result.set(
                registry.execute(
                    PluginExecutionRequest(
                        "test_plugin",
                        "read_info",
                        mapOf("locale" to PluginValue.Text("en"))
                    )
                )
            )
        }.apply {
            isDaemon = true
            start()
        }

        assertTrue(started.await(2, TimeUnit.SECONDS))
        assertTrue(registry.revokeConsent("test_plugin"))
        assertTrue(interrupted.await(2, TimeUnit.SECONDS))
        worker.join(2_000L)

        assertFalse(worker.isAlive)
        assertEquals(PluginExecutionStatus.CANCELLED, result.get()?.status)
        assertFalse(consent.isGranted("test_plugin"))
    }

    @Test
    fun installedManifestIsDefensivelyCopiedFromMutableCallerCollections() {
        val consent = PluginConsentStore { true }
        val registry = PluginRegistry(
            handlers = listOf(handler { PluginActionOutput(true, "ok") }),
            consentStore = consent,
            auditLog = PluginAuditLog {},
            accessChecker = testAccessChecker
        )
        val mutableCapabilities = mutableSetOf(PluginCapability.DEVICE_INFORMATION)
        val mutableAllowedValues = mutableSetOf("en", "ru", "tg")
        val mutableArguments = manifest.actions.single().arguments.toMutableMap().apply {
            this["locale"] = getValue("locale").copy(allowedValues = mutableAllowedValues)
        }
        val mutableActions = mutableListOf(
            manifest.actions.single().copy(arguments = mutableArguments)
        )
        val input = manifest.copy(
            capabilities = mutableCapabilities,
            actions = mutableActions
        )

        assertTrue(registry.install(input))
        mutableCapabilities.clear()
        mutableAllowedValues.clear()
        mutableArguments.clear()
        mutableActions.clear()

        val stored = registry.installedPlugins().single()
        assertEquals(setOf(PluginCapability.DEVICE_INFORMATION), stored.capabilities)
        assertEquals(1, stored.actions.size)
        assertEquals(setOf("en", "ru", "tg"), stored.actions.single().arguments.getValue("locale").allowedValues)
        assertTrue(
            registry.execute(
                PluginExecutionRequest(
                    "test_plugin",
                    "read_info",
                    mapOf("locale" to PluginValue.Text("en"))
                )
            ).success
        )
    }

}
