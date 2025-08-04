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
        val lower = command.trim().lowercase()

        return when {
            lower.startsWith("find ") -> {
                val target = lower.removePrefix("find ").trim()
                if (target.isNotEmpty()) {
                    VoiceCommand.FindObject(target)
                } else {
                    VoiceCommand.Unknown(command)
                }
            }

            lower == "cancel" || lower == "stop" -> {
                VoiceCommand.Stop
            }

            lower == "start navigation" || lower == "start camera" -> {
                VoiceCommand.StartNavigation
            }

            lower == "stop navigation" || lower == "stop camera" -> {
                VoiceCommand.StopNavigation
            }

            lower == "cross street" -> {
                VoiceCommand.CrossStreet
            }

            lower == "read money" || lower == "identify currency" -> {
                VoiceCommand.IdentifyCurrency
            }

            lower == "read receipt" -> {
                VoiceCommand.ReadReceipt
            }

            lower == "read text" -> {
                VoiceCommand.ReadText
            }

            lower == "toggle haptic" || lower == "haptic on" || lower == "haptic off" -> {
                VoiceCommand.ToggleHaptic
            }

            lower == "test haptic" || lower == "vibrate" -> {
                VoiceCommand.TestHaptic
            }

            lower == "help" || lower == "help me" -> {
                VoiceCommand.Help
            }

            lower.startsWith("question") -> {
                val question = lower.removePrefix("question").trim()
                if (question.isNotEmpty()) {
                    VoiceCommand.AskQuestion(question)
                } else {
                    VoiceCommand.AskQuestion("")
                }
            }

            else -> {
                // Treat everything else as a question
                VoiceCommand.AskQuestion(lower.trim())
            }
        }
    }
}
