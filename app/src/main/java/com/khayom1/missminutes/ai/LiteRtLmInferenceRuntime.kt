package com.khayom1.missminutes.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * LiteRT-LM text generation on CPU. All Engine and Conversation operations are
 * confined to one worker thread, and model weights stay outside the APK.
 *
 * The Local AI provider deliberately rejects structured tool requests until
 * model tool calls use the same approval-gated dispatcher as cloud models.
 */
internal fun buildLiteRtLmPrompt(
    messages: List<AiMessage>,
    maxCharacters: Int = 18_000
): String {
    require(maxCharacters > 0)
    return messages
        .filter { it.role != AiRole.SYSTEM }
        .joinToString("\n\n") { message ->
            val role = when (message.role) {
                AiRole.USER -> "User"
                AiRole.ASSISTANT -> "Assistant"
                AiRole.TOOL -> "Tool result (${message.name.orEmpty()})"
                AiRole.SYSTEM -> "System"
            }
            "$role:\n${message.content}"
        }
        .takeLast(maxCharacters)
        .ifBlank { throw IllegalArgumentException("The local model prompt is empty.") }
}

class LiteRtLmInferenceRuntime(
    private val modelPath: () -> String?,
    private val cacheDirectory: File
) : LocalInferenceRuntime, AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "miss-minutes-litertlm").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val runtimeScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private val closed = AtomicBoolean(false)

    private var engine: Engine? = null
    private var loadedPath: String? = null

    override fun isAvailable(): Boolean {
        if (closed.get()) return false
        val path = modelPath() ?: return false
        val file = File(path)
        return file.isFile && file.length() > 0L
    }

    override suspend fun generate(
        messages: List<AiMessage>,
        maxOutputTokens: Int
    ): String = withContext(dispatcher) {
        check(!closed.get()) { "Local inference runtime is closed." }
        require(maxOutputTokens in 1..2_048)
        require(messages.isNotEmpty()) { "A local model request needs messages." }

        mutex.withLock {
            check(!closed.get()) { "Local inference runtime is closed." }
            val path = modelPath()?.takeIf { it.isNotBlank() }
                ?: throw IOException("Import a .litertlm model in Settings first.")
            val modelFile = File(path)
            if (!modelFile.isFile || modelFile.length() <= 0L) {
                throw IOException("The selected local model file is missing or empty.")
            }
            ensureEngine(path)

            val systemInstruction = messages
                .filter { it.role == AiRole.SYSTEM }
                .joinToString("\n") { it.content }
                .ifBlank { DEFAULT_SYSTEM_INSTRUCTION }
            val prompt = buildLiteRtLmPrompt(messages)

            val conversation = checkNotNull(engine).createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction),
                    maxOutputToken = maxOutputTokens
                )
            )
            val output = StringBuilder()
            try {
                conversation.sendMessageAsync(prompt).collect { chunk ->
                    val part = chunk.toString()
                    if (output.length + part.length > MAX_OUTPUT_CHARACTERS) {
                        throw IOException("Local model response exceeded the output limit.")
                    }
                    output.append(part)
                }
            } catch (error: CancellationException) {
                runCatching { conversation.cancelProcess() }
                throw error
            } finally {
                conversation.close()
            }

            output.toString().trim().ifBlank {
                throw IOException("The local model returned an empty response.")
            }
        }
    }

    private fun ensureEngine(path: String) {
        if (loadedPath == path && engine != null) return
        releaseEngine()

        val candidate = Engine(
            EngineConfig(
                modelPath = path,
                backend = Backend.CPU(),
                cacheDir = cacheDirectory.absolutePath
            )
        )
        // Store the engine only after successful initialization. LiteRT-LM
        // documents closing an engine whose initialize() failed as unsafe.
        candidate.initialize()
        engine = candidate
        loadedPath = path
    }

    private fun releaseEngine() {
        engine?.close()
        engine = null
        loadedPath = null
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runtimeScope.launch {
            mutex.withLock { releaseEngine() }
            dispatcher.close()
            runtimeScope.cancel()
        }
    }

    private companion object {
        const val MAX_OUTPUT_CHARACTERS = 16_000
        const val DEFAULT_SYSTEM_INSTRUCTION =
            "You are Miss Minutes, a helpful personal assistant. Be accurate, practical and concise."
    }
}
