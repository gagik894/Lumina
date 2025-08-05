package com.lumina.domain.model

sealed class InitializationState {
    object NotInitialized : InitializationState()
    object Initializing : InitializationState()
    object Initialized : InitializationState()
    data class Error(val message: String) : InitializationState()
}

