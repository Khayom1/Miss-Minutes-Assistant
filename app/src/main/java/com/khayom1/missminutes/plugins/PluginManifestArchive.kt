package com.khayom1.missminutes.plugins

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Portable, declarative plugin metadata only. No executable code, credentials,
 * consent grants, runtime state, or Android permissions are serialized.
 */
object PluginManifestArchive {
    private const val SCHEMA_VERSION = 1
    private const val MAX_ARCHIVE_BYTES = 256_000
    private const val MAX_PLUGINS = 100

    fun encode(manifests: List<PluginManifest>): JsonObject {
        require(manifests.size <= MAX_PLUGINS)
        require(manifests.map { it.id }.distinct().size == manifests.size)
        return buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("plugins", buildJsonArray {
                manifests.sortedBy { it.id }.forEach { manifest ->
                    add(manifest.toJson())
                }
            })
        }
    }

    fun decode(payload: JsonObject): List<PluginManifest> {
        require(payload.toString().toByteArray(Charsets.UTF_8).size <= MAX_ARCHIVE_BYTES)
        val version = payload["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw IllegalArgumentException("Plugin archive schema version is missing.")
        require(version == SCHEMA_VERSION) { "Unsupported plugin archive version." }
        val entries = payload["plugins"]?.jsonArray
            ?: throw IllegalArgumentException("Plugin list is missing.")
        require(entries.size <= MAX_PLUGINS)
        val manifests = entries.map { entry ->
            val item = entry.jsonObject
            val actions = item["actions"]?.jsonArray
                ?: throw IllegalArgumentException("Plugin actions are missing.")
            require(actions.isNotEmpty() && actions.size <= 16)
            val parsedActions = actions.map { rawAction ->
                val action = rawAction.jsonObject
                val argumentsObject = action["arguments"]?.jsonObject ?: JsonObject(emptyMap())
                require(argumentsObject.size <= 8)
                val arguments = argumentsObject.mapValues { (key, rawField) ->
                    val field = rawField.jsonObject
                    val allowed = field["allowedValues"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
                    require(allowed.size <= 32)
                    PluginFieldSpec(
                        name = field.requiredString("name"),
                        type = enumValue<PluginValueType>(field.requiredString("type")),
                        required = field["required"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                        maxLength = field["maxLength"]?.jsonPrimitive?.content?.toIntOrNull() ?: 256,
                        minimum = field["minimum"]?.jsonPrimitive?.content?.toLongOrNull(),
                        maximum = field["maximum"]?.jsonPrimitive?.content?.toLongOrNull(),
                        allowedValues = allowed
                    ).also { require(key == it.name) }
                }
                PluginActionSpec(
                    id = action.requiredString("id"),
                    capability = enumValue(action.requiredString("capability")),
                    arguments = arguments,
                    requiresConfirmation = action["requiresConfirmation"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                )
            }
            PluginManifest(
                id = item.requiredString("id"),
                version = item.requiredString("version"),
                displayName = item.requiredString("displayName"),
                description = item.requiredString("description"),
                capabilities = item["capabilities"]?.jsonArray?.map {
                    enumValue<PluginCapability>(it.jsonPrimitive.content)
                }?.toSet() ?: throw IllegalArgumentException("Plugin capabilities are missing."),
                actions = parsedActions,
                maxCallsPerMinute = item["maxCallsPerMinute"]?.jsonPrimitive?.content?.toIntOrNull() ?: 6,
                maxExecutionMillis = item["maxExecutionMillis"]?.jsonPrimitive?.content?.toLongOrNull() ?: 1_500L
            )
        }
        require(manifests.map { it.id }.distinct().size == manifests.size)
        return manifests
    }

    private fun PluginManifest.toJson() = buildJsonObject {
        put("id", id)
        put("version", version)
        put("displayName", displayName)
        put("description", description)
        put("capabilities", buildJsonArray {
            capabilities.sortedBy { it.name }.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.name)) }
        })
        put("maxCallsPerMinute", maxCallsPerMinute)
        put("maxExecutionMillis", maxExecutionMillis)
        put("actions", buildJsonArray {
            actions.forEach { action ->
                add(buildJsonObject {
                    put("id", action.id)
                    put("capability", action.capability.name)
                    put("requiresConfirmation", action.requiresConfirmation)
                    put("arguments", buildJsonObject {
                        action.arguments.toSortedMap().forEach { (key, field) ->
                            put(key, buildJsonObject {
                                put("name", field.name)
                                put("type", field.type.name)
                                put("required", field.required)
                                put("maxLength", field.maxLength)
                                field.minimum?.let { put("minimum", it) }
                                field.maximum?.let { put("maximum", it) }
                                put("allowedValues", JsonArray(field.allowedValues.sorted().map {
                                    kotlinx.serialization.json.JsonPrimitive(it)
                                }))
                            })
                        }
                    })
                })
            }
        })
    }

    private fun JsonObject.requiredString(key: String): String =
        this[key]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Plugin field '$key' is missing.")

    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        try { enumValueOf<T>(value) } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Plugin archive contains an unknown enum value.", error)
        }
}
