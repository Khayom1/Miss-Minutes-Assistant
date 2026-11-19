package com.khayom1.missminutes.plugins

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginToolSchemaFactoryTest {
    private fun descriptor(connected: Boolean, requiresConfirmation: Boolean = false) = PluginToolDescriptor(
        toolName = "plugin_test",
        pluginId = "test_plugin",
        actionId = "read_info",
        pluginName = "Test plugin",
        description = "Read a bounded status value.",
        capability = PluginCapability.DEVICE_INFORMATION,
        arguments = mapOf(
            "locale" to PluginFieldSpec(
                name = "locale",
                type = PluginValueType.STRING,
                maxLength = 8,
                allowedValues = setOf("en", "ru", "tg")
            )
        ),
        requiresConfirmation = requiresConfirmation,
        connected = connected,
        access = PluginAccessDecision.Granted,
        sideEffect = requiresConfirmation
    )

    @Test
    fun schemaContainsOnlyConnectedToolsAndDisallowsUnknownArguments() {
        val schema = PluginToolSchemaFactory.create(listOf(descriptor(true), descriptor(false)))
        assertEquals(1, schema.size)
        val function = schema.single().jsonObject["function"]!!.jsonObject
        assertEquals("plugin_test", function["name"]!!.jsonPrimitive.content)
        val parameters = function["parameters"]!!.jsonObject
        assertFalse(parameters["additionalProperties"]!!.jsonPrimitive.boolean)
        assertEquals("locale", parameters["required"]!!.jsonArray.single().jsonPrimitive.content)
        val locale = parameters["properties"]!!.jsonObject["locale"]!!.jsonObject
        assertEquals(listOf("en", "ru", "tg"), locale["enum"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun writeToolSchemaDisclosesConfirmationRequirement() {
        val definition = PluginToolSchemaFactory.toAiToolDefinitions(
            listOf(descriptor(connected = true, requiresConfirmation = true))
        ).single()

        assertTrue(definition.description.contains("Requires explicit user confirmation before execution."))
        assertTrue(definition.sideEffect)
    }

    @Test
    fun duplicateDisconnectedToolNamesAreIgnored() {
        val schema = PluginToolSchemaFactory.create(listOf(descriptor(false), descriptor(false)))
        assertTrue(schema.isEmpty())
    }

    @Test
    fun duplicateToolNamesAreRejected() {
        try {
            PluginToolSchemaFactory.create(listOf(descriptor(true), descriptor(true)))
            throw AssertionError("Expected duplicate tool names to be rejected.")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
