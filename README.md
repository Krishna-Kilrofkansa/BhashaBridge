# 🇮🇳 BhashaBridge — Dark Elegant Neural Translator

**BhashaBridge** is a beautiful, highly personalized, offline-first translation tool designed to narrow the communicative gap between **Northern Hindi** and the **Southern Dravidian language families** (Tamil, Telugu, Kannada, Malayalam). It encapsulates modern Material 3 design paradigms, combining offline translation simulation, on-device voice speech synthesis, and script detection.

---

## 🎨 Design Concept: Elegant Dark Slate
The user interface has been customized around the **Elegant Dark** design guideline:
- **Calm Charcoal Canvas**: Styled with deep slate primary tones (`#1A1C1E`) and rich visual contrast points.
- **Deep Blue Accent Highlights**: The translation outputs are encased in striking deep-sea cobalt panels (`#004A77`) paired with a high-contrast ice-blue text field (`#C2E8FF`).
- **Pristine Fluidity**: Integrated a standard vertical scroll fallback, avoiding cramped buttons or overflowing indicators on ultra-compact phone devices.

---

## 🚀 Key Functional Modules

### 1. Offline & Local Bridge Architecture
- **Interactive Translation Engine**: Supports multi-mode operations including Direct Translation, Script Auto-detection, and Phonetic Romanization (helpful for reading Dravidian/Devanagari text in Latin character formats).
- **Offline Intelligence Concept**: Mimics the neural inference speeds of Gemma's local language processing.

### 2. Immersive Speech & Audio Utilities
- **🎙️ Voice Recognition Input**: Provides immediate speech-to-text recognition localized dynamically to the selected source input dialect (Bcp47 mapping: `hi-IN`, `ta-IN`, `te-IN`, etc.).
- **🔊 Immediate Text-To-Speech (TTS)**: Every translation automatically synthesized with native audio cadence. Touch any historical log item or saved favorite to repeat pronunciation logs gracefully.

### 3. Identity & Custom Settings
- **First-access Onboarding Dialog**: Greets users upon first install and stores the username to customize application banners.
- **Dynamic Font Size Scaling**: Choose between **Small**, **Default**, **Large**, or **Huge** layout adjustments inside the settings console (`getSharedPreferences` storage module).

### 4. Recents, Favorites & Storage Persistence
- Built-in local persistence (utilizing a reactive Room DB backplane) tracking active inputs.
- Bookmark translations into a persistent favorites directory for handy future pronunciation guides.

---

## 🛡️ Setup & Technology Stack

- **Framework**: Jetpack Compose (100% Kotlin-first declarative engine) with strict Material 3 integration.
- **Persistence**: Room Database Engine (DAO, Entity models with reactive Flow components).
- **Core Integrations**:
  - `Android TTS (TextToSpeech)` engine for authentic audio playback.
  - `RecognizerIntent` APIs for voice translation captures.
  - Injected Gradle SDK credentials.
