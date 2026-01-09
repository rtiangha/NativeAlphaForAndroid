1→# AGENTS.md - Native Alpha Development Guide
2→
3→## Setup
4→```bash
5→# No virtual environment needed (Android/Gradle project)
6→# Sync project with Gradle wrapper
7→./gradlew --version
8→```
9→
10→## Commands
11→- **Build**: `./gradlew assembleDebug` or `./gradlew assembleRelease`
12→- **Lint**: `./gradlew lint`
13→- **Test**: `./gradlew test` (unit tests), `./gradlew connectedAndroidTest` (instrumented tests)
14→- **Dev**: Install to connected device/emulator: `./gradlew installDebug`
15→
16→## Tech Stack
17→- **Language**: Kotlin (primary), Java (WebViewActivity)
18→- **Build**: Gradle 8.8.2, Android Gradle Plugin
19→- **Target**: Android SDK 35, Min SDK 28 (Android 9+)
20→- **Architecture**: Activity-based with Fragments, DataBinding/ViewBinding
21→- **Key Libraries**: AndroidX WebKit, Jsoup, AdblockAndroid, Material Design 3
22→
23→## Code Style
24→- Use Kotlin for new code; follow existing conventions in `app/src/main/kotlin/`
25→- Java used only for WebViewActivity template (auto-generated classes)
26→- Object pattern for utility classes (e.g., `DateUtils`, `ColorUtils`)
27→- Companion objects for static members in regular classes
28→- No comments unless complex logic; prefer self-documenting code
29→