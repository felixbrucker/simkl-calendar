# AI Agent Guidelines for Simkl Calendar

## Database Migrations & Schema Export Rules

Whenever modifying the Room database or entities in this project:

1. **Database Version Increments**:
   - When any `@Entity`, column, or database configuration changes requiring a version bump, increment the `version` property in `@Database` (`AppDatabase.kt`).
   - Always define the corresponding migration (e.g., `@AutoMigration` with `@AutoMigrationSpec` if columns are deleted/renamed, or manual `Migration`).

2. **MANDATORY Schema JSON Generation**:
   - Every time the database `version` is incremented, AI agents **MUST** ensure the corresponding JSON schema file is generated/written and committed under:
     `app/schemas/com.felixbrucker.simklcalendar.data.database.AppDatabase/<version>.json`
   - Keep `exportSchema = true` on `@Database`.
   - Ensure the KSP schema export argument in `app/build.gradle.kts` (`ksp { arg("room.schemaLocation", "$projectDir/schemas") }`) remains intact.
   - Run compilation (`compile_applet` or Gradle build) after any database changes to ensure Room's KSP compiler exports the updated `<version>.json` schema file into `app/schemas/`.
   - Never delete existing schema JSON files (e.g. `7.json`, `8.json`, `9.json`, `10.json`, `11.json`, `12.json`, etc.), as Room requires them for verifying auto-migrations and history.

3. **Data Safety**:
   - Never enable destructive migrations (`fallbackToDestructiveMigration(false)` is strictly enforced). Always write non-destructive migrations to preserve user data.
