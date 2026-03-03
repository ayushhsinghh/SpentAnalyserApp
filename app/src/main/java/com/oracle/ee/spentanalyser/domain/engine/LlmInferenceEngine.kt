package com.oracle.ee.spentanalyser.domain.engine

/**
 * Strategy pattern interface for LLM inference.
 * The domain layer depends on this abstraction — NOT on MediaPipe directly.
 * Implementations (e.g., MediaPipeLlmEngine) live in the Data layer.
 */
interface LlmInferenceEngine {
    /**
     * Initialize the engine with a model file and hardware preference.
     * @param modelPath Absolute path to the model file on disk.
     * @param useGpu Whether to prefer GPU acceleration over CPU.
     */
    suspend fun initialize(modelPath: String, useGpu: Boolean)

    /**
     * Run inference with the given prompt and return raw text output.
     */
    suspend fun infer(prompt: String): String

    /**
     * Release all resources held by the engine.
     */
    fun release()

    /**
     * Whether the engine is currently initialized and ready for inference.
     */
    fun isInitialized(): Boolean
}
