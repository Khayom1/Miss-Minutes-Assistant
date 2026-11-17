package com.khayom1.missminutes.plugins

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Converts the trusted local catalog into provider-neutral function-tool JSON Schema. */
object PluginToolSchemaFactory {
    fun create(tools: Collection<PluginToolDescriptor>): JsonArray {
        require(tools.map { it.toolName }.distinct().size == tools.size) {
            "Tool names must be unique."
        }
        return buildJsonArray {
            tools.filter { it.connected }.sortedBy { it.toolName }.forEach { tool ->
                add(buildJsonObject {
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", tool.toolName)
                        put("description", "${tool.pluginName}: ${tool.description}".take(512))
                        put("parameters", parametersSchema(tool.arguments))
                    })
                })
            }
        }
    }

    private fun parametersSchema(arguments: Map<String, PluginFieldSpec>): JsonObject =
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                arguments.toSortedMap().forEach { (name, field) ->
                    put(name, buildJsonObject {
                        put(
                            "type",
                            when (field.type) {
                                PluginValueType.STRING -> "string"
                                PluginValueType.INTEGER -> "integer"
                                PluginValueType.BOOLEAN -> "boolean"
                            }
                        )
                        if (field.type == PluginValueType.STRING) {
                            put("maxLength", field.maxLength)
                            if (field.allowedValues.isNotEmpty()) {
                                put("enum", JsonArray(field.allowedValues.sorted().map {
                                    kotlinx.serialization.json.JsonPrimitive(it)
                                }))
                            }
                        }
                        field.minimum?.let { put("minimum", it) }
                        field.maximum?.let { put("maximum", it) }
                    })
                }
            })
            put("required", JsonArray(arguments.values.filter { it.required }.sortedBy { it.name }.map {
                kotlinx.serialization.json.JsonPrimitive(it.name)
            }))
            put("additionalProperties", false)
        }
}
