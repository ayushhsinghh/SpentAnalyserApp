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
 * Concrete implementation of [LlmInferenceEngine] using the new Google AI Edge LiteRT LLM API.
 * This is used specifically for `.litertlm` models which the old tasks-genai API cannot initialize
 * due to missing embedded tokenizers.
 */
@OptIn(ExperimentalApi::class)
class LiteRtLlmEngine(private val context: Context) : LlmInferenceEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    
    @Volatile
    private var initialized = false

    override suspend fun initialize(modelPath: String, useGpu: Boolean) = withContext(Dispatchers.IO) {
        release()

        try {
            val backend = if (useGpu) Backend.GPU else Backend.CPU

            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                maxNumTokens = 2048,
                cacheDir = context.cacheDir.absolutePath
            )

            val newEngine = Engine(config)
            newEngine.initialize()
            
            val newConversation = newEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        temperature = 0.5,
                        topK = 40,
                        topP = 0.9
                    )
                )
            )

            this@LiteRtLlmEngine.engine = newEngine
            this@LiteRtLlmEngine.conversation = newConversation
            initialized = true
            Timber.d("LiteRTLlmEngine initialized (backend=%s, model=%s)", backend, modelPath)
        } catch (e: Exception) {
            initialized = false
            Timber.e(e, "Failed to initialize LiteRTLlmEngine")
            release()
            throw e
        }
    }

    override suspend fun infer(prompt: String): String = withContext(Dispatchers.IO) {
        val currentConversation = conversation
            ?: throw IllegalStateException("Conversation not initialized. Call initialize() first.")

        suspendCancellableCoroutine { continuation ->
            val builder = StringBuilder()
            
            val messageCallback = object : MessageCallback {
                override fun onMessage(message: Message) {
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
                // We use structured content. For simple text, it's just [Content.Text]
                currentConversation.sendMessageAsync(
                    Contents.of(listOf(Content.Text(prompt))),
                    messageCallback
                )
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    override fun release() {
        try {
            conversation?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing Conversation")
        }
        
        try {
            engine?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing Engine")
        }
        
        conversation = null
        engine = null
        initialized = false
    }

    override fun isInitialized(): Boolean = initialized
}
