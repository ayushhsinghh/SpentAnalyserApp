package com.oracle.ee.spentanalyser.data.engine

import android.content.Context
import com.oracle.ee.spentanalyser.domain.engine.LlmInferenceEngine
import timber.log.Timber

/**
 * A wrapper engine that dynamically instantiates either [LiteRtLlmEngine] for modern `.litertlm`
 * models or [MediaPipeLlmEngine] for legacy `.task` models, hiding the complexity from the domain layer.
 */
class DelegatingLlmEngine(private val context: Context) : LlmInferenceEngine {

    private var activeEngine: LlmInferenceEngine? = null

    override suspend fun initialize(modelPath: String, useGpu: Boolean): LlmInferenceEngine.Backend {
        release()

        val engine = if (modelPath.endsWith(".litertlm")) {
            Timber.d("Routing to LiteRtLlmEngine for: %s", modelPath)
            LiteRtLlmEngine(context)
        } else {
            Timber.d("Routing to MediaPipeLlmEngine for: %s", modelPath)
            MediaPipeLlmEngine(context)
        }

        activeEngine = engine
        return engine.initialize(modelPath, useGpu)
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
