package com.lumina.domain.usecase

import com.lumina.domain.model.VoiceCommand
import javax.inject.Inject

/**
 * Use case for parsing and processing voice commands.
 *
 * Extracts voice command parsing logic from the ViewModel and provides
 * structured command objects for the presentation layer to handle.
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
