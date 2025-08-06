# Lumina: On-Device AI Assistant for the Visually Impaired

### **About the Developer**

My name is Gagik, and I am a 19-year-old student at Paris-Saclay University. I developed Lumina as a
solo project to explore my passion for using on-device AI to solve real-world accessibility
challenges.

---

Lumina is an Android application engineered to serve as a real-time AI assistant for visually
impaired users. By leveraging the power of Gemma 3n, Lumina transforms a smartphone into an
intelligent companion that provides environmental understanding without ever compromising user
privacy. All processing occurs 100% on-device.

### Core Features

* **Scene Analysis:** Get a rich, detailed audio description of any new environment.
* **Real-World Finder:** Locate specific businesses or landmarks (e.g., "Find a pharmacy").
* **Object Finder:** Find common household objects in your personal space.
* **General Q&A:** Ask any question about what the camera sees (e.g., "What color is this?").
* **Advanced Text Reading:** A powerful OCR for reading documents and signs.
* **Specialized Readers:** Includes dedicated modes for parsing **Receipts** and identifying *
  *Currency**.

### Core Technical Innovation

A key challenge of mobile AI is handling imperfect camera data. Our primary innovation is an *
*Intelligent Frame Selection** pipeline that analyzes the video stream in real-time, discards blurry
frames, and selects only the sharpest image to send to Gemma 3n. This dramatically increases the
accuracy and reliability of all vision-based features.

---

### Getting Started: A Quick Tour of Lumina

**NOTE:** To activate voice input, **press and hold** anywhere on the screen.

**1. Scene Understanding**

* **Describe a Scene:** Double-tap the screen to get a detailed description of your surroundings.
* **Find a Business:** Say "Find a pharmacy" while moving the phone to locate it.
* **Find an Object:** Say "Find TV remote" to locate an object indoors.

**2. Reading & Recognition**

* **Read General Text:** Say "Read text" while pointing at a document.
* **Identify Currency:** Say "Identify currency" or "Read money" while pointing at a bill.
* **Read a Receipt:** Say "Read receipt."

**3. Interactive Modes**

* **Ask a Question:** Say "What is this?" or "What color is the book?"
* **Navigational Assistance:** Say "Start navigation" for continuous environmental updates.

---

### Project Status & Roadmap

Lumina is currently a functional prototype. The features listed in the "Quick Tour" are the most
representative of the final vision.

**Future Work:**

* **Proactive Safety Features:** Our immediate goal is to build out the **"Crossing Mode"**, a
  feature designed to analyze traffic flow. The conceptual framework is in place but it is not yet
  optimized for real-world use.
* **Enhanced Recognition:** Fine-tuning the model on specialized datasets to improve the accuracy of
  the Currency and Receipt readers.
* **Personalization:** Adding the ability for users to "teach" Lumina about their personal objects.

---

### How to Build and Run

This is a standard Android Gradle project.

1. **Build:** `./gradlew build`
2. **Install and Run:**
   `./gradlew installDebug && adb shell am start -n com.lumina.app/.MainActivity`