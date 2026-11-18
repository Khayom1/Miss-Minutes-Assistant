package com.khayom1.missminutes.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolCall
import com.google.ai.edge.litertlm.tool
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject

/**
 * LiteRT-LM text and structured-tool inference on CPU.
 *
 * All Engine/Conversation operations are confined to one worker thread. Model
 * tool calls are returned to the app rather than auto-executed by the SDK, so
 * the shared AiToolExecutionLoop and user approval gate remain authoritative.
 */
internal fun newLocalToolCallId(id: UUID = UUID.randomUUID()): String =
    "local_${id.toString().replace("-", "")}"

class LiteRtLmInferenceRuntime(
    private val modelPath: () -> String?,
    private val cacheDirectory: File
) : LocalInferenceRuntime, AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "miss-minutes-litertlm").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val activeRequests = ConcurrentHashMap.newKeySet<Job>()
    private val mutex = Mutex()
    private val closed = AtomicBoolean(false)

    private var engine: Engine? = null
    private var loadedPath: String? = null

    override fun isAvailable(): Boolean {
        if (closed.get()) return false
        val path = modelPath() ?: return false
        val file = File(path)
        return file.isFile &&
            file.length() > 0L &&
            file.name.endsWith(".litertlm", ignoreCase = true)
    }

    /** Initializes the exact staged model off the main thread before the store activates it. */
    suspend fun validateModel(path: String) = withContext(dispatcher) {
        check(!closed.get()) { "Local inference runtime is closed." }
        mutex.withLock {
            val file = File(path).canonicalFile
            if (!file.isFile || file.length() <= 0L ||
                !file.name.endsWith(".litertlm", ignoreCase = true)
            ) {
                throw IOException("The selected .litertlm model file is missing, empty or invalid.")
            }
            val candidate = try {
                Engine(
                    EngineConfig(
                        modelPath = file.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = cacheDirectory.absolutePath
                    )
                )
            } catch (error: LinkageError) {
                throw IOException("The LiteRT-LM native runtime is unavailable on this device.", error)
            }
            var initialized = false
            try {
                candidate.initialize()
                initialized = true
            } catch (error: Exception) {
                throw IOException("LiteRT-LM could not initialize this model. Choose a compatible .litertlm file.", error)
            } catch (error: LinkageError) {
                throw IOException("The LiteRT-LM native runtime is unavailable on this device.", error)
            } finally {
                if (initialized) runCatching { candidate.close() }
            }
        }
    }

    override suspend fun generate(
        messages: List<AiMessage>,
        maxOutputTokens: Int
    ): String = complete(
        AiRequest(messages = messages, maxOutputTokens = maxOutputTokens)
    ).content

    override suspend fun complete(request: AiRequest): AiCompletion = coroutineScope {
        check(!closed.get()) { "Local inference runtime is closed." }
        val requestJob = Job(currentCoroutineContext()[Job])
        activeRequests.add(requestJob)
        if (closed.get()) requestJob.cancel(CancellationException("Local inference runtime is closed."))
        try {
            withContext(dispatcher + requestJob) {
            check(!closed.get()) { "Local inference runtime is closed." }
            require(request.maxOutputTokens in 1..768)
            require(request.messages.isNotEmpty())

            mutex.withLock {
                check(!closed.get()) { "Local inference runtime is closed." }
                val path = modelPath()?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Import a .litertlm model in Settings first.")
                val modelFile = File(path)
                if (!modelFile.isFile || modelFile.length() <= 0L) {
                    throw IOException("The selected local model file is missing or empty.")
                }
                ensureEngine(path)

                val systemInstruction = request.messages
                    .filter { it.role == AiRole.SYSTEM }
                    .joinToString("\n") { it.content }
                    .ifBlank { DEFAULT_SYSTEM_INSTRUCTION }
                val conversationHistory = request.messages.dropLast(1).map(::toRuntimeMessage)
                val toolAdapters = request.tools.map { definition ->
                    tool(RegistryOpenApiTool(definition))
                }
                val conversation = checkNotNull(engine).createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemInstruction),
                        initialMessages = conversationHistory,
                        tools = toolAdapters,
                        automaticToolCalling = false,
                        maxOutputToken = request.maxOutputTokens
                    )
                )

                try {
                    val lastMessage = request.messages.last()
                    val modelResponse = if (lastMessage.role == AiRole.USER) {
                        conversation.sendMessageAsync(lastMessage.content)
                    } else {
                        conversation.sendMessageAsync(
                            Message.tool(
                                Contents.of(
                                    Content.ToolResponse(
                                        lastMessage.name.orEmpty(),
                                        parseToolResponse(lastMessage.content)
                                    )
                                )
                            )
                        )
                    }

                    val output = StringBuilder()
                    var finalMessage: Message? = null
                    try {
                        modelResponse.collect { chunk ->
                            finalMessage = chunk
                            val part = chunk.toString()
                            if (output.length + part.length > MAX_OUTPUT_CHARACTERS) {
                                throw IOException("Local model response exceeded the output limit.")
                            }
                            output.append(part)
                        }
                    } catch (error: CancellationException) {
                        runCatching { conversation.cancelProcess() }
                        throw error
                    }

                    val responseMessage = finalMessage
                        ?: throw IOException("The local model returned no response.")
                    val toolCalls = responseMessage.toolCalls.map { call ->
                        AiToolCall(
                            id = newLocalToolCallId(),
                            name = call.name,
                            arguments = JSONObject(call.arguments).toString()
                        )
                    }
                    if (toolCalls.isNotEmpty() &&
                        (request.tools.isEmpty() ||
                            toolCalls.size > 4 ||
                            toolCalls.any { call -> request.tools.none { it.name == call.name } })
                    ) {
                        throw AiProviderException.InvalidResponse()
                    }

                    AiCompletion(
                        content = output.toString().trim(),
                        model = "local",
                        toolCalls = toolCalls
                    )
                } finally {
                    conversation.close()
                }
            }
            }
        } finally {
            activeRequests.remove(requestJob)
            requestJob.cancel()
        }
    }

    private fun toRuntimeMessage(message: AiMessage): Message =
        when (message.role) {
            AiRole.SYSTEM -> Message.system(message.content)
            AiRole.USER -> Message.user(message.content)
            AiRole.ASSISTANT -> Message.model(
                Contents.of(message.content),
                message.toolCalls.map { call ->
                    ToolCall(
                        name = call.name,
                        arguments = parseArguments(call.arguments)
                    )
                }
            )
            AiRole.TOOL -> Message.tool(
                Contents.of(
                    Content.ToolResponse(
                        message.name.orEmpty(),
                        parseToolResponse(message.content)
                    )
                )
            )
        }

    private fun parseArguments(raw: String): Map<String, Any?> {
        val parsed = Json.parseToJsonElement(raw)
        val objectValue = parsed as? JsonObject
            ?: throw AiProviderException.InvalidResponse()
        return objectValue.mapValues { (_, value) -> value.toNativeValue() }
    }

    private fun parseToolResponse(raw: String): Any =
        runCatching { JSONObject(raw) }.getOrElse { raw }

    private fun JsonElement.toNativeValue(): Any? =
        when (this) {
            is JsonObject -> entries.associate { (key, value) -> key to value.toNativeValue() }
            is JsonArray -> map { it.toNativeValue() }
            JsonNull -> null
            is JsonPrimitive -> booleanOrNull
                ?: longOrNull
                ?: doubleOrNull
                ?: content
        }

    private fun ensureEngine(path: String) {
        if (loadedPath == path && engine != null) return

        val candidate = try {
            Engine(
                EngineConfig(
                    modelPath = path,
                    backend = Backend.CPU(),
                    cacheDir = cacheDirectory.absolutePath
                )
            )
        } catch (error: LinkageError) {
            throw IOException("The LiteRT-LM native runtime is unavailable on this device.", error)
        }
        // Keep the previously loaded engine until the replacement initializes.
        // LiteRT-LM documents closing an engine whose initialize() failed as unsafe.
        try {
            candidate.initialize()
        } catch (error: Exception) {
            throw IOException("The selected local model could not be initialized.", error)
        } catch (error: LinkageError) {
            throw IOException("The LiteRT-LM native runtime is unavailable on this device.", error)
        }
        val previous = engine
        engine = candidate
        loadedPath = path
        runCatching { previous?.close() }
    }

    private fun releaseEngine() {
        val previous = engine
        engine = null
        loadedPath = null
        runCatching { previous?.close() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        activeRequests.forEach { it.cancel(CancellationException("Local inference runtime is closed.")) }
        executor.execute {
            try {
                releaseEngine()
            } finally {
                dispatcher.close()
            }
        }
    }

    private class RegistryOpenApiTool(
        private val definition: AiToolDefinition
    ) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String =
            buildJsonObject {
                put("name", definition.name)
                put("description", definition.description)
                put("parameters", definition.parameters)
            }.toString()

        /** Never called: automaticToolCalling=false is mandatory for app-side approval. */
        override fun execute(paramsJsonString: String): String =
            throw IllegalStateException("Plugin tools must be executed by the application.")
    }

    private companion object {
        const val MAX_OUTPUT_CHARACTERS = 16_000
        const val DEFAULT_SYSTEM_INSTRUCTION =
            "You are Miss Minutes, a helpful personal assistant. Be accurate, practical and concise."
    }
}
