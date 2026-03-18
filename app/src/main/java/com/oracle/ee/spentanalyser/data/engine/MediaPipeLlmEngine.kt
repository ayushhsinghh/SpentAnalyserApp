package com.oracle.ee.spentanalyser.data.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Concrete implementation of [LlmInferenceEngine] using Google MediaPipe.
 * All MediaPipe-specific code is isolated here.
 */
class MediaPipeLlmEngine(private val context: Context) : LlmInferenceEngine {

    private var llmInference: LlmInference? = null
    @Volatile
    private var initialized = false

    override suspend fun initialize(modelPath: String, useGpu: Boolean): LlmInferenceEngine.Backend = withContext(Dispatchers.IO) {
        // Release any existing instance first
        release()

        try {
            val backend = if (useGpu) LlmInference.Backend.GPU else LlmInference.Backend.CPU

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(2048)
                .setPreferredBackend(backend)
                .build()

            if (useGpu) {
                try {
                    // Try GPU initialization with a timeout to catch freezes
                    llmInference = withTimeout(20_000L) {
                        LlmInference.createFromOptions(context, options)
                    }
                    initialized = true
                    Timber.d("MediaPipeLlmEngine initialized (backend=GPU, model=%s)", modelPath)
                    LlmInferenceEngine.Backend.GPU
                } catch (e: Exception) {
                    Timber.w(e, "GPU initialization failed or timed out. Falling back to CPU backend.")
                    
                    val cpuOptions = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setMaxTokens(2048)
                        .setPreferredBackend(LlmInference.Backend.CPU)
                        .build()
                        
                    llmInference = LlmInference.createFromOptions(context, cpuOptions)
                    initialized = true
                    Timber.d("MediaPipeLlmEngine initialized (backend=CPU FALLBACK, model=%s)", modelPath)
                    LlmInferenceEngine.Backend.CPU
                }
            } else {
                llmInference = LlmInference.createFromOptions(context, options)
                initialized = true
                Timber.d("MediaPipeLlmEngine initialized (backend=CPU, model=%s)", modelPath)
                LlmInferenceEngine.Backend.CPU
            }
        } catch (e: Exception) {
            initialized = false
            Timber.e(e, "Failed to initialize MediaPipeLlmEngine")
            throw e
        }
    }

    override suspend fun infer(prompt: String): String = withContext(Dispatchers.IO) {
        val engine = llmInference
            ?: throw IllegalStateException("Engine not initialized. Call initialize() first.")

        val response = engine.generateResponse(prompt)
            ?: throw IllegalStateException("Inference returned null.")

        response
    }

    override fun release() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing LlmInference")
        }
        llmInference = null
        initialized = false
    }

    override fun isInitialized(): Boolean = initialized
}
