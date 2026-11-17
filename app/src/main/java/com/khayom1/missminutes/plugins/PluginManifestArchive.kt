package com.khayom1.missminutes.plugins

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
        val encoded = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("plugins", buildJsonArray {
                manifests.sortedBy { it.id }.forEach { manifest ->
                    add(manifest.toJson())
                }
            })
        }
        require(encoded.toString().toByteArray(Charsets.UTF_8).size <= MAX_ARCHIVE_BYTES)
        return encoded
    }

    fun decode(payload: JsonObject): List<PluginManifest> {
        require(payload.toString().toByteArray(Charsets.UTF_8).size <= MAX_ARCHIVE_BYTES)
        require(payload.keys == setOf("schemaVersion", "plugins")) { "Unknown plugin archive fields." }
        val version = payload["schemaVersion"]?.jsonPrimitive?.takeIf { !it.isString }?.content?.toIntOrNull()
            ?: throw IllegalArgumentException("Plugin archive schema version is missing.")
        require(version == SCHEMA_VERSION) { "Unsupported plugin archive version." }
        val entries = payload["plugins"]?.jsonArray
            ?: throw IllegalArgumentException("Plugin list is missing.")
        require(entries.size <= MAX_PLUGINS)
        val manifests = entries.map { entry ->
            val item = entry.jsonObject
            require(item.keys == setOf("id", "version", "displayName", "description", "capabilities", "maxCallsPerMinute", "maxExecutionMillis", "actions"))
            val actions = item["actions"]?.jsonArray
                ?: throw IllegalArgumentException("Plugin actions are missing.")
            require(actions.isNotEmpty() && actions.size <= 16)
            val parsedActions = actions.map { rawAction ->
                val action = rawAction.jsonObject
                require(action.keys == setOf("id", "capability", "requiresConfirmation", "arguments"))
                val argumentsObject = action["arguments"]?.jsonObject
                    ?: throw IllegalArgumentException("Plugin action arguments are missing.")
                require(argumentsObject.size <= 8)
                val arguments = argumentsObject.mapValues { (key, rawField) ->
                    val field = rawField.jsonObject
                    require(field.keys.all {
                        it in setOf("name", "type", "required", "maxLength", "minimum", "maximum", "allowedValues")
                    })
                    require(field.keys.containsAll(setOf("name", "type", "required", "maxLength", "allowedValues")))
                    val allowed = field["allowedValues"]?.jsonArray?.map {
                        it.jsonPrimitive.also { primitive -> require(primitive.isString) }.content
                    }?.toSet() ?: emptySet()
                    require(allowed.size <= 32 && field["allowedValues"]?.jsonArray?.size == allowed.size)
                    PluginFieldSpec(
                        name = field.requiredString("name"),
                        type = enumValue<PluginValueType>(field.requiredString("type")),
                        required = field.requiredBoolean("required"),
                        maxLength = field.requiredInt("maxLength"),
                        minimum = field.optionalLong("minimum"),
                        maximum = field.optionalLong("maximum"),
                        allowedValues = allowed
                    ).also { spec ->
                        require(key == spec.name)
                        require(spec.maxLength in 1..2_000)
                        require(spec.minimum == null || spec.maximum == null || spec.minimum <= spec.maximum)
                        require(spec.allowedValues.all { value -> value.length <= spec.maxLength })
                    }
                }
                PluginActionSpec(
                    id = action.requiredString("id"),
                    capability = enumValue(action.requiredString("capability")),
                    arguments = arguments,
                    requiresConfirmation = action.requiredBoolean("requiresConfirmation")
                )
            }
            val rawCapabilities = item["capabilities"]?.jsonArray
                ?: throw IllegalArgumentException("Plugin capabilities are missing.")
            val capabilities = rawCapabilities.map {
                enumValue<PluginCapability>(it.jsonPrimitive.also { primitive -> require(primitive.isString) }.content)
            }.toSet()
            require(capabilities.isNotEmpty() && capabilities.size == rawCapabilities.size)
            val manifest = PluginManifest(
                id = item.requiredString("id"),
                version = item.requiredString("version"),
                displayName = item.requiredString("displayName"),
                description = item.requiredString("description"),
                capabilities = capabilities,
                actions = parsedActions,
                maxCallsPerMinute = item.requiredInt("maxCallsPerMinute"),
                maxExecutionMillis = item.requiredLong("maxExecutionMillis")
            )
            require(manifest.capabilities == manifest.actions.map { it.capability }.toSet())
            require(manifest.maxCallsPerMinute in 1..12 && manifest.maxExecutionMillis in 1L..5_000L)
            manifest
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

    private fun JsonObject.requiredString(key: String): String {
        val value = this[key]?.jsonPrimitive ?: throw IllegalArgumentException("Plugin field '$key' is missing.")
        require(value.isString)
        return value.content
    }

    private fun JsonObject.requiredBoolean(key: String): Boolean {
        val value = this[key]?.jsonPrimitive ?: throw IllegalArgumentException("Plugin field '$key' is missing.")
        require(!value.isString)
        return value.content.toBooleanStrictOrNull()
            ?: throw IllegalArgumentException("Plugin field '$key' is invalid.")
    }

    private fun JsonObject.requiredInt(key: String): Int {
        val value = this[key]?.jsonPrimitive ?: throw IllegalArgumentException("Plugin field '$key' is missing.")
        require(!value.isString)
        return value.content.toIntOrNull() ?: throw IllegalArgumentException("Plugin field '$key' is invalid.")
    }

    private fun JsonObject.requiredLong(key: String): Long {
        val value = this[key]?.jsonPrimitive ?: throw IllegalArgumentException("Plugin field '$key' is missing.")
        require(!value.isString)
        return value.content.toLongOrNull() ?: throw IllegalArgumentException("Plugin field '$key' is invalid.")
    }

    private fun JsonObject.optionalLong(key: String): Long? {
        val value = this[key]?.jsonPrimitive ?: return null
        require(!value.isString)
        return value.content.toLongOrNull() ?: throw IllegalArgumentException("Plugin field '$key' is invalid.")
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        try { enumValueOf<T>(value) } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Plugin archive contains an unknown enum value.", error)
        }
}
