package com.lumina.domain.model

/**
 * Enum representing different types of navigation cues for UI styling.
 * Moved from ViewModel to domain model for better separation of concerns.
 */
enum class NavigationCueType {
    NONE,
    CRITICAL,
    INFORMATIONAL,
    AMBIENT
}
