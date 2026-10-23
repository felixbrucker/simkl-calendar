# AI Agent Guidelines & Architecture Blueprint

## 1. Core Tech Stack & Engineering Standards
- **Language & Runtime:** Kotlin 2.4+ with KSP.
- **Android SDK:** Min SDK 26, Target SDK 37, Compile SDK 37.
- **UI Framework:** Jetpack Compose + Material 3 (Single-Activity architecture via `MainActivity`).
- **Dependency Injection:** Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, constructor injection preferred).
- **Database:** Room + KSP (`exportSchema = true`, foreign key cascades, non-destructive migrations). **Never delete historical schema JSON files.**
- **Key-Value Storage:** Jetpack DataStore Preferences exclusively (wrapped in typed repositories via `Flow`). Synchronous `SharedPreferences` is **prohibited**.
- **Networking:** Retrofit + OkHttp + Moshi codegen.
- **Background Work:** WorkManager (`CoroutineWorker` via `HiltWorkerFactory`) + AlarmManager.
- **Logging:** Timber.

---

## 2. Coding Conventions & Mandatory Rules
- **Idiomatic Kotlin & KTX:** Always leverage Android KTX extension functions (e.g., `.toUri()`, `.edit {}`, ...) over verbose legacy methods.
- **Loops & Collections:** Prefer idiomatic `for (item in items)` loops over index-based `0 until size` loops.
- **Named Boolean Arguments:** ALWAYS use named arguments for boolean parameters at call sites (e.g., `showNotification(isVisible = true, forceRefresh = false)`).
- **Compose 25-Line Limit:** Any UI layout section or block exceeding **25 lines** of code MUST be extracted into a separate standalone `@Composable` helper function.
- **State Hoisting:** Child composables must be stateless, accepting immutable data and lambda callbacks (e.g., `onItemClick: (String) -> Unit`).
- **Default Parameters**: Never define default parameter values in constructors that manually instantiate injectable classes or dependencies.

---

## 3. Build Environment & Commands
Always use the local Gradle wrapper:

    # Gradle Tasks
    ./gradlew assembleDebug
    ./gradlew test
