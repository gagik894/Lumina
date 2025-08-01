package com.lumina.domain.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain service for generating contextually appropriate AI prompts for navigation scenarios.
 *
 * This service centralizes all prompt generation logic, ensuring consistent and
 * optimized prompts for various use cases. Each prompt is carefully crafted to:
 * - Minimize token usage while maximizing information quality
 * - Provide clear, actionable guidance for visually impaired users
 * - Maintain consistent tone and terminology across the application
 */
@Singleton
class PromptGenerationService @Inject constructor() {

    /**
     * Generates a prompt for critical threat detection and immediate alert generation.
     *
     * This prompt is designed to produce ultra-brief, actionable responses that can
     * be quickly processed and announced to the user in emergency situations.
     *
     * @return Concise prompt for critical threat identification
     */
    fun generateCriticalThreatPrompt(): String {
        return "IMMEDIATE OBSTACLE. NAME IT IN 3 WORDS."
    }

    /**
     * Generates a prompt for informational alerts about new objects in the environment.
     *
     * This prompt balances brevity with informativeness, providing useful context
     * about newly detected objects without overwhelming the user.
     *
     * @param newObjects List of newly detected important objects
     * @return Prompt for describing new environmental elements
     */
    fun generateInformationalAlertPrompt(newObjects: List<String>): String {
        val objectContext = if (newObjects.isNotEmpty()) {
            " A new ${newObjects.first()} has appeared."
        } else {
            ""
        }

        return "fast describe image, just most important for a blind.$objectContext"
    }

    /**
     * Generates a prompt for ambient environmental updates.
     *
     * This prompt is used for general scene description when no immediate threats
     * or new objects are detected, providing broad situational awareness.
     *
     * @return Prompt for general environmental description
     */
    fun generateAmbientUpdatePrompt(): String {
        return "Briefly describe the general surroundings for a blind user."
    }

    /**
     * Generates a prompt for street crossing guidance.
     *
     * This prompt is specifically designed for the crossing mode, emphasizing
     * safety, brevity, and clear completion signals.
     *
     * @return Prompt for crossing guidance with termination instructions
     */
    fun generateCrossingGuidancePrompt(): String {
        return "You are in CROSSING MODE. Guide the user with WAIT, CROSS, or ADJUST LEFT/RIGHT in <=3 words. " +
                "IMPORTANT: Once you determine the user has safely crossed the street, you MUST respond with the exact phrase 'CROSSING COMPLETE' and nothing else."
    }

    /**
     * Generates a prompt for object detection queries.
     *
     * This prompt is optimized for binary yes/no responses to determine if a
     * specific object is present in the current view.
     *
     * @param target The object to search for
     * @return Prompt for binary object detection
     */
    fun generateObjectDetectionPrompt(target: String): String {
        return "Answer yes or no only. Do you see a $target in the image?"
    }

    /**
     * Generates a prompt for object location description.
     *
     * Once an object is confirmed to be present, this prompt guides the AI to
     * provide spatial information about the object's location relative to the user.
     *
     * @param target The object whose location should be described
     * @return Prompt for spatial object description
     */
    fun generateObjectLocationPrompt(target: String): String {
        return "Describe the location of the $target relative to the user."
    }

    /**
     * Generates a prompt for scene description with custom user input.
     *
     * This prompt wraps user-provided prompts with appropriate context for
     * image-based question answering.
     *
     * @param userPrompt The user's custom prompt or question
     * @return Contextualized prompt for scene analysis
     */
    fun generateSceneDescriptionPrompt(userPrompt: String): String {
        return userPrompt
    }

    /**
     * Generates a prompt for answering user questions about the current scene.
     *
     * This prompt contextualizes user questions within the current visual scene,
     * ensuring the AI uses the image to provide relevant answers.
     *
     * @param question The user's question
     * @return Contextualized prompt for question answering
     */
    fun generateQuestionAnsweringPrompt(question: String): String {
        return "Based on the image, answer the user's question: $question"
    }

    /**
     * Generates prompts for specific navigation contexts with additional parameters.
     *
     * This is a flexible method that can be extended for future prompt types
     * that require more complex parameter handling.
     *
     * @param context The navigation context (e.g., "indoor", "urban", "rural")
     * @param additionalInfo Additional context information
     * @return Context-specific navigation prompt
     */
    fun generateContextualNavigationPrompt(
        context: String,
        additionalInfo: String = ""
    ): String {
        return when (context.lowercase()) {
            "indoor" -> "Describe indoor navigation hazards and landmarks for a blind user.$additionalInfo"
            "urban" -> "Describe urban navigation elements: sidewalks, crossings, obstacles for a blind user.$additionalInfo"
            "rural" -> "Describe rural path conditions and landmarks for a blind user.$additionalInfo"
            else -> "Describe the navigation environment for a blind user.$additionalInfo"
        }
    }

    /**
     * Generates a prompt for currency identification.
     *
     * This prompt is specifically designed for identifying money bills, coins,
     * and providing denomination information to visually impaired users.
     *
     * @return Prompt optimized for currency recognition and value identification
     */
    fun generateCurrencyIdentificationPrompt(): String {
        return "Identify the currency in this image. State the exact denomination and currency, for example, '10 US Dollars' or '5 Euro'. " +
                "Be precise and clear"
    }

    /**
     * Generates a prompt for receipt and document reading.
     *
     * This prompt is optimized for reading receipts, bills, and other text documents,
     * extracting the most relevant information for the user.
     *
     * @return Prompt designed for comprehensive document text extraction
     */
    fun generateReceiptReadingPrompt(): String {
        return "Read only the important information from this receipt or document aloud. Start with the business name and date if visible. " +
                "List the main items and their prices, the total amount, and payment method if shown. Do not read the receipt number, bar code, or other irrelevant details. " +
                "Be organized and speak clearly."
    }

    /**
     * Generates a prompt for general text reading from images.
     *
     * This prompt handles any text content in images, from signs to labels,
     * providing clear and structured reading for visually impaired users.
     *
     * @return Prompt for general text extraction and reading
     */
    fun generateTextReadingPrompt(): String {
        return "Read all visible text in this image clearly and systematically. " +
                "Start with the largest or most prominent text, then continue with smaller details. " +
                "Organize the information logically"
    }

    /**
     * Generates a prompt for multi-frame currency identification.
     *
     * This prompt informs the AI that it will receive multiple frames of the same currency
     * for improved accuracy in identification and denomination detection.
     *
     * @return Prompt optimized for multi-frame currency analysis
     */
    fun generateMultiFrameCurrencyPrompt(): String {
        return "You will receive multiple frames showing the same currency " +
                "Analyze all frames to identify the currency with maximum accuracy. " +
                "State the exact denomination and currency, for example, '10 US Dollars' or '5 Euro'. " +
                "Use information from all frames to provide the most accurate identification. You can use color and other identifications if written denomination isn't visible" +
                "Be precise and clear. DON'T GUESS"
    }

    /**
     * Generates a prompt for multi-frame receipt reading.
     *
     * This prompt informs the AI that it will receive multiple frames of the same receipt
     * for improved text recognition and information extraction.
     *
     * @return Prompt optimized for multi-frame receipt analysis
     */
    fun generateMultiFrameReceiptPrompt(): String {
        return "You will receive multiple frames showing the same receipt or document from slightly different angles. " +
                "Analyze all frames to extract the most complete and accurate information. " +
                "Start with the business name and date if visible. List the main items and their prices, " +
                "the total amount, and payment method if shown. Do not read receipt numbers, bar codes, or irrelevant details. " +
                "Use information from all frames to provide the most complete reading. Be organized and speak clearly."
    }

    /**
     * Generates a prompt for multi-frame text reading.
     *
     * This prompt informs the AI that it will receive multiple frames of the same document
     * for improved text recognition and reading accuracy.
     *
     * @return Prompt optimized for multi-frame text analysis
     */
    fun generateMultiFrameTextPrompt(): String {
        return "You will receive multiple frames showing the same text or document from slightly different angles or lighting. " +
                "Analyze all frames to read the text with maximum accuracy and completeness. " +
                "Start with the largest or most prominent text, then continue with smaller details. " +
                "Use information from all frames to provide the most complete and accurate reading. Organize the information logically."
    }
}
