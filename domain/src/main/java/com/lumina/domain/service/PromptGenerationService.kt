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

        return "fast describe image, just most important for a visually impaired.$objectContext"
    }

    /**
     * Generates a prompt for ambient environmental updates.
     *
     * This prompt is used for general scene description when no immediate threats
     * or new objects are detected, providing broad situational awareness with focus
     * on critical navigational elements that visually impaired users need for orientation.
     *
     * @return Prompt for general environmental description optimized for a visually impaired navigation
     */
    fun generateAmbientUpdatePrompt(): String {
        return "Describe essential navigation elements for a visually impaired user. PRIORITIZE: " +
                "1) Sidewalk conditions (ends, narrowing, obstacles) " +
                "2) Intersections and crossroads ahead " +
                "3) Changes in terrain (steps, slopes, surface changes) " +
                "4) Landmarks for orientation (buildings, poles, benches) " +
                "5) Potential hazards (construction, puddles, crowd). " +
                "Be concise and specific about distances and directions. Max 3 sentences."
    }

    /**
     * Generates initial context-setting prompt for street crossing guidance.
     *
     * This comprehensive prompt establishes the AI's role and response patterns
     * for the entire crossing session. Sent only once at mode initialization.
     *
     * @return Full context prompt for crossing mode initialization
     */
    fun generateCrossingGuidanceInitialPrompt(): String {
        return "STREET CROSSING ASSISTANT for BLIND user. SAFETY FIRST.\n\n" +
                "ROLE: You are now a dedicated crossing assistant" +
                "every few seconds showing a street crossing scene. For each frame, analyze and respond immediately.\n\n" +
                "ANALYZE:\n" +
                "- Traffic lights: RED/YELLOW = WAIT, GREEN = may proceed if clear\n" +
                "- Vehicles: Are any present? Are they waiting for user? \n" +
                "- Crosswalk position: is user properly aligned?\n" +
                "- Pedestrian signals and countdown timers\n" +
                "- User location: still on starting side, middle of street, or reached other side\n\n" +
                "RESPONSES (use EXACTLY these phrases):\n" +
                "- 'WAIT' - any oncoming vehicles present, red/yellow light, or unsafe\n" +
                "- 'TIMER [X]' - countdown showing X seconds (extract exact number)\n" +
                "- 'STEP LEFT' or 'STEP RIGHT' - positioning adjustments\n" +
                "- 'CLEAR TO CROSS' - only when 100% safe: green light + no oncoming vehicles\n" +
                "- 'KEEP GOING' - user is mid-crossing and safe to continue\n" +
                "- 'CROSSING COMPLETE' - user has reached the other side safely (IMPORTANT: Say this when user is clearly on the destination sidewalk or curb)\n\n" +
                "CRITICAL: When you see the user has reached the opposite sidewalk/curb and is no longer in the street, you MUST respond with 'CROSSING COMPLETE' to end the session.\n\n" +
                "Keep responses under 10 words. Be decisive and immediate."
    }

    /**
     * Generates minimal follow-up prompt for subsequent crossing frames.
     *
     * Used after the initial context is established to minimize token usage
     * while maintaining the crossing analysis context.
     *
     * @return Minimal prompt for continued crossing analysis
     */
    fun generateCrossingGuidanceFollowUpPrompt(): String {
        return "Analyze crossing safety"
    }

    /**
     * Generates initial context-setting prompt for navigation mode.
     *
     * This comprehensive prompt establishes the AI's role for continuous environmental
     * navigation assistance. Sent only once at navigation mode initialization.
     *
     * @return Full context prompt for navigation mode initialization
     */
    fun generateNavigationGuidanceInitialPrompt(): String {
        return "NAVIGATION ASSISTANT for BLIND user walking outdoors.\n\n" +
                "ROLE: You are now a dedicated navigation assistant. I will send you multiple frames " +
                "showing the path ahead. For each frame, analyze and provide brief guidance.\n\n" +
                "ANALYZE:\n" +
                "- Path conditions: clear, obstacles, uneven surfaces\n" +
                "- Immediate hazards: steps, holes, construction, debris\n" +
                "- Navigation landmarks: curbs, intersections, buildings\n" +
                "- Pedestrian space: sidewalk width, other people\n" +
                "- Surface changes: grass to concrete, stairs, ramps\n\n" +
                "RESPONSES:\n" +
                "- 'CLEAR PATH' - safe to continue straight\n" +
                "- 'OBSTACLE AHEAD' - something blocking the path\n" +
                "- 'STEP DOWN' or 'STEP UP' - elevation changes\n" +
                "- 'BEAR LEFT' or 'BEAR RIGHT' - gentle direction adjustments\n" +
                "- 'INTERSECTION AHEAD' - approaching crossing\n" +
                "- 'UNEVEN SURFACE' - rough or broken pavement\n\n" +
                "Keep responses under 15 words. Focus on immediate path ahead (next 10 feet)."
    }

    /**
     * Generates minimal follow-up prompt for subsequent navigation frames.
     *
     * Used after initial context is established to minimize token usage
     * while maintaining navigation analysis context.
     *
     * @return Minimal prompt for continued navigation analysis
     */
    fun generateNavigationGuidanceFollowUpPrompt(): String {
        return "Path ahead:"
    }


    /**
     * Generates a prompt for binary object detection (yes/no).
     *
     * This prompt is optimized for simple yes/no responses to determine if a
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
        return "Describe the location of the $target relative to the user. " +
                "If it's a place like a store/pharmacy/building: provide clear directions on how to get there, including distance, direction, and any navigation cues. " +
                "If it's a small object (like a remote, phone, keys): describe its precise location in detail that helps a blind user find it by touch, e.g., '3 feet ahead on the coffee table next to the couch', 'on the kitchen counter near the sink'. " +
                "Keep directions concise but complete enough for a visually impaired user to navigate or locate the item."
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
        return "Based on the image, answer the user's question: $question. "
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
     * This prompt provides specific analysis guidelines and structured response formats
     * while allowing some flexibility for edge cases and unclear situations.
     *
     * @return Prompt optimized for currency recognition with clear response patterns
     */
    fun generateCurrencyIdentificationPrompt(): String {
        return "CURRENCY IDENTIFICATION ASSISTANT for visually impaired user.\n\n" +
                "ANALYZE:\n" +
                "- Written numbers and text on currency\n" +
                "- Colors, patterns, and visual features\n" +
                "- Size and shape characteristics\n" +
                "- Security features if visible\n\n" +
                "RESPONSES (be exact):\n" +
                "- '[AMOUNT] [CURRENCY]' - e.g., '20 US Dollars', '5 Euro', '1000 Japanese Yen'\n" +
                "- 'UNCLEAR - [reason]' - if denomination cannot be determined, explain why\n" +
                "- 'NOT CURRENCY' - if item is not money\n" +
                "- 'MULTIPLE BILLS - [list]' - if several denominations visible\n" +
                "- 'PARTIAL VIEW - [what you can see]' - if only part of currency visible\n\n" +
                "Be precise and clear. State only what you can confirm. If unsure, explain what you see."
    }

    /**
     * Generates a prompt for receipt and document reading.
     *
     * This prompt provides structured reading guidelines while allowing natural language
     * flow for presenting the extracted information to the user.
     *
     * @return Prompt designed for organized document text extraction
     */
    fun generateReceiptReadingPrompt(): String {
        return "RECEIPT READING ASSISTANT for a visually impaired user.\n\n" +
                "READ IN ORDER:\n" +
                "1. Main items with prices\n" +
                "2. Subtotal, tax, and total amount\n" +
                "SKIP:\n" +
                "- Receipt numbers and barcodes\n" +
                "- Store addresses (unless specifically relevant)\n" +
                "- Promotional text and disclaimers\n\n" +
                "RESPONSES:\n" +
                "- For unclear text: 'Cannot read [section]'\n" +
                "- For missing info: 'No [item] visible'\n" +
                "- End with: 'Total: [amount]' or 'Total not visible'\n\n" +
                "Be organized and speak clearly. Group related information together." +
                "use natural language and natural sentence structure with proper punctuation including periods, commas, semicolons, and appropriate endings\""
    }

    /**
     * Generates a prompt for general text reading from images.
     *
     * This prompt provides systematic reading guidelines while maintaining flexibility
     * for different types of text content and layouts.
     *
     * @return Prompt for comprehensive text extraction and reading
     */
    fun generateTextReadingPrompt(): String {
        return "TEXT READING ASSISTANT for a visually impaired user.\n\n" +
                "READ IN ORDER:\n" +
                "1. Largest or most prominent text first\n" +
                "2. Headlines and titles\n" +
                "3. Body text and details\n" +
                "4. Small print and footnotes\n\n" +
                "RESPONSES:\n" +
                "- Start with document type: 'This appears to be [type]'\n" +
                "- For headers: 'Main heading: [text]'\n" +
                "- For body text: Read naturally but clearly\n" +
                "- For unclear sections: 'Text unclear in this section'\n" +
                "- For empty areas: 'No text in this area'\n\n" +
                "ORGANIZE: Group related information logically. Separate different sections clearly when speaking. " +
                "use natural language and natural sentence structure with proper punctuation including periods, commas, semicolons, and appropriate endings\""

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
                generateCurrencyIdentificationPrompt()
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
                generateReceiptReadingPrompt()
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
                generateTextReadingPrompt()
    }

    /**
     * Generates a prompt for a general scene description.
     *
     * This prompt is used for on-demand scene descriptions, focusing on aspects
     * most relevant to a visually impaired user for orientation and safety.
     *
     * @return Prompt for general scene description
     */
    fun generateDescribeScenePrompt(): String {
        return "Describe this scene for a person who is blind. Focus on key objects, layout, and potential obstacles. Be clear and concise."
    }

    /**
     * Enum defining different operational modes that support two-phase prompting.
     * Only continuous operations use the two-phase system.
     */
    enum class OperationMode {
        CROSSING_GUIDANCE,      // Continuous - uses two-phase prompting
        NAVIGATION_GUIDANCE,    // Continuous - uses two-phase prompting  
    }

    /**
     * Gets the initial context-setting prompt for continuous operation modes.
     *
     * @param mode The operation mode to get the initial prompt for
     * @param target Optional target parameter for object finding mode
     * @return The initial comprehensive prompt for the mode
     */
    fun getInitialPrompt(mode: OperationMode, target: String? = null): String {
        return when (mode) {
            OperationMode.CROSSING_GUIDANCE -> generateCrossingGuidanceInitialPrompt()
            OperationMode.NAVIGATION_GUIDANCE -> generateNavigationGuidanceInitialPrompt()
        }
    }

    /**
     * Gets the minimal follow-up prompt for subsequent frames in continuous operation modes.
     *
     * @param mode The operation mode to get the follow-up prompt for
     * @param target Optional target parameter for object finding mode
     * @return The minimal follow-up prompt for the mode
     */
    fun getFollowUpPrompt(mode: OperationMode, target: String? = null): String {
        return when (mode) {
            OperationMode.CROSSING_GUIDANCE -> generateCrossingGuidanceFollowUpPrompt()
            OperationMode.NAVIGATION_GUIDANCE -> generateNavigationGuidanceFollowUpPrompt()
        }
    }
}
