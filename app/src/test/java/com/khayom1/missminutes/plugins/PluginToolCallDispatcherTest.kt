package com.khayom1.missminutes.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginToolCallDispatcherTest {
    private class ConsentStore : MutablePluginConsentStore {
        private val grants = mutableMapOf<String, Boolean>()
        override fun isGranted(pluginId: String) = grants[pluginId] == true
        override fun setGranted(pluginId: String, granted: Boolean) {
            grants[pluginId] = granted
        }
    }

    private fun manifest(requiresConfirmation: Boolean = false) = PluginManifest(
        id = "test_plugin",
        version = "1.0.0",
        displayName = "Test plugin",
        description = "Read a bounded status value.",
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
                ),
                requiresConfirmation = requiresConfirmation
            )
        )
    )

    private fun setup(requiresConfirmation: Boolean = false): Triple<PluginToolCallDispatcher, () -> Int, PluginRegistry> {
        var calls = 0
        val consent = ConsentStore()
        val registry = PluginRegistry(
            handlers = listOf(object : PluginActionHandler {
                override val actionId = "read_info"
                override val capability = PluginCapability.DEVICE_INFORMATION
                override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                    calls++
                    return PluginActionOutput(true, (arguments["locale"] as PluginValue.Text).value)
                }
            }),
            consentStore = consent,
            auditLog = PluginAuditLog {},
            accessChecker = PluginAccessChecker { _, _ -> PluginAccessDecision.Granted }
        )
        registry.install(manifest(requiresConfirmation))
        check(registry.connect("test_plugin"))
        return Triple(PluginToolCallDispatcher(registry), { calls }, registry)
    }

    @Test
    fun connectedToolExecutesOnlyTypedAllowlistedArguments() {
        val (dispatcher, calls, registry) = setup()
        val name = registry.connectedTools().single().toolName
        val result = dispatcher.execute(name, """{"locale":"tg"}""")
        assertTrue(result.success)
        assertEquals("tg", result.message)
        assertEquals(1, calls())
    }

    @Test
    fun unknownFieldsAndWrongTypesAreRejectedBeforeHandler() {
        val (dispatcher, calls, registry) = setup()
        val name = registry.connectedTools().single().toolName
        assertEquals(
            PluginExecutionStatus.INVALID_ARGUMENTS,
            dispatcher.execute(name, """{"locale":"tg","shell":"id"}""").status
        )
        assertEquals(
            PluginExecutionStatus.INVALID_ARGUMENTS,
            dispatcher.execute(name, """{"locale":7}""").status
        )
        assertEquals(
            PluginExecutionStatus.INVALID_ARGUMENTS,
            dispatcher.execute(name, """not-json""").status
        )
        assertEquals(0, calls())
    }

    @Test
    fun consequentialActionCannotRunWithoutExplicitConfirmation() {
        val (dispatcher, calls, registry) = setup(requiresConfirmation = true)
        val name = registry.connectedTools().single().toolName
        val denied = dispatcher.execute(name, """{"locale":"en"}""")
        assertFalse(denied.success)
        assertEquals(PluginExecutionStatus.CONFIRMATION_REQUIRED, denied.status)
        assertEquals(0, calls())

        val approved = dispatcher.execute(name, """{"locale":"en"}""", userConfirmed = true)
        assertTrue(approved.success)
        assertEquals(1, calls())
    }
}
