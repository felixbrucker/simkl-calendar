# AI Agent Guidelines for Simkl Calendar

## Database Migrations & Schema Export Rules

Whenever modifying the Room database or entities in this project:

1. **Database Version Increments**:
   - When any `@Entity`, column, or database configuration changes requiring a version bump, increment the `version` property in `@Database` (`AppDatabase.kt`).
   - Always define the corresponding migration (e.g., `@AutoMigration` with `@AutoMigrationSpec` if columns are deleted/renamed, or manual `Migration`).

2. **MANDATORY Schema JSON Generation & Platform Registration**:
   - Every time the database `version` is incremented, AI agents **MUST** ensure the corresponding JSON schema file is generated, registered in AI Studio's workspace layer, and committed under:
     `app/schemas/com.felixbrucker.simklcalendar.data.database.AppDatabase/<version>.json`
   - Keep `exportSchema = true` on `@Database`.
   - Ensure the KSP schema export argument in `app/build.gradle.kts` (`ksp { arg("room.schemaLocation", "$projectDir/schemas") }`) remains intact.
   - **CRITICAL Platform Registration Step**: When Room's KSP compiler generates the schema in the container background filesystem, the AI Studio Web UI and Git Sync index will not automatically track it unless it is explicitly written through the agent's file tools. AI agents **MUST** view the newly generated `<version>.json` content and explicitly create/write it using `create_file` so AI Studio's workspace system and Git sync tracking register it properly.
   - Never delete existing schema JSON files (e.g. `7.json`, `8.json`, `9.json`, `10.json`, `11.json`, `12.json`, `13.json`, etc.), as Room requires them for verifying auto-migrations and history.

3. **Data Safety**:
   - Never enable destructive migrations. Always write non-destructive migrations to preserve user data.

## Test Generation & Structure

When writing or modifying tests, you MUST follow these guidelines to ensure comprehensive coverage and readability:

* **Exhaustive Path Coverage:** Never test just a single code path. You must generate tests for **all** possible code paths within the target method. 
* **Branch Testing:** If the method contains conditional logic (e.g., `if`/`else`, `when` statements), you must explicitly write tests that cover every possible case and outcome.
* **Strict 3-Section Pattern:** Every test must be structured into three clearly separated sections (Arrange, Act, Assert):
  1. **Setup:** Configure only the state and variables specific to what this exact test is verifying. *Note: Generic setup that applies to multiple tests must be abstracted into `before` hooks (or equivalent setup methods).*
  2. **Execution:** Call the target method being tested.
  3. **Verification:** Assert and verify the expected results or state changes.

## Google Jules Agent Environment Setup

> **CRITICAL DIRECTIVE:** The instructions in this section apply ONLY to the **Google Jules** agent. All other AI coding agents (Cursor, Copilot, Aider, Claude Code, etc.) MUST IGNORE this section entirely.

The default Google Jules VM environment provisions older versions of Java, Kotlin, and the Android SDK. This codebase relies on modern tooling and strictly requires **Java 25** and **Android SDK 37**.

Whenever Jules starts a new session, reloads the workspace, or prepares to run a build, it must first execute the following script to prepare the environment:

```bash
# Install Java 25 via apt
sudo apt-get update
sudo apt-get install -y openjdk-25-jdk

# Export JAVA_HOME so the Gradle wrapper uses the newly installed Java 25
export JAVA_HOME="/usr/lib/jvm/java-25-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"

# Install Android SDK 37 using the globally available sdkmanager
yes | sdkmanager "platforms;android-37"
```

### Jules Directory & Build Rules

1. **Local Gradle Wrapper:** Jules must **NEVER** use a globally installed `gradle` command. Always use the project's local wrapper to ensure the correct AGP and Kotlin configuration is respected.
   ```bash
   # Ensure the wrapper is executable
   chmod +x gradlew
   
   # Execute tasks using the local wrapper
   ./gradlew <task>
   ```
2. **Troubleshooting:** If a build fails with an unsupported class file major version error or a missing SDK error, immediately verify that `JAVA_HOME` is set to Java 25 and that you are actively executing `./gradlew` from inside `/app`.
