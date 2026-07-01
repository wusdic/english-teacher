# BritSpeak — English Speaking Tutor 🇬🇧🗣️

英语陪练 · A production-grade Android app that helps Chinese-speaking learners practise
**spoken English** with a good-looking **digital-human tutor** (Emma) who speaks natural
**British English**, corrects mistakes in **English or 中文**, and has you **repeat the
correct sentence** out loud. Pick a topic or role-play scenario, get a fresh topic each day,
or continue your previous chat. Trains both **speaking** and **listening**. Benchmarked
against 「咕噜口语」.

> **Note on architecture & verification:** the app is split into a pure-Kotlin **`core`**
> module (all the business logic + AI orchestration, **fully unit-tested**) and an Android
> **`app`** module (Compose UI, digital-human avatar, speech, persistence). The core is built
> and tested on a plain JVM; the Android APK is built by CI. See [docs/PLAN.md](docs/PLAN.md).

---

## Features

| | |
|---|---|
| 🧑‍🎨 **Digital human** | A polished, animated Compose-drawn tutor with idle / listening / thinking / speaking states, blinking, breathing and lip-sync. |
| 🇬🇧 **British English** | Claude `claude-opus-4-8` with a British-tutor persona + `en-GB` neural TTS. |
| ✅ **Smart corrections** | Grammar / vocabulary / pronunciation / naturalness, each with **both** an English and a Chinese explanation. |
| 🔤 **EN / 中文 toggle** | Switch the correction language instantly — no re-fetch. |
| 🔁 **Repeat after Emma** | Every turn suggests a sentence to say back; your attempt is scored word-by-word. |
| 🎭 **Topics & scenarios** | Restaurant, airport, job interview, doctor, IELTS… plus open free chat. |
| 📅 **Daily topic** | A deterministic "topic of the day", or continue your last conversation. |
| 🎧 **Speaking + listening** | Speech-to-text in, text-to-speech out, repeat-after-me drills. |
| 🧠 **Any model** | Switch between the **Anthropic** format and any **OpenAI-compatible** endpoint (OpenAI, DeepSeek, Qwen, Kimi, a local server…) in Settings. |
| 🔌 **Offline recognition** | Speech recognition runs **fully on-device (Vosk)** — the model is bundled in the APK, no network needed to recognise your speech. |
| 💬 **Subtitles** | Optional bottom captions during the conversation (toggle any time). |

---

## Project layout

```
englishteacher/
├── core/                 # Standalone pure-Kotlin/JVM build — the tutor "brain" (tested)
│   └── src/{main,test}    #   domain models · AI client · use-cases · catalogue + tests
├── app/                  # Android application (Compose UI, avatar, TTS/STT, Room, Hilt)
├── docs/PLAN.md          # Full design & validation document
└── .github/workflows/    # CI: core tests + APK build + app tests
```

---

## Build & run

### Prerequisites
- JDK 17
- Android SDK (for the `app` module) — Android Studio Koala or newer recommended.

### Run the tested core logic (no Android SDK needed)
```bash
cd core
./gradlew test
```

### Build the Android app
```bash
# from the repo root
bash scripts/fetch-models.sh          # one-time: download the offline Vosk model into assets
./gradlew :app:assembleDebug          # builds the APK (pulls in core via composite build)
./gradlew :app:testDebugUnitTest      # app JVM unit tests
```
> `scripts/fetch-models.sh` downloads the ~40 MB offline speech-recognition model into
> `app/src/main/assets/` (it is not committed to git). CI runs this automatically before the
> build. Without it the app still runs, but offline recognition will be unavailable until the
> model is present.
Open the project in Android Studio and run the `app` configuration on a device/emulator with
a microphone and Google TTS/Speech services.

### Configure your model & API key
Open **Settings** and pick a **Model provider** — *Anthropic* or *OpenAI-compatible* — then
set the **Base URL** and **Model** (sensible defaults are filled in) and paste your **API key**
(stored **encrypted** on-device via `EncryptedSharedPreferences`). This lets the app drive
Claude, GPT-4o, DeepSeek, Qwen, Kimi, a local model, etc. For a multi-user production
deployment, point the Base URL at your own backend proxy and drop the BYO-key flow.

---

## Testing

* **`core`** — 40+ JVM unit tests covering the domain models, prompt builder, structured-output
  parser, topic catalogue, daily-topic selector, repeat scorer and every use-case, **plus an
  end-to-end test of the real HTTP + JSON path against an in-process OkHttp `MockWebServer`**.
  Run with `cd core && ./gradlew test`.
* **`app`** — JVM unit tests for the avatar/phase logic, the Room ↔ domain mappers, and the
  `ChatViewModel`; a Compose UI test for the chat components. Run with
  `./gradlew :app:testDebugUnitTest`.
* **CI** ([.github/workflows/ci.yml](.github/workflows/ci.yml)) builds the debug APK, runs all
  tests, compiles the instrumentation tests, and uploads the APK as an artifact.

---

## Tech stack
Kotlin · Coroutines · Jetpack Compose (Material 3) · Hilt · Room · DataStore ·
kotlinx.serialization · OkHttp · Anthropic Messages API + OpenAI-compatible Chat Completions
(structured / JSON output) · **Vosk** on-device offline speech recognition · Android
`TextToSpeech` (en-GB).

## License
Provided as a reference implementation for the English-tutor brief.
