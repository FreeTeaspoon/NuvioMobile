# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

NuvioTV is a Kotlin Multiplatform / Compose Multiplatform mobile media hub app for Android and iOS. It uses the Stremio addon ecosystem for content. The entire codebase is a single Gradle project with one module (`:composeApp`).

### Prerequisites

- **JDK 21** (pre-installed on Cloud VMs)
- **Android SDK** with platform 36 and build-tools. `ANDROID_HOME` must be set to the SDK location (e.g. `/opt/android-sdk`).
- A `local.properties` file at the repo root with at minimum `sdk.dir=/opt/android-sdk`. Supabase/Trakt keys are optional for compilation but required at runtime.
- iOS builds require macOS + Xcode and are **not available** on Linux Cloud VMs.

### Key commands

| Task | Command |
|---|---|
| Build playstore debug APK | `./gradlew :composeApp:assemblePlaystoreDebug` |
| Build full debug APK | `./gradlew :composeApp:assembleFullDebug` |
| Run unit tests | `./gradlew :composeApp:testPlaystoreDebugUnitTest` |
| Kotlin compile check | `./gradlew :composeApp:compilePlaystoreDebugKotlinAndroid` |
| Android lint | `./gradlew :composeApp:lintPlaystoreDebug` |

### Gotchas

- **iOS targets disabled on Linux**: Gradle will warn about disabled `iosArm64`/`iosSimulatorArm64` targets and cinterop cross-compilation. These warnings are expected and harmless for Android-only builds.
- **Android lint has pre-existing failures**: The codebase has ~41 lint errors and ~139 warnings with no baseline configured. Lint will exit non-zero. Use Kotlin compilation (`compilePlaystoreDebugKotlinAndroid`) as the primary code-correctness check.
- **`local.properties` is gitignored**: Never commit it. The update script recreates it on every session.
- **Two product flavors**: `full` (includes MPV player + QuickJS addon runtime) and `playstore` (restricted). Both build on Linux. Use `playstore` flavor for faster iteration.
- **Gradle configuration cache** is enabled. If you change `build.gradle.kts`, the cache may need to be invalidated (`./gradlew --no-configuration-cache ...` or `./gradlew clean`).
- **No backend services**: All backends (Supabase, Trakt, TMDB, Stremio addons) are external cloud services. There is nothing to start locally besides the Gradle build.
