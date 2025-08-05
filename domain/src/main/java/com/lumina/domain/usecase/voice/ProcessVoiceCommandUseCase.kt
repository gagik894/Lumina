package com.lumina.domain.usecase.voice

import com.lumina.domain.model.VoiceCommand
import javax.inject.Inject

/**
 * Use case responsible for processing raw voice command strings and converting them into structured
 * [VoiceCommand] objects.
 *
 * This class encapsulates the logic for interpreting various voice commands, such as finding objects,
 * starting or stopping navigation, reading text, and asking questions.
 *
 * It provides a single method [processCommand] that takes a raw command string and returns a
 * corresponding [VoiceCommand].
 */
class ProcessVoiceCommandUseCase @Inject constructor() {

    /**
     * Processes a raw voice command string and returns a structured command.
     */
    fun processCommand(command: String): VoiceCommand {
        // Normalize input: lowercase and strip polite prefixes
        val lowerOrig = command.trim().lowercase()
        val lower = lowerOrig
            .replace(Regex("\\b(please|can you|could you|would you|kindly)\\b"), "")
            .trim()

        // Helper to remove articles
        fun stripArticles(text: String) = text.replace(Regex("\\b(a|an|the)\\b"), "").trim()

        // FIND OBJECT commands
        val findKeys = listOf("find ", "locate ", "search for ", "look for ")
        findKeys.firstOrNull { lower.startsWith(it) }?.let { key ->
            val target = stripArticles(lower.removePrefix(key))
            return if (target.isNotEmpty()) VoiceCommand.FindObject(target)
            else VoiceCommand.Unknown(command)
        }

        // STOP command
        if (listOf("cancel", "stop").any { lower.startsWith(it) }) {
            return VoiceCommand.Stop
        }

        // NAVIGATION commands
        if (listOf("start navigation", "start camera", "navigate").any { lower.contains(it) }) {
            return VoiceCommand.StartNavigation
        }
        if (listOf("stop navigation", "stop camera").any { lower.contains(it) }) {
            return VoiceCommand.StopNavigation
        }

        // CROSS-STREET
        if (lower.contains("cross street") || lower.contains("crossing street")) {
            return VoiceCommand.CrossStreet
        }

        // OCR/text commands
        if (listOf("identify currency", "read money").any { lower.contains(it) }) {
            return VoiceCommand.IdentifyCurrency
        }
        if (listOf("read receipt", "read receipts").any { lower.contains(it) }) {
            return VoiceCommand.ReadReceipt
        }
        if (listOf(
                "read text",
                "read book",
                "read document",
                "read label"
            ).any { lower.contains(it) }
        ) {
            return VoiceCommand.ReadText
        }

        // HAPTIC
        if (listOf("toggle haptic", "haptic on", "haptic off").any { lower.contains(it) }) {
            return VoiceCommand.ToggleHaptic
        }
        if (listOf("test haptic", "vibrate").any { lower.contains(it) }) {
            return VoiceCommand.TestHaptic
        }

        // HELP
        if (lower.contains("help")) {
            return VoiceCommand.Help
        }

        // QUESTION
        if (lower.startsWith("question")) {
            val q = lower.removePrefix("question").trim()
            return VoiceCommand.AskQuestion(q)
        }

        // Default: treat as question
        return VoiceCommand.AskQuestion(lower.trim())
    }
}
