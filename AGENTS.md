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
   - Never enable destructive migrations (`fallbackToDestructiveMigration(false)` is strictly enforced). Always write non-destructive migrations to preserve user data.
