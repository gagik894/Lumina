package com.lumina.domain.model

/**
 * Sealed class representing structured voice commands.
 *
 * This replaces raw string parsing in the ViewModel with structured
 * command objects that encapsulate business logic.
 */
sealed class VoiceCommand {
    data class FindObject(val target: String) : VoiceCommand()
    data object Stop : VoiceCommand()
    data object StartNavigation : VoiceCommand()
    data object StopNavigation : VoiceCommand()
    data object CrossStreet : VoiceCommand()
    data object IdentifyCurrency : VoiceCommand()
    data object ReadReceipt : VoiceCommand()
    data object ReadText : VoiceCommand()
    data class AskQuestion(val question: String) : VoiceCommand()
    data object ToggleHaptic : VoiceCommand()
    data object TestHaptic : VoiceCommand()
    data object Help : VoiceCommand()
    data class Unknown(val originalCommand: String) : VoiceCommand()
}
