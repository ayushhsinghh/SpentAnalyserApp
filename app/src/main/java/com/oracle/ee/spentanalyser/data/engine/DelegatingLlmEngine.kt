package com.oracle.ee.spentanalyser.data.engine

import android.content.Context
import com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine
import timber.log.Timber

/**
 * A wrapper engine that dynamically instantiates either [MediaPipeLlmEngine] for legacy `.task` models
 * or [LiteRtLlmEngine] for modern `.litertlm` models, hiding the complexity from the domain layer.
 */
class DelegatingLlmEngine(private val context: Context) : LlmInferenceEngine {

    private var activeEngine: LlmInferenceEngine? = null

    override suspend fun initialize(modelPath: String, useGpu: Boolean) {
        release()

        val engine = if (modelPath.endsWith(".litertlm", ignoreCase = true)) {
            Timber.d("Routing to LiteRTLlmEngine for: %s", modelPath)
            LiteRtLlmEngine(context)
        } else {
            Timber.d("Routing to MediaPipeLlmEngine for: %s", modelPath)
            MediaPipeLlmEngine(context)
        }

        activeEngine = engine
        engine.initialize(modelPath, useGpu)
    }

    override suspend fun infer(prompt: String): String {
        return activeEngine?.infer(prompt)
            ?: throw IllegalStateException("No active engine. Call initialize() first.")
    }

    override fun release() {
        activeEngine?.release()
        activeEngine = null
    }

    override fun isInitialized(): Boolean {
        return activeEngine?.isInitialized() == true
    }
}
