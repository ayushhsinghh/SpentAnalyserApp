package com.oracle.ee.spentanalyser.data.engine

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Concrete implementation of [LlmInferenceEngine] using the Google AI Edge LiteRT LLM API.
 * This is used specifically for `.litertlm` models which the old tasks-genai API cannot initialize
 * due to missing embedded tokenizers.
 *
 * Aligned with litertlm 0.9.0-alpha06 API (as used in the gallery reference project).
 */
@OptIn(ExperimentalApi::class)
class LiteRtLlmEngine(private val context: Context) : LlmInferenceEngine {

    private var engine: Engine? = null

    @Volatile
    private var initialized = false

    override suspend fun initialize(modelPath: String, useGpu: Boolean): LlmInferenceEngine.Backend =
        withContext(Dispatchers.IO) {
            release()

            try {
                // In 0.9.0+, Backend is a sealed class — must call constructors Backend.GPU() / Backend.CPU()
                val backend: Backend = if (useGpu) Backend.GPU() else Backend.CPU()

                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    maxNumTokens = 2048,
                    // cacheDir is only needed for models stored in /data/local/tmp.
                    // For internal storage paths, pass null (matches gallery reference).
                    cacheDir = null,
                )

                val newEngine = Engine(config)
                newEngine.initialize()

                this@LiteRtLlmEngine.engine = newEngine
                initialized = true
                Timber.d("LiteRTLlmEngine initialized (backend=%s, model=%s)", backend, modelPath)

                if (useGpu) LlmInferenceEngine.Backend.GPU else LlmInferenceEngine.Backend.CPU
            } catch (e: Exception) {
                initialized = false
                Timber.e(e, "Failed to initialize LiteRTLlmEngine")
                release()
                throw e
            }
        }

    override suspend fun infer(prompt: String): String = withContext(Dispatchers.IO) {
        val currentEngine = engine
            ?: throw IllegalStateException("Engine not initialized. Call initialize() first.")

        // Create a stateless conversation for this single prompt
        val scopedConversation = currentEngine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    temperature = 0.5,
                    topK = 40,
                    topP = 0.9,
                )
            )
        )

        try {
            suspendCancellableCoroutine { continuation ->
                val builder = StringBuilder()

                val messageCallback = object : MessageCallback {
                    override fun onMessage(message: Message) {
                        // In 0.9.0+, message.toString() provides the clean text output,
                        // consistent with how the gallery reference project uses it.
                        builder.append(message.toString())
                    }

                    override fun onDone() {
                        if (continuation.isActive) {
                            continuation.resume(builder.toString())
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        Timber.e(throwable, "Error during LiteRT inference")
                        if (continuation.isActive) {
                            continuation.resumeWithException(throwable)
                        }
                    }
                }

                try {
                    // In 0.9.0+, sendMessageAsync takes a Contents object, not a Message.
                    // Contents.of(list) wraps one or more Content items (text, image, audio).
                    scopedConversation.sendMessageAsync(
                        Contents.of(listOf(Content.Text(prompt))),
                        messageCallback,
                    )
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        } finally {
            try {
                scopedConversation.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing stateless scoped Conversation")
            }
        }
    }

    override fun release() {
        try {
            engine?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing Engine")
        }

        engine = null
        initialized = false
    }

    override fun isInitialized(): Boolean = initialized
}
