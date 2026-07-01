# BritSpeak — English Speaking Tutor (Android)

A production-grade Android app that helps Chinese-speaking learners practise **spoken
English** with a good-looking **digital-human tutor** that speaks **natural British English
(en-GB)**, corrects the learner's mistakes (in **English or Chinese**, learner's choice),
and asks the learner to **repeat the corrected sentence**. Conversations can be themed
around a **topic or role-play scenario**, picked fresh each day or continued from the
previous session. Benchmarked against 「咕噜口语」 (Gulu Speaking).

---

## 1. Goals & success criteria

| Requirement (from brief)                                   | How it is met |
|------------------------------------------------------------|---------------|
| Good-looking digital human                                 | `DigitalHumanAvatar` — a polished, Compose-drawn animated tutor with idle/listening/thinking/speaking states, blinking, breathing and viseme-style lip-sync driven by TTS progress. Asset-free, no licensing, upgrade path to Live2D/3D documented. |
| Natural British English conversation                       | LLM (Claude `claude-opus-4-8`) with a British-tutor system prompt + `Locale.UK` neural TTS. |
| Detect mistakes, explain grammar/pronunciation             | Structured `corrections[]` returned by the model (grammar / vocabulary / pronunciation / naturalness). |
| Explanation language selectable (EN / 中文)                | `FeedbackLanguage` setting; each correction carries **both** `explanationEn` and `explanationZh`; UI shows the chosen one and can toggle. |
| Ask the learner to repeat the correct sentence             | `repeatTarget` in every tutor turn + a "repeat & score" UI flow that re-runs STT and compares. |
| Choose a topic / simulate a scenario                       | `TopicCatalog` of curated topics & role-play scenarios (restaurant, airport, interview…). |
| Pick a new topic each day or continue the previous chat    | `DailyTopicSelector` (deterministic per-day rotation) + session persistence/resume. |
| Train speaking **and** listening                           | STT (speaking) + TTS (listening) + repeat-after-me drills. |
| Production-grade, directly deliverable                     | Clean architecture, fully unit-tested pure-Kotlin core, Android instrumentation tests, CI that builds the APK. |

---

## 2. Architecture

Clean architecture + MVVM, split into two Gradle builds so the **brains are testable on a
plain JVM** (no Android SDK / emulator needed):

```
englishteacher/                ← root Android Gradle build (built by CI)
├── settings.gradle.kts        includeBuild("core"); include(":app")
├── app/                       ← Android application module (Compose, TTS/STT, Room, Hilt)
└── core/                      ← STANDALONE Kotlin/JVM Gradle build (no Android deps)
    └── src/{main,test}/kotlin ← domain, AI client, use-cases, catalog  + full unit tests
```

* **`core`** is a self-contained Gradle build. It depends only on Maven-Central artifacts
  (kotlinx-serialization, coroutines, OkHttp) and is **compiled and unit-tested in CI and in
  the dev container**. It holds every piece of business logic:
  * `domain.model` — immutable models (`ChatMessage`, `Correction`, `Topic`,
    `ConversationSession`, `TutorTurn`, enums).
  * `domain.port` — interfaces the app implements (`SessionRepository`, `Clock`,
    `IdGenerator`, `TutorEngine`).
  * `ai` — `PromptBuilder` (British-tutor system prompt + JSON schema),
    `ConversationResponseParser`, and `AnthropicTutorEngine` (OkHttp + Anthropic
    Messages API, structured `output_config.format`).
  * `catalog` — `TopicCatalog` with curated topics/scenarios.
  * `usecase` — `StartSessionUseCase`, `ContinueSessionUseCase`, `SendUtteranceUseCase`,
    `ListTopicsUseCase`, `DailyTopicSelector`, `RepeatScorer`.
* **`app`** depends on `core` via a Gradle **composite build** (`includeBuild("core")`),
  and adds the Android shell:
  * Jetpack Compose UI (`ChatScreen`, `TopicScreen`, `SettingsScreen`, `HistoryScreen`,
    onboarding).
  * `DigitalHumanAvatar` Compose component.
  * Platform adapters: `AndroidSpeechRecognizer` (STT), `AndroidTextToSpeech`
    (`Locale.UK`), Room (`SessionRepository`), DataStore (settings + encrypted API key).
  * Hilt DI, `ChatViewModel` etc.

### Why two builds
The CI runner has the Android SDK and full network access, so the root build compiles the
APK. The dev/test container's network policy blocks `dl.google.com` (Android SDK + Google
Maven), so the Android module cannot compile there — but the `core` build can, and it carries
all the logic worth testing. Splitting the build lets us **prove the core correct here** and
**ship a buildable APK via CI**.

---

## 3. Conversation / correction design

One LLM call per learner utterance returns **structured JSON** (enforced by
`output_config.format` json_schema):

```json
{
  "reply": "Lovely! And what did you have for breakfast?",
  "hasErrors": true,
  "corrections": [
    { "original": "I eat breakfast at 8 yesterday",
      "corrected": "I had breakfast at 8 yesterday",
      "type": "grammar",
      "explanationEn": "Use the past simple 'had' for a finished action in the past.",
      "explanationZh": "描述过去发生且已结束的动作，要用一般过去时 'had'。" }
  ],
  "repeatTarget": "I had breakfast at 8 yesterday."
}
```

* The model **always stays in British English and in character** for `reply`.
* `corrections` is empty when the utterance is fine.
* The app shows explanations in the learner's chosen language and drives the
  repeat-after-me flow from `repeatTarget`.
* `RepeatScorer` compares the learner's repeat (via STT) to `repeatTarget` (token-level
  similarity) and returns a 0–100 score with the differing words highlighted.

### Model & API
* Model: `claude-opus-4-8` (configurable). Endpoint `POST /v1/messages`,
  `anthropic-version: 2023-06-01`.
* No `temperature`/`thinking` (latency + 4.x API constraints); structured output via
  `output_config.format`.
* **API key**: BYO-key, entered in Settings, stored in `EncryptedSharedPreferences`. The
  `TutorEngine` also accepts a custom `baseUrl`, so teams can point it at a backend proxy
  for production key custody (documented in README).

---

## 4. Testing strategy

* **`core` (runs here + CI, JVM):**
  * Pure unit tests for models, `PromptBuilder`, `ConversationResponseParser`,
    `TopicCatalog`, `DailyTopicSelector`, `RepeatScorer`, every use-case (with in-memory
    fakes).
  * **End-to-end** test of `AnthropicTutorEngine` against **OkHttp MockWebServer** —
    asserts the exact request shape (headers, model, schema) and parses a canned API
    response into a `TutorTurn`. This exercises the real HTTP + serialization path without
    network or secrets.
* **`app` (CI):** Robolectric/JVM unit tests for `ChatViewModel` and the avatar state
  machine; Compose UI tests for the main screens; instrumentation smoke test.
* **CI:** GitHub Actions builds `:app:assembleDebug`, runs `core` tests + `app` unit tests,
  and uploads the APK artifact.

---

## 5. Upgrade paths (documented, not blocking)
* Replace the Compose avatar with Live2D Cubism or a 3D `SceneView` model + phoneme-driven
  lip-sync.
* Swap BYO-key for a backend token-broker (`baseUrl` already pluggable).
* Add on-device pronunciation scoring (e.g. Vosk / Whisper) for phoneme-level feedback.
