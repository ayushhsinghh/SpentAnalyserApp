package com.oracle.ee.spentanalyser.domain.engine

/**
 * Strategy pattern interface for LLM inference.
 * The domain layer depends on this abstraction — NOT on MediaPipe directly.
 * Implementations (e.g., MediaPipeLlmEngine) live in the Data layer.
 */
interface LlmInferenceEngine {
    /**
     * Represents the actual backend initialized by the engine.
     */
    enum class Backend { GPU, CPU }

    /**
     * Initialize the engine with a model file and hardware preference.
     * @param modelPath Absolute path to the model file on disk.
     * @param useGpu Whether to prefer GPU acceleration over CPU.
     * @return The actual hardware Backend used (may fall back to CPU).
     */
    suspend fun initialize(modelPath: String, useGpu: Boolean): Backend

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
