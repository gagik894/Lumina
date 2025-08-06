# Lumina: On-Device AI Assistant for the Visually Impaired

Lumina is an Android application engineered to serve as a real-time AI assistant for visually
impaired users. By leveraging the power of Gemma 3n, Lumina transforms a smartphone into an
intelligent companion that provides environmental understanding without ever compromising user
privacy. All processing occurs 100% on-device.

---

### Core Features

Lumina provides a comprehensive suite of tools to help users navigate their world with confidence.

* **Scene Analysis:** Get a rich, detailed audio description of any new environment.
* **Real-World Finder:** Locate specific businesses or landmarks (e.g., "Find a pharmacy").
* **Object Finder:** Find common household objects in your personal space.
* **General Q&A:** Ask any question about what the camera sees (e.g., "What color is this?").
* **Advanced Text Reading:** A powerful OCR for reading documents and signs.
* **Specialized Readers:** Includes dedicated modes for parsing **Receipts** and identifying *
  *Currency**.

---

### Core Technical Innovation

A key challenge of mobile AI is handling imperfect camera data. Our primary innovation is an *
*Intelligent Frame Selection** pipeline that analyzes the video stream in real-time, discards blurry
frames, and selects only the sharpest image to send to Gemma 3n. This dramatically increases the
accuracy and reliability of all vision-based features.

**For a complete breakdown of our architecture and other technical solutions, please see our
detailed `TECHNICAL_WRITEUP.md`.**

---

### Getting Started: A Quick Tour

To get a feel for Lumina's primary capabilities, we recommend trying these core features first:

NOTE: TO start voice input press and hold the screen

**1. Describe a Scene:**

* **Action:** Point the phone at any scene (e.g., your desk).
* **Command:** Double tap anywhere on the screen.

**2. Find a Business:**

* **Action:** If possible, stand on a street with visible storefronts.
* **Voice Command:** Say "Find a pharmacy."

**3. Read Text:**

* **Action:** Point the phone at a well-lit document.
* **Voice Command:** Say "Read text."

**4. Idetify Currency:**

* **Action:** Point the phone at a well-lit currency note.
* **Voice Command:** Say "Identify currency/read money."

**5. Read receipts:**

* **Action:** Point the phone at a well-lit receipt.
* **Voice Command:** Say "Read receipt."

**6. Ask a Question:**

* **Action:** Point the phone at any object.
* **Voice Command:** Ask a question, like "What is this?"

**7. Navigational Assistance:**

* **Action:** Point the phone forward.
* **Voice Command:** Say "Start Navigation"

---

### Project Status & Roadmap

Lumina is currently a functional prototype developed for the Gemma 3n Impact Challenge. The features
listed in the "Quick Tour" are the most stable and representative of the final vision.

**Future Work:**

* **Proactive Safety Features:** Our immediate goal is to build out the **"Crossing Mode"**, a
  feature designed to analyze traffic flow and assist with street crossings. The conceptual
  framework for this is in place, leveraging our Motion Context Analysis pipeline, but it is not yet
  optimized for real-world use.
* **Enhanced Recognition:** Fine-tuning the model on specialized datasets to improve the accuracy of
  the Currency and Receipt readers.
* **Personalization:** Adding the ability for users to "teach" Lumina about their personal objects (
  e.g., "This is my wallet").

---

### How to Build and Run

This is a standard Android Gradle project.

1. **Build:** `./gradlew build`
2. **Install and Run:**
   `./gradlew installDebug && adb shell am start -n com.lumina.app/.MainActivity`