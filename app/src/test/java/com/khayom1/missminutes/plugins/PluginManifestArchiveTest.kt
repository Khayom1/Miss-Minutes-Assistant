package com.khayom1.missminutes.plugins

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestArchiveTest {
    private val manifest = PluginManifest(
        id = "archive_test",
        version = "1.2.3",
        displayName = "Archive test",
        description = "Declarative plugin metadata.",
        capabilities = setOf(PluginCapability.DEVICE_INFORMATION),
        actions = listOf(
            PluginActionSpec(
                id = "read_info",
                capability = PluginCapability.DEVICE_INFORMATION,
                arguments = mapOf(
                    "locale" to PluginFieldSpec(
                        name = "locale",
                        type = PluginValueType.STRING,
                        required = true,
                        maxLength = 8,
                        allowedValues = setOf("en", "ru", "tg")
                    )
                ),
                requiresConfirmation = true
            )
        )
    )

    @Test
    fun roundTripPreservesOnlyDeclarativeManifestFields() {
        val decoded = PluginManifestArchive.decode(PluginManifestArchive.encode(listOf(manifest)))
        assertEquals(listOf(manifest), decoded)
        assertFalse(PluginManifestArchive.encode(listOf(manifest)).toString().contains("credential"))
        assertFalse(PluginManifestArchive.encode(listOf(manifest)).toString().contains("consent"))
    }

    @Test
    fun duplicatePluginIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PluginManifestArchive.encode(listOf(manifest, manifest))
        }
    }

    @Test
    fun unknownSchemaVersionAndUnknownEnumAreRejected() {
        val payload = PluginManifestArchive.encode(listOf(manifest))
        val wrongVersion = kotlinx.serialization.json.buildJsonObject {
            put("schemaVersion", 2)
            put("plugins", payload.getValue("plugins"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PluginManifestArchive.decode(wrongVersion)
        }
        val invalidEnum = kotlinx.serialization.json.Json.parseToJsonElement(
            payload.toString().replace("DEVICE_INFORMATION", "ROOT_ACCESS")
        ).jsonObject
        assertThrows(IllegalArgumentException::class.java) {
            PluginManifestArchive.decode(invalidEnum)
        }
    }

    @Test
    fun rejectsUnknownTopLevelFields() {
        val payload = PluginManifestArchive.encode(listOf(manifest)).toMutableMap()
        payload["apiKey"] = JsonPrimitive("secret")
        assertThrows(IllegalArgumentException::class.java) {
            PluginManifestArchive.decode(JsonObject(payload))
        }
    }

    @Test
    fun rejectsStringEncodedConfirmationAndFieldPolicyBooleans() {
        val payload = PluginManifestArchive.encode(listOf(manifest))
        val invalidConfirmation = kotlinx.serialization.json.Json.parseToJsonElement(
            payload.toString().replace("\"requiresConfirmation\":true", "\"requiresConfirmation\":\"false\"")
        ).jsonObject
        assertThrows(IllegalArgumentException::class.java) {
            PluginManifestArchive.decode(invalidConfirmation)
        }
        val invalidRequired = kotlinx.serialization.json.Json.parseToJsonElement(
            payload.toString().replace("\"required\":true", "\"required\":\"false\"")
        ).jsonObject
        assertThrows(IllegalArgumentException::class.java) {
            PluginManifestArchive.decode(invalidRequired)
        }
    }

    @Test
    fun importedManifestStillRequiresRegistryConsentAndCompiledHandler() {
        var executed = false
        val restored = PluginManifestArchive.decode(PluginManifestArchive.encode(listOf(manifest))).single()
        val registry = PluginRegistry(
            handlers = listOf(object : PluginActionHandler {
                override val actionId = "read_info"
                override val capability = PluginCapability.DEVICE_INFORMATION
                override val argumentSchema = restored.actions.single().arguments
                override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                    executed = true
                    return PluginActionOutput(true, "ok")
                }
            }),
            consentStore = PluginConsentStore { false },
            auditLog = PluginAuditLog {},
            accessChecker = PluginAccessChecker { _, _ -> PluginAccessDecision.Granted }
        )
        assertTrue(registry.install(restored))
        val result = registry.execute(
            PluginExecutionRequest("archive_test", "read_info", mapOf("locale" to PluginValue.Text("en")))
        )
        assertFalse(result.success)
        assertFalse(executed)
    }
}
