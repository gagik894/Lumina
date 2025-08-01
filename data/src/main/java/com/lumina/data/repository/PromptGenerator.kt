package com.lumina.data.repository

import com.lumina.domain.service.PromptGenerationService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data layer adapter for PromptGenerationService.
 *
 * This adapter wraps the pure domain PromptGenerationService and provides
 * the same interface for the data layer.
 */
@Singleton
class PromptGenerator @Inject constructor(
    private val promptGenerationService: PromptGenerationService
) {

    /**
     * Generates a prompt for critical threat detection and immediate alert generation.
     *
     * @return Concise prompt for critical threat identification
     */
    fun generateCriticalThreatPrompt(): String {
        return promptGenerationService.generateCriticalThreatPrompt()
    }

    /**
     * Generates a prompt for informational alerts about new objects in the environment.
     *
     * @param newObjects List of newly detected important objects
     * @return Prompt for describing new environmental elements
     */
    fun generateInformationalAlertPrompt(newObjects: List<String>): String {
        return promptGenerationService.generateInformationalAlertPrompt(newObjects)
    }

    /**
     * Generates a prompt for ambient environmental updates.
     *
     * @return Prompt for general environmental description
     */
    fun generateAmbientUpdatePrompt(): String {
        return promptGenerationService.generateAmbientUpdatePrompt()
    }

    /**
     * Generates a prompt for street crossing guidance.
     *
     * @return Prompt for crossing guidance with termination instructions
     */
    fun generateCrossingGuidancePrompt(): String {
        return promptGenerationService.generateCrossingGuidancePrompt()
    }

    /**
     * Generates a prompt for object detection queries.
     *
     * @param target The object to search for
     * @return Prompt for binary object detection
     */
    fun generateObjectDetectionPrompt(target: String): String {
        return promptGenerationService.generateObjectDetectionPrompt(target)
    }

    /**
     * Generates a prompt for object location description.
     *
     * @param target The object whose location should be described
     * @return Prompt for spatial object description
     */
    fun generateObjectLocationPrompt(target: String): String {
        return promptGenerationService.generateObjectLocationPrompt(target)
    }

    /**
     * Generates a prompt for scene description with custom user input.
     *
     * @param userPrompt The user's custom prompt or question
     * @return Contextualized prompt for scene analysis
     */
    fun generateSceneDescriptionPrompt(userPrompt: String): String {
        return promptGenerationService.generateSceneDescriptionPrompt(userPrompt)
    }

    /**
     * Generates a prompt for answering user questions about the current scene.
     *
     * @param question The user's question
     * @return Contextualized prompt for question answering
     */
    fun generateQuestionAnsweringPrompt(question: String): String {
        return promptGenerationService.generateQuestionAnsweringPrompt(question)
    }

    /**
     * Generates prompts for specific navigation contexts with additional parameters.
     *
     * @param context The navigation context (e.g., "indoor", "urban", "rural")
     * @param additionalInfo Additional context information
     * @return Context-specific navigation prompt
     */
    fun generateContextualNavigationPrompt(
        context: String,
        additionalInfo: String = ""
    ): String {
        return promptGenerationService.generateContextualNavigationPrompt(context, additionalInfo)
    }

    /**
     * Generates a prompt for currency identification.
     *
     * @return Prompt optimized for currency recognition and value identification
     */
    fun generateCurrencyIdentificationPrompt(): String {
        return promptGenerationService.generateCurrencyIdentificationPrompt()
    }

    /**
     * Generates a prompt for receipt and document reading.
     *
     * @return Prompt designed for comprehensive document text extraction
     */
    fun generateReceiptReadingPrompt(): String {
        return promptGenerationService.generateReceiptReadingPrompt()
    }

    /**
     * Generates a prompt for general text reading from images.
     *
     * @return Prompt for general text extraction and reading
     */
    fun generateTextReadingPrompt(): String {
        return promptGenerationService.generateTextReadingPrompt()
    }

    /**
     * Generates a prompt for multi-frame currency identification analysis.
     * The prompt indicates that multiple frames show the same currency for better accuracy.
     *
     * @return Prompt designed for multi-frame currency analysis
     */
    fun generateMultiFrameCurrencyPrompt(): String {
        return promptGenerationService.generateMultiFrameCurrencyPrompt()
    }

    /**
     * Generates a prompt for multi-frame receipt reading analysis.
     * The prompt indicates that multiple frames show the same receipt for better accuracy.
     *
     * @return Prompt designed for multi-frame receipt analysis
     */
    fun generateMultiFrameReceiptPrompt(): String {
        return promptGenerationService.generateMultiFrameReceiptPrompt()
    }

    /**
     * Generates a prompt for multi-frame text reading analysis.
     * The prompt indicates that multiple frames show the same document for better accuracy.
     *
     * @return Prompt designed for multi-frame text analysis
     */
    fun generateMultiFrameTextPrompt(): String {
        return promptGenerationService.generateMultiFrameTextPrompt()
    }
}
