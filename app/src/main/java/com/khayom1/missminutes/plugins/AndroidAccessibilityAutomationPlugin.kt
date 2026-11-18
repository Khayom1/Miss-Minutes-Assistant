package com.khayom1.missminutes.plugins

import com.khayom1.missminutes.accessibility.AccessibilityAutomationService

/**
 * Generic automation for the currently visible Android application.
 * No app package names or app-specific selectors are embedded here.
 */
object AndroidAccessibilityAutomationPlugin {
    const val PLUGIN_ID = "android_accessibility_automation"

    fun manifest() = PluginManifest(
        id = PLUGIN_ID,
        version = "1.0.0",
        displayName = "Cross-app accessibility automation",
        description = "Inspect the active app screen, tap one uniquely labeled visible control, type into a focused or labeled field, scroll, and go back. Works across apps that expose accessible UI controls. Password fields and dangerous destructive, financial, or account-security controls are blocked.",
        capabilities = setOf(PluginCapability.ACCESSIBILITY_AUTOMATION),
        actions = listOf(
            PluginActionSpec("inspect_screen", PluginCapability.ACCESSIBILITY_AUTOMATION),
            PluginActionSpec(
                "click_text",
                PluginCapability.ACCESSIBILITY_AUTOMATION,
                arguments = mapOf(
                    "text" to PluginFieldSpec("text", PluginValueType.STRING, maxLength = 128)
                )
            ),
            PluginActionSpec(
                "click_confirmed_text",
                PluginCapability.ACCESSIBILITY_AUTOMATION,
                arguments = mapOf(
                    "text" to PluginFieldSpec("text", PluginValueType.STRING, maxLength = 128)
                ),
                requiresConfirmation = true
            ),
            PluginActionSpec(
                "type_text",
                PluginCapability.ACCESSIBILITY_AUTOMATION,
                arguments = mapOf(
                    "text" to PluginFieldSpec("text", PluginValueType.STRING, maxLength = 1_700),
                    "target" to PluginFieldSpec("target", PluginValueType.STRING, required = false, maxLength = 128)
                )
            ),
            PluginActionSpec(
                "scroll_screen",
                PluginCapability.ACCESSIBILITY_AUTOMATION,
                arguments = mapOf(
                    "direction" to PluginFieldSpec(
                        "direction",
                        PluginValueType.STRING,
                        maxLength = 8,
                        allowedValues = setOf("up", "down")
                    )
                )
            ),
            PluginActionSpec("press_back", PluginCapability.ACCESSIBILITY_AUTOMATION)
        ),
        maxCallsPerMinute = 12,
        maxExecutionMillis = 4_500L
    )

    fun handlers(): List<PluginActionHandler> = listOf(
        action("inspect_screen", emptyMap()) { _ -> AccessibilityAutomationService.inspectScreen() },
        action("click_text", manifest().actions.first { it.id == "click_text" }.arguments) { arguments ->
            val text = (arguments["text"] as? PluginValue.Text)?.value.orEmpty()
            AccessibilityAutomationService.clickText(text)
        },
        action(
            "click_confirmed_text",
            manifest().actions.first { it.id == "click_confirmed_text" }.arguments,
            requiresConfirmation = true
        ) { arguments ->
            val text = (arguments["text"] as? PluginValue.Text)?.value.orEmpty()
            AccessibilityAutomationService.clickText(text, allowDangerousAction = true)
        },
        action("type_text", manifest().actions.first { it.id == "type_text" }.arguments) { arguments ->
            val text = (arguments["text"] as? PluginValue.Text)?.value.orEmpty()
            val target = (arguments["target"] as? PluginValue.Text)?.value
            AccessibilityAutomationService.typeText(text, target)
        },
        action("scroll_screen", manifest().actions.first { it.id == "scroll_screen" }.arguments) { arguments ->
            val direction = (arguments["direction"] as? PluginValue.Text)?.value.orEmpty()
            AccessibilityAutomationService.scrollScreen(direction)
        },
        action("press_back", emptyMap()) { _ -> AccessibilityAutomationService.pressBack() }
    )

    private fun action(
        id: String,
        schema: Map<String, PluginFieldSpec> = emptyMap(),
        requiresConfirmation: Boolean = false,
        operation: (Map<String, PluginValue>) -> String
    ): PluginActionHandler = object : PluginActionHandler {
        override val actionId: String = id
        override val capability: PluginCapability = PluginCapability.ACCESSIBILITY_AUTOMATION
        override val argumentSchema: Map<String, PluginFieldSpec> = schema
        override val requiresConfirmation: Boolean = requiresConfirmation

        override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
            val message = operation(arguments)
            return PluginActionOutput(message.startsWith("OK:"), message.removePrefix("OK:").removePrefix("ERROR:").trim())
        }
    }
}
