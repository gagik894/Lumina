# Lumina - AI Assistant for the Visually Impaired

Lumina is an Android application designed to assist visually impaired users by providing real-time
information about their surroundings through a combination of AI-powered computer vision and voice
commands.

## Features

The application is structured around a set of core features, each represented by a specific use case
in the domain layer.

### Core Functionality

* **Navigation Mode**: Provides continuous audio feedback about the environment to help users
  navigate safely. This includes ambient updates about the surroundings.
    * `StartNavigationUseCase`: Initiates the navigation mode.
    * `StopNavigationUseCase`: Terminates the navigation mode.

* **Scene Description**: Offers a detailed description of the current scene from the camera's point
  of view.
    * `DescribeSceneUseCase`: Generates a textual description of the scene.

* **Object Finding**: Helps users locate specific objects in their environment.
    * `FindObjectUseCase`: Searches for a user-specified object.

* **Question Answering**: Allows users to ask questions about what the camera sees.
    * `AskQuestionUseCase`: Processes user questions about the scene.

* **Text and Document Reading**:
    * `ReadTextUseCase`: Reads any visible text from the camera feed, including from multiple frames
      for better accuracy.
    * `ReadReceiptUseCase`: Specifically tailored to read and interpret receipts.
    * `IdentifyCurrencyUseCase`: Identifies currency bills and coins.

* **Crossing Mode**: A specialized mode to assist with safely crossing streets.
    * `StartCrossingModeUseCase`: Activates the street crossing assistance feature.

### Voice Commands

The application is primarily controlled through voice. The `ProcessVoiceCommandUseCase` interprets
natural language commands to activate the various features. Supported commands include:

* **Finding Objects**: "Find [object]", "Locate the book"
* **Navigation**: "Start navigation", "Stop navigation"
* **Scene Interaction**: "Describe the scene", "Question: what is in front of me?"
* **Text Reading**: "Read text", "Identify currency", "Read the receipt"
* **Control**: "Stop", "Cancel"
* **Help**: "Help"

### Camera and Frame Management

The application intelligently manages the camera and the frames it captures to optimize for
different tasks.

* `ManageCameraOperationsUseCase`: Handles activating and deactivating the camera, and switching
  between modes like `NAVIGATION` and `TEXT_READING`.
* `ProcessFrameUseCase`: Processes individual frames from the camera.
* `ManageFrameThrottlingUseCase`: Manages the rate at which frames are processed to balance
  performance and battery usage.

### System and State Management

* `GetInitializationStateUseCase`: Checks if the system components, like the AI model and
  Text-to-Speech engine, are ready.
* `NavigationOrchestrator`: Manages the flow of navigation cues and coordinates the various
  subsystems.
* `StopAllOperationsUseCase`: Provides a global way to stop all ongoing operations.
* `HandleTtsUseCase`: Manages Text-to-Speech functionality.

## Architecture

The application follows a clean architecture pattern, separating concerns into `data`, `domain`, and
`app` (presentation) layers. The `domain` layer contains the core business logic and use cases,
making the application modular and testable.

### Privacy-First and Offline-Ready

A core principle of Lumina is user privacy. All AI processing is done **100% on-device** thanks to
the efficiency of the Gemma 3n model. This means:

- No user data (images, audio, or voice commands) ever leaves the device.
- The application is fully functional without an internet connection, making it reliable in any
  situation.

## How to Build

This is a standard Android Gradle project. You can build it using Android Studio or from the command
line:

```bash
./gradlew build
```

## How to Run

To run the application, you can use Android Studio to deploy it to an Android device or emulator.
Alternatively, you can install the generated APK using `adb`:

```bash
./gradlew installDebug
adb shell am start -n com.lumina.app/.MainActivity
```
