package com.khayom1.missminutes.plugins

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Strictly parses model arguments and routes them to a currently connected compiled handler. */
class PluginToolCallDispatcher(
    private val registry: PluginRegistry,
    private val json: Json = Json
) {
    fun execute(
        toolName: String,
        argumentsJson: String,
        userConfirmed: Boolean = false
    ): PluginExecutionResult {
        val descriptor = registry.connectedTools().firstOrNull { it.toolName == toolName }
            ?: return rejected(PluginExecutionStatus.ACTION_NOT_ALLOWED, "Tool is not in the connected plugin catalog.")
        if (argumentsJson.length !in 2..MAX_ARGUMENT_CHARACTERS) {
            return rejected(PluginExecutionStatus.INVALID_ARGUMENTS, "Tool arguments are outside the allowed size.")
        }

        val objectValue = try {
            json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Exception) {
            return rejected(PluginExecutionStatus.INVALID_ARGUMENTS, "Tool arguments must be a valid JSON object.")
        }
        if (objectValue.keys.any { it !in descriptor.arguments }) {
            return rejected(PluginExecutionStatus.INVALID_ARGUMENTS, "Tool arguments contain an unknown field.")
        }

        val values = mutableMapOf<String, PluginValue>()
        for ((name, element) in objectValue) {
            val field = descriptor.arguments[name]
                ?: return rejected(PluginExecutionStatus.INVALID_ARGUMENTS, "Tool argument is not allowlisted.")
            val primitive: JsonPrimitive = try {
                element.jsonPrimitive
            } catch (_: Exception) {
                return rejected(PluginExecutionStatus.INVALID_ARGUMENTS, "Tool arguments must be primitive values.")
            }
            val value = when (field.type) {
                PluginValueType.STRING -> {
                    if (!primitive.isString) return rejected(
                        PluginExecutionStatus.INVALID_ARGUMENTS,
                        "Tool argument type does not match its schema."
                    )
                    PluginValue.Text(primitive.content)
                }
                PluginValueType.INTEGER -> {
                    // kotlinx.serialization's numeric accessors also parse quoted strings.
                    // Require a native JSON number so model output cannot blur schema types.
                    if (primitive.isString) return rejected(
                        PluginExecutionStatus.INVALID_ARGUMENTS,
                        "Tool argument type does not match its schema."
                    )
                    PluginValue.Number(
                        primitive.longOrNull ?: return rejected(
                            PluginExecutionStatus.INVALID_ARGUMENTS,
                            "Tool argument type does not match its schema."
                        )
                    )
                }
                PluginValueType.BOOLEAN -> {
                    // A JSON string such as "true" is not a JSON boolean.
                    if (primitive.isString) return rejected(
                        PluginExecutionStatus.INVALID_ARGUMENTS,
                        "Tool argument type does not match its schema."
                    )
                    PluginValue.Flag(
                        primitive.booleanOrNull ?: return rejected(
                            PluginExecutionStatus.INVALID_ARGUMENTS,
                            "Tool argument type does not match its schema."
                        )
                    )
                }
            }
            values[name] = value
        }

        return registry.execute(
            PluginExecutionRequest(
                pluginId = descriptor.pluginId,
                actionId = descriptor.actionId,
                arguments = values,
                userConfirmed = userConfirmed
            )
        )
    }

    private fun rejected(status: PluginExecutionStatus, message: String) = PluginExecutionResult(
        success = false,
        message = message,
        capability = PluginCapability.DEVICE_INFORMATION,
        status = status
    )

    private companion object {
        const val MAX_ARGUMENT_CHARACTERS = 2_000
    }
}
