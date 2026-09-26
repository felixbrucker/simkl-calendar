# AI Agent Guidelines & Architecture Blueprint

This document defines the strict architectural standards, coding conventions, tech stack choices, database schema guidelines, network paradigms, UI designs, performance requirements, and testing patterns expected for this Android codebase. All AI coding agents working on or generating code in this project MUST follow these directives strictly to ensure uniform code style, maintainability, and production quality.

---

## 1. Tech Stack & Engineering Specifications

| Layer | Library / Tool | Configuration & Standards |
| :--- | :--- | :--- |
| **Language & Runtime** | Kotlin 2.4+ (Java 11/25 Target) | Idiomatic Kotlin with KSP (Kotlin Symbol Processing). |
| **Android SDK** | Min SDK 26, Target SDK 37, Compile SDK 37 | Native Android 16 (API 37) compatibility; backward compatible to Android 8.0. |
| **UI Framework** | Jetpack Compose + Material 3 | Single-Activity architecture (`MainActivity`), Compose BOM, Navigation Compose, Material 3 Dark theme. |
| **Dependency Injection** | Hilt | Scoped injection (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Inject constructor(...)`). |
| **Database** | Room 2.8.5 + KSP | SQLite database with KSP schema exports (`exportSchema = true`), Foreign Keys (`CASCADE`), non-destructive migrations. |
| **Key-Value Storage** | Jetpack DataStore Preferences | Asynchronous `Flow`-based DataStore preferences wrapped in dedicated typed repositories. Synchronous `SharedPreferences` is prohibited. |
| **Networking** | Retrofit 2/3 + OkHttp 5 + Moshi | Moshi JSON adapter codegen (`moshi-kotlin-codegen`). Custom OkHttp Interceptors & Authenticators for OAuth2 PKCE token refreshes. |
| **Image Loading** | Coil Compose | `AsyncImage` for asynchronous image loading. |
| **Background Scheduling** | WorkManager + AlarmManager | `CoroutineWorker` via `HiltWorkerFactory` for periodic background tasks; `AlarmManager` for time-exact scheduled alarms. |
| **Logging** | Timber + Disk File Buffer | Custom `AppLogTree` streaming log entries to Logcat and daily buffered JSON files (`LogRepository`). |
| **Testing** | JUnit 4 + MockK + Coroutines Test | MockK for mocking, `UnconfinedTestDispatcher` / `runTest` for coroutine tests, Kover coverage verification (70% min threshold). |

---

## 2. Architecture & Layering Principles

The application follows a strict Clean Layered Architecture with Unidirectional Data Flow (UDF):

```
UI Layer (Screens / Composables / Feature ViewModels)
   ▲
   │ StateFlow<UiState> / Navigation Events
   ▼
ViewModel Layer (@HiltViewModel)
   ▲
   │ Coroutines / StateFlow Streams
   ▼
Repository Layer (@Singleton Repositories in core/data)
   ▲
   │ Domain Logic / Data Caching / Sync
   ▼
Data Sources (Room DAOs, DataStores, Retrofit Services, Background Services)
```

### 2.1 Ideal Package Organization Pattern (Core & Feature Modularization)
Organize code following Android's official architecture guidelines by separating shared infrastructure (`core/`) from isolated feature presentation modules (`feature/`). This package-by-feature pattern ensures high cohesion, loose coupling, and seamless future multi-module migration:

```
com.example.app/
├── MainActivity.kt                       # Host Activity initializing Navigation Graph & Edge-to-Edge
├── MainApplication.kt                    # Application setup & Hilt DI initialization
├── core/
│   ├── data/                             # Repositories & Unified Data Strategy
│   ├── database/                         # Room AppDatabase, DAOs, Entities, Converters, Migrations
│   ├── datastore/                        # DataStore Preferences Repositories & Data Models
│   ├── designsystem/                     # Material 3 Theme, Typography, Colors, Shared Composables
│   ├── logging/                          # AppLogTree, LogEntry, LogRepository for disk log buffering
│   ├── model/                            # Shared Domain Data Models, Enums, Value Classes
│   ├── network/                          # Retrofit Services, DTOs, Interceptors, Authenticators
│   ├── notifications/                    # NotificationManager, AlarmScheduler, Broadcast Receivers
│   └── util/                             # Date, Formatting, Permission, and System Utilities
├── feature/
│   ├── auth/                             # Login UI, AuthViewModel, Auth Navigation
│   ├── dashboard/                        # Main Dashboard UI, DashboardViewModel, View Mode Toggles
│   ├── detail/                           # Item Detail UI, DetailViewModel, Action Controls
│   ├── logs/                             # Log Viewer UI, LogViewerViewModel
│   └── settings/                         # Settings UI, SettingsViewModel
├── di/                                   # Application-wide Hilt DI Modules
├── extensions/                           # Extension functions (Context, String, List)
└── worker/                               # Background WorkManager CoroutineWorkers
```

### 2.2 Repository Design & Aggregate Root Organization
- **Domain / Aggregate Root Alignment**: Repositories MUST be organized around specific domains and aggregate roots (e.g., `UserRepository`, `CatalogRepository`, `OrderRepository`, `SyncRepository`, `NotificationRepository`) rather than mapped 1-to-1 to raw database tables or API endpoints.
- **Aggregate Responsibility**: A single repository manages the complete lifecycle, local caching, and remote network synchronization for its aggregate root entity and associated child entities/value objects.
- **DAO Abstraction**: ViewModels consume repositories aligned with their aggregate domain rather than directly interacting with multiple low-level DAOs or raw network API interfaces.

---

## 3. Dependency Injection & Coding Conventions

### 3.1 Idiomatic Kotlin
- **KTX Extensions**: Leverage Android KTX extension functions (`.toUri()`, `.edit {}`, `lifecycleScope`) over verbose legacy methods.
- **Loops & Collections**:
  - Prefer idiomatic `for (item in items)` loops over index-based `0 until size` loops.
  - Use `.withIndex()` or `.forEachIndexed` only when index access is strictly necessary.
- **Named Boolean Arguments**: ALWAYS use named arguments for boolean parameters to clarify call-site intent.
  ```kotlin
  // Correct
  showNotification(isVisible = true, forceRefresh = false)

  // Incorrect
  showNotification(true, false)
  ```

### 3.2 Hilt Injection Rules
- **Constructor Injection**: Prefer direct constructor injection (`@Inject constructor(...)` with scope annotations like `@Singleton`) over `@Module` `@Provides` methods whenever possible.
- **Hilt Modules**: Use `@Module` with `@InstallIn(SingletonComponent::class)` ONLY for interfaces, Room DAOs, Retrofit API services, or third-party library builders.
- **Framework Subclasses**: Always extend standard Android framework classes (`ComponentActivity`, `Application`, `BroadcastReceiver`, `CoroutineWorker`) directly with Hilt annotations (`@AndroidEntryPoint`, `@HiltAndroidApp`, `@HiltWorker`). Never extend generated `Hilt_*` classes directly, and NEVER set `disableAndroidSuperclassValidation = true`.
- **Default Parameters**: Never define default parameter values in constructors that manually instantiate injectable classes or dependencies.

---

## 4. Local Storage & Database Guidelines

### 4.1 Room Database Standards
- Annotate database entities with `@Entity(tableName = "...")` and explicit primary keys.
- Use multi-table Foreign Keys with explicit `onDelete = ForeignKey.CASCADE` and `onUpdate = ForeignKey.CASCADE` to preserve relational integrity.
- Index foreign key columns and frequently queried fields (`indices = [Index(value = ["foreignId"]), Index(value = ["date"])]`).
- Model multi-table joined data using `@Embedded` and `@Relation` inside immutable data classes annotated with `@Immutable`:
  ```kotlin
  @Immutable
  data class ParentWithChildren(
      @Embedded val parent: ParentEntity,
      @Relation(parentColumn = "id", entityColumn = "parentId")
      val children: List<ChildEntity>,
      @Relation(parentColumn = "id", entityColumn = "parentId")
      val state: LocalStateEntity? = null
  )
  ```

### 4.2 Database Version Increments & Schema JSON Rules
Whenever modifying Room entities or database schema:
1. **Increment Database Version**:
   - Increment `version` in `@Database` (`AppDatabase.kt`).
2. **Define Non-Destructive Migrations**:
   - Use `@AutoMigration` with `@AutoMigrationSpec` for renamed/deleted columns or tables.
   - Write explicit manual `Migration(from, to)` classes for complex structural transformations or data backfills.
   - Never enable `fallbackToDestructiveMigration()`.
3. **MANDATORY Schema JSON Generation & Platform Tracking**:
   - Maintain `exportSchema = true` in `@Database`.
   - Ensure `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` remains in `build.gradle.kts`.
   - Verify that generated schema JSON files under `app/schemas/<applicationId>.data.database.AppDatabase/<version>.json` are created and committed. Never delete historical schema JSON files (`1.json`, `2.json`, etc.).

### 4.3 Key-Value Storage via DataStore
- Use Jetpack DataStore Preferences exclusively (`preferencesDataStore(name = "...")`).
- Encapsulate each DataStore inside a dedicated Repository class in `core/datastore/` (e.g., `AppSettingsRepository`, `AuthRepository`).
- Expose typed immutable preference data classes via `Flow` streams (`preferencesFlow`).
- Support functional updates via `dataStore.edit { preferences -> ... }`.

---

## 5. Networking & OAuth2 Integration

### 5.1 Networking Architecture
- **Dual API Services**:
  - `PublicApiService`: Handles unauthenticated endpoints, CDN fetches, and authentication exchanges.
  - `AuthenticatedApiService`: Handles protected user data, sync operations, and user state updates.
- **Interceptors**:
  1. **Rate Limiting Interceptor**: Prevents API throttling by enforcing request intervals.
  2. **Static Parameter / API Key Interceptor**: Appends required static headers or query parameters (e.g., API keys or client identifiers).
  3. **Auth Token Interceptor**: Retrieves active credentials/tokens from local authentication storage/DAO and dynamically attaches the `Authorization: Bearer <accessToken>` header.
  4. **Logging Interceptor**: Logs request URLs, headers, and HTTP status codes.
- **Token Refresh Authenticator**:
  - Catches HTTP 401 Unauthorized responses on authenticated endpoints.
  - Acquires a thread-safe mutex lock to prevent concurrent token refresh requests.
  - Invokes the token refresh endpoint using the persisted refresh token.
  - Updates the local authentication storage/DAO with the new access token and retries the original failed request.

### 5.2 OAuth2 PKCE Authorization Flow
1. Generate PKCE parameters: `code_verifier` (32 random bytes URL-safe Base64), `code_challenge` (SHA-256 hash of verifier URL-safe Base64), and random `state`.
2. Store `code_verifier`, `redirectUri` (`appscheme://auth`), and `state` in `AuthRepository`.
3. Open `AuthTabIntent` (or fallback `CustomTabsIntent`) pointing to authorization URL.
4. Capture redirect intent in `MainActivity.onNewIntent`, verify `state`, and exchange authorization `code` via POST `/oauth2/token` for access and refresh tokens, saving them to the authentication storage/DAO.

---

## 6. Background Scheduling & Notifications

### 6.1 Alarm Scheduling & Background Receivers
- **`AlarmScheduler`**: Queries database for scheduled items, calculating trigger times and setting exact/inexact alarms via `AlarmManager.setExactAndAllowWhileIdle` targeting `AlarmReceiver` with an explicit action and intent data URI.
- **`AlarmReceiver`**: Executed on alarm trigger. Invokes `goAsync()`, updates database status, executes background processing, and posts system notifications via `NotificationManager`.

### 6.2 Notification System
- **`NotificationManager`**: Constructs Material 3 system notifications with `NotificationCompat.Builder` on dedicated notification channels.
  - Asynchronously loads large icons/images via Coil `ImageLoader`.
  - Adds action buttons targeting `PendingIntent` broadcast receivers (`NotificationActionReceiver`).
  - Persists active notifications in `ActiveNotificationDao` and restores them on system reboot via `restoreActiveNotifications()`.

---

## 7. UI Layer, State Hoisting & Compose Standards

### 7.1 Single-Activity Navigation
- `MainActivity` acts as the single entry point with `enableEdgeToEdge()`.
- Top-level `NavHost` handles screen transitions using explicit route strings.
- Pass URL-encoded string arguments (`URLEncoder.encode(param, "UTF-8")`) when navigating across routes with string parameters.
- Provide safe backstack navigation helpers (`navController.popBackStackSafely()`).

### 7.2 Composable Function Decomposition Rule (15-Line Limit)
- **Granular Composable Extraction**: UI code MUST be split into small, single-responsibility `@Composable` functions.
- **15-Line Limit**: Any UI layout section or block that exceeds **15 lines** of code MUST be extracted into a separate standalone `@Composable` helper function (e.g., `HeaderCard`, `SearchBar`, `StatusBadge`, `SectionTitle`).
- **Benefits**: This strict decomposition keeps screen files clean and readable, reduces re-composition scope, simplifies UI testing, and enables isolated `@Preview` functions.

### 7.3 State Hoisting & Flow Subscription Directives
- **Stateless Child Composables & State Hoisting**: All child views, components, cards, and UI composables MUST be stateless components using state hoisting. They must accept raw immutable data parameters and expose lambda callback functions (e.g., `onItemClick: (String) -> Unit`) rather than receiving `ViewModel` instances directly.
- **Stateful Top-Level Containers**: Top-level screen composables act as stateful containers that host ViewModels, collect state via `collectAsState()`, and pass state values and event lambda callbacks down to stateless child composables.
- **Single Subscription for Cold Flows**: Cold flows (e.g., database query flows or network streams) MUST be subscribed to at most once across the app to prevent redundant query executions or duplicated network requests. Convert cold flows to hot `StateFlow` / `SharedFlow` streams using `stateIn` / `shareIn` with `SharingStarted.WhileSubscribed(5000)` at the repository or ViewModel layer.

---

## 8. Performance & Zero-Allocation Directives

To eliminate main-thread jank and Garbage Collection (GC) pauses:

1. **Date Grouping**: Group sorted collections by native `LocalDate` BEFORE mapping keys to localized display strings in Compose state calculations:
   ```kotlin
   val (earlierList, earlierGrouped, upcomingGrouped) = remember(items) {
       val zone = ZoneId.systemDefault()
       val today = LocalDate.now(zone)
       // Group by LocalDate first, mapKeys second
       ...
   }
   ```
2. **Single-Pass Collection Segregation**: Avoid chaining multiple `.filter` predicates on state collections in Compose screens. Combine segregations into a single `for` loop pass inside `remember(items)` blocks.
3. **Primitive Arrays in Measure Policies**: Use primitive `IntArray` and `Array<Placeable>` instead of `List` or `MutableList` inside custom Compose `MeasurePolicy.measure` blocks.
4. **Sublist Slicing & Direct Stream Writing**: Use `indexOfFirst` and `subList` when pruning chronologically sorted log collections. Stream logs directly to disk using `BufferedWriter`.
5. **DataStore Pre-fetching**: Pre-fetch DataStore `Flow` values (`preferencesFlow.first()`) ONCE prior to entering hot processing loops or bulk synchronization operations.

---

## 9. Test Generation & Structure Standards

All new feature code, viewmodels, repositories, and utilities MUST be accompanied by comprehensive unit tests adhering to these rules:

### 9.1 General Testing Rules
- **Mandatory Unit Tests**: Every business logic class MUST have corresponding unit tests.
- **Exemptions**: UI Composables, Screens, Theme, BuildConfig, and generated Hilt code (`*Hilt_*`, `*Factory*`) are exempt as per Kover exclusion rules in `build.gradle.kts`.
- **Exhaustive Coverage**: Test ALL code paths, including success, failure, null inputs, empty collections, and every conditional branch (`if`/`else`, `when`).

### 9.2 Strict 3-Section Test Pattern (Arrange-Act-Assert)
Every test method MUST follow this exact formatting:
- **Phase 1 (Setup / Arrange)**: Configure mock returns and state variables.
- **Phase 2 (Execution / Act)**: Invoke target method.
- **Phase 3 (Verification / Assert)**: Assert outputs or verify mock calls.
- **Formatting Rules**:
  - Each phase MUST be a single continuous block of code with NO empty lines inside it.
  - Separate each phase with EXACTLY ONE empty line.
  - Do NOT write comments labeling the sections (e.g., NEVER write `// Setup`, `// Arrange`, `// Act`, `// Assert`).

#### Correct Example:
```kotlin
@Test
fun testUpdateItemStateSuccess() = runTest {
    val viewModel = createViewModel()
    coEvery { repositoryMock.updateItemState(1, "ACTIVE") } returns Result.success(Unit)

    var resultCallback = false
    viewModel.updateItemState(1, "ACTIVE") { ok ->
        resultCallback = ok
    }
    advanceUntilIdle()

    assertTrue(resultCallback)
    coVerify { repositoryMock.updateItemState(1, "ACTIVE") }
}
```

---

## 10. Build Environment & Executables

### 10.1 Jules VM & Automation Environment Rules
When executing automated builds or unit test tasks in headless environments requiring specific Java versions:
```bash
# Environment setup for Java 25 & SDK 37
sudo apt-get update && sudo apt-get install -y openjdk-25-jdk
export JAVA_HOME="/usr/lib/jvm/java-25-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"
yes | sdkmanager "platforms;android-37"

# Execute tasks using local Gradle wrapper ONLY
chmod +x gradlew
./gradlew test
```

### 10.2 Gradle Wrapper Usage
Always use the local Gradle wrapper (`./gradlew` or `gradlew.bat`):
- `./gradlew assembleDebug`: Build debug APK.
- `./gradlew test`: Run unit test suite.
- `./gradlew koverVerify`: Run Kover test coverage verification.
