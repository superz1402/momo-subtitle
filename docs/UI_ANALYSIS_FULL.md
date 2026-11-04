# UI Analysis — GitHub repos of bilal140202, ansaribilal14, superz1402
Research for **Momo Subtitle** (Android media-utility app). Unauthenticated GitHub access only (API was rate-limited; repo lists were scraped from public HTML profile pages, clones were `git clone --depth 1`, no tokens used).

## Repo discovery summary (per user)

### bilal140202 (30 public repos)
- Android/UI-relevant: **AuraGallery** (Kotlin, Compose photo gallery w/ on-device CLIP AI) ✅ analyzed
- **momo-1.0** — NOT Android despite the name: Python ML repo (`app.py`, `train.py`, `momo_core.py`). No UI.
- Not Android: ytagent, imgagent, myang-studio (Python/TS web agents), momostack/momotts (HTML), masthead (web/bun), privacy-think (Tauri desktop), antigravity-skill-recorder (TS/Node), skill-atlas/lkos-system/universal-transcriptor (Rust/Python).
- **Picked: AuraGallery** (+ momo-1.0 cloned and rejected as non-Android; masthead & privacy-think also cloned and rejected).

### ansaribilal14 (30 public repos)
- Android/UI-relevant: **zerium-browser** (Java + Compose, GeckoView-based browser with download manager) ✅ analyzed
- **open-video-engine** — Rust workspace (ove-decode/ove-encode/ove-engine/ove-mcp): media decode/encode engine, zero UI. Relevant to media processing philosophy but nothing to copy for UI.
- Not Android: ytagent, ttagent, documentary-agent-kit (pipeline tooling), zerium-gecko, uaal, open-video-engine-android (Rust).
- **Picked: zerium-browser** (+ open-video-engine cloned and rejected as non-Android).

### superz1402 (22 public repos)
- Android/UI-relevant: **nexpdf** (Kotlin, pure-Compose PDF utility) ✅ analyzed; **linuxify** (Kotlin, Compose Linux-on-Android tool) ✅ analyzed
- Not Android: opus-video-engine, documentary-engine, films (HTML sites), agent-ui-playbook, apk-aegis, dmarcduck (?).
- **Picked: nexpdf + linuxify** (2 repos, within the max-4 total).

**Final analyzed set (4):** AuraGallery, zerium-browser, nexpdf, linuxify.

---

## Repo 1 — bilal140202/AuraGallery
- **Language:** Kotlin · **UI framework:** Jetpack Compose + Material 3 (single-activity)
- **Build:** version catalog (`libs.*`), AGP via catalog, Kotlin+Compose plugins, KSP + Hilt + Room + kotlinx-serialization. minSdk 26, compileSdk/targetSdk 34, JVM 17. Compose M3 experimental opt-ins declared in `kotlinOptions.freeCompilerArgs`. Media3 referenced (`androidx.media3.common.util.UnstableApi` opt-in).
- **Key UI files read:** `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`, `ui/components/AuraBottomBar.kt`, `app/build.gradle.kts`, `AndroidManifest.xml`.
- **Palette (hex, "desaturated cosmic tones, WCAG AA"):**
  - Primary aurora teal: light `#006A6A` / dark `#4FE3E0`; dark primaryContainer `#003E40`
  - Secondary amethyst: `#4B5E86` / `#B6C8FF` (dark container `#1F2937`)
  - Tertiary coral: `#8B4513` / `#FFB77C` (dark container `#2E1A0F`)
  - Error `#BA1A1A` / `#FFB4AB`; Success `#2E7D32` / `#81C784`
  - Surfaces: light `#F9FBFC` (onBackground `#101418`), **dark = true black `#000000` (AMOLED)**, surfaceVariant dark `#1A1F23`, onSurface dark `#E2E6E8`
  - Outline `#6F797A` / `#899394`; AccentSpectrum for heatmaps/progress: `#66D9EF, #8AB4F8, #E1BEE7, #FF8A80, #FFD180, #A5D6A7`
- **Theme pattern:** full `lightColorScheme`/`darkColorScheme` mapping every M3 role incl. containers; dynamic color (Material You) wins on Android 12+ with the brand scheme as fallback; `outlineVariant = outline.copy(alpha = 0.4f)`.
- **Typography:** complete `Typography()` override with explicit size/lineHeight/letterSpacing per role (display 48→11sp); conceptual pairing Sora (display) + Inter (UI) + Plex Mono (technical), substituted to SansSerif/Monospace to avoid the Google Fonts dependency. Dedicated `MonoTextStyle` (12sp mono) for technical text.
- **Nav structure:** Material 3 `NavigationBar` bottom bar (`AuraBottomBar`) driven by a `TopLevelDestination` enum (selected/unselected icon pairs, `stringResource` labels, `alwaysShowLabel = true`), containerColor = surface, `tonalElevation = 0.dp`. Plus an `IndexingProgressStrip`: thin strip in `secondaryContainer` with an 8dp primary dot + label, shown during background work.
- **Reusable components:** `EmptyState`, `PermissionGate`, `PhotoTile`, `PhotoViewer`, `AuraBottomBar` + progress strip; `BadgedBox`/`Badge` on nav icons.
- **Manifest polish:** single `MainActivity` (`singleTop`), dedicated splash theme, `windowSoftInputMode="adjustResize"`, `enableOnBackInvokedCallback`, `hardwareAccelerated`, scoped-storage media permissions with `READ_MEDIA_VISUAL_USER_SELECTED` + `maxSdkVersion="32"` legacy fallback, WorkManager deferred init, FileProvider, deep link `auragallery://photo/{id}`, biometric vault permission.
- **Copy:** the whole theme triple (Color/Type/Theme) as a template; AMOLED true-black dark scheme; enum-driven bottom bar; progress strip; EmptyState/PermissionGate.
- **Avoid:** `isMinifyEnabled = false` in release (justified only by weak CI RAM — bad default for Play), debug-keystore release signing fallback, `largeHeap="true"`.

---

## Repo 2 — ansaribilal14/zerium-browser
- **Language:** Java (View/XML) + Kotlin for embedded Compose screens · **UI framework:** Android View/Material3 XML (Theme.Material3.DayNight.NoActionBar) **plus** Jetpack Compose M3 for Downloads/Media grabber/Appearance screens; GeckoView engine.
- **Build:** Groovy DSL, compileSdk/targetSdk 34, minSdk 26, v1.15.0, compose compiler ext 1.5.14, BOM 2024.09.03, appcompat 1.7.0, material 1.12.0, RecyclerView, SwipeRefreshLayout. JVM 17.
- **Key UI files read:** `ui/ZeriumTheme.kt`, `res/values/colors.xml`, `res/values/themes.xml`, `res/values-night/colors.xml`, `app/build.gradle`, `AndroidManifest.xml`.
- **Palette (indigo seed, full M3 tonal role set):**
  - Light: primary `#4355B9`, onPrimary `#FFFFFF`, primaryContainer `#DEE1FF`, onPrimaryContainer `#001159`; secondary `#595D72`/container `#DEE1F9`; tertiary `#77546F`/container `#FFD7F1`; background/surface `#FBF8FF`, onSurface `#1B1B21`; surfaceVariant `#E3E1EC`; surface container ladder lowest→highest: `#FFFFFF, #F5F3FA, #EFEDF5, #E9E7EF, #E4E2E9`; outline `#767680`, outlineVariant `#C7C5D0`
  - Night: primary `#BAC3FF`, onPrimary `#253380`, primaryContainer `#3C4A9E`, onPrimaryContainer `#DEE1FF`; secondaryContainer `#414659`
  - Status bar & navigation bar colored `surface_container` in themes.xml.
- **Theme pattern:** identical palette maintained twice — XML `colors.xml` + `values-night/colors.xml` for View screens, and the same values in `ZeriumTheme.kt` Compose schemes with dynamic color on S+. Comment: "tonal surfaces, generous rounded corners, calm elevation-less cards."
- **Icon handling:** `ZIcon(res, size = 22.dp, tint)` — ships **tint-free `_c` vector variants** and applies `ColorFilter.tint()` in Compose (theme-attribute tints in vector XML are a View-only concept). 22dp default icon size.
- **Nav structure:** multi-activity (`MainActivity` singleTask + Settings/Bookmarks/History/Downloads/Appearance activities) + Compose screens inside those activities; `MenuSheet` bottom-sheet, RecyclerView adapters (`TabsAdapter`, `OmniboxSuggestAdapter`).
- **Media/subtitle relevance:** full download manager (`DownloadEngine`, HLS downloader, media grabber, `CategoryResolver`), `YouTubeFilter`, `ReaderSupport` — good domain model for a media-utility app's download/export flows.
- **Copy:** the M3 tonal-palette-in-XML + `values-night` discipline; the 5-step `surface_container` ladder; status/nav bar theming; the `_c` tint-free icon + `ColorFilter.tint` trick; download-engine architecture.
- **Avoid:** maintaining the palette in two places by hand (drift risk); multi-activity nav for a new Compose app; committed keystore fallback password (`"zerium"`) in build.gradle; `lint { abortOnError false }`.

---

## Repo 3 — superz1402/nexpdf
- **Language:** Kotlin · **UI framework:** pure Jetpack Compose + Material 3 (BOM 2025.12.00, material-icons-extended), no navigation library, no View XML.
- **Build:** compileSdk 36, targetSdk 35, minSdk 26, `pdfbox-android` 2.0.27.0, activity-compose 1.10.1. Release minified (R8 on).
- **Key UI files read:** `ui/theme/Theme.kt`, `ui/theme/Motion.kt`, `ui/AppRoot.kt`, `app/build.gradle.kts`, `AndroidManifest.xml` (+ file list of screens: Home/Tools/Organizer/Annotate/Viewer, `ui/common/Skeleton.kt`).
- **Palette ("Ember" warm orange seed):** Ember `#FF8956`, EmberDim `#8C4A27`.
  - Light: primary `#8F4C25`, onPrimary `#FFFFFF`, primaryContainer `#FFDBCB`, onPrimaryContainer `#371000`, surface `#FFF8F6`, surfaceContainer `#FCEEE8`, surfaceContainerHigh `#F7E8E2`, surfaceVariant `#F4DED5`, onSurfaceVariant `#52443D`
  - Dark: primary `#FFB68D`, onPrimary `#531F00`, primaryContainer `#703600`, surface `#201A17`, onSurface `#ECE0DA`, surfaceContainer `#261E1A`, surfaceContainerHigh `#312824`, onSurfaceVariant `#D7C3B9`
- **Motion system (`NexMotion`) — the standout artifact:** durations QUICK 120ms / STANDARD 220ms / EMPHASIZED 320ms; easings `EmphasizedDecelerate = CubicBezier(0.05, 0.7, 0.1, 1.0)`, `EmphasizedAccelerate = CubicBezier(0.3, 0, 0.8, 0.15)`, `StandardEase = CubicBezier(0.2, 0, 0, 1.0)`; `expressiveSpring()` = `spring(dampingRatio = LowBouncy, stiffness = MediumLow)`.
- **Nav structure:** hand-rolled string route state machine in `AppRoot` — `rememberSaveable { mutableStateOf("home") }`, routes like `"viewer:<uri>"`, modes home/viewer/annotate/tools/organizer; external PDF open via `ACTION_VIEW` intent-filter handled as an override mode.
- **Manifest:** `android:theme="@android:style/Theme.Material.Light.NoActionBar"` on `<application>` (not M3, not DayNight — see pitfalls), `configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode"` handled manually, `adjustResize`, VIEW intent-filter for `application/pdf`.
- **Copy:** `NexMotion` verbatim as a Momo motion token file; the Skeleton loading component; tight focused screens per task.
- **Avoid:** string-route state machine (fine for 5 screens, won't scale for a subtitle editor with back-stack + argument passing — use Navigation-Compose); non-DayNight base theme (light status-bar flash in dark mode); no values-night resource set at all.

---

## Repo 4 — superz1402/linuxify
- **Language:** Kotlin · **UI framework:** Jetpack Compose + Material 3 (BOM 2024.12.01), **dark-first design**.
- **Build:** compileSdk 35, minSdk 26, **targetSdk 28** (deliberate + documented: Android 10+ blocks executing bundled binaries in app-private storage — a functional constraint, not laziness).
- **Key UI files read:** `ui/theme/Color.kt`, `ui/theme/Type.kt`, `ui/theme/Theme.kt` (+ screen list: Home/Setup/Terminal/Files/Software/Services/Projects/Settings, `ui/components/Common.kt`, shared `AppViewModel`).
- **Palette ("restrained technical palette"):**
  - Dark: background `#0E1116`, surface `#151A21`, surfaceHigh `#1C232C`, surfaceHighest `#242D38`, onBackground `#E3E7EC`, onSurface `#D6DCE3`, onSurfaceDim `#8A93A6`, outline `#303945`, primary terminal-cyan `#5CC8D7`, onPrimary `#06222A`, primaryContainer `#10393F`, onPrimaryContainer `#B8ECF4`
  - Light: background `#FAFBFC`, surface `#FFFFFF`, surfaceHigh `#F0F2F5`, onBackground `#1A1D22`, primary `#0E7C8C`, primaryContainer `#D3F1F6`, onPrimaryContainer `#06363E`
  - **Semantic status colors, fixed hues across both themes, explicitly "never color-only: always paired with label":** success `#4ADE80`/`#15803D`, warning `#FBBF24`/`#B45309`, error `#F87171`/`#B91C1C`, info `#60A5FA`/`#1D4ED8`, neutral `#8A93A6`/`#5C6672`.
- **Typography:** platform sans for UI + **Monospace for technical values** (paths, sizes, PIDs, ports) — `MonoSmall` 12/16, `MonoBody` 13/18, `MonoLabel` 11/14 medium. "Deliberate developer-tool identity."
- **Theme pattern:** simple dark/light schemes with `surfaceContainer` tiers mapped to explicit ramp values; status colors feed `error` and custom status chips.
- **Copy:** the elevated-surface dark ramp (`#0E1116 → #151A21 → #1C232C → #242D38`) — exactly what a video/subtitle dark UI needs for player chrome, sheets, and dialogs; the fixed semantic status palette with labels; mono styles for timestamps/codecs.
- **Avoid:** targeting old SDKs without cause; default `Typography()` in Type.kt (they under-used their own token idea).

---

## Lessons for Momo Subtitle UI (10+ concrete recommendations)

1. **Stack:** single-activity Kotlin + Jetpack Compose + Material 3, minSdk 26, targetSdk 35, JVM 17 — this is the consistent toolchain across all three users' newest apps (AuraGallery, nexpdf, linuxify). Use a version catalog + Compose BOM (nexpdf uses BOM 2025.12.00).
2. **Palette strategy:** pick ONE seed hue and map the full M3 role set (primary/primaryContainer/onPrimary/onPrimaryContainer + surfaceContainer ladder) for light AND dark. Proven seeds from their apps: indigo `#4355B9` (light) / `#BAC3FF` (dark) [zerium], terminal cyan `#5CC8D7` / `#0E7C8C` [linuxify], ember `#FF8956`/`#8F4C25` [nexpdf], aurora teal `#006A6A`/`#4FE3E0` [AuraGallery]. For a media app, recommend **indigo seed** (reads "cinema/streaming") with cyan reserved for accent/progress.
3. **Dark theme is the primary theme** for a video/subtitle utility. Use linuxify's elevated dark ramp for surfaces: `#0E1116` bg → `#151A21` → `#1C232C` → `#242D38` (sheets/dialogs/player chrome), onSurface `#D6DCE3`, dim text `#8A93A6`. Offer a **pure-AMOLED black `#000000`** variant like AuraGallery's dark surface for OLED battery + cinema feel.
4. **Dynamic color fallback:** all three Compose apps enable `dynamicColorScheme` on Android 12+ and fall back to the brand scheme — copy this exact `when { dynamicColor && SDK >= S ... }` guard so Material You never wipes the brand on Pixels.
5. **Typography contract:** write a complete `Typography()` with explicit sp/lineHeight/letterSpacing for every role (AuraGallery's Type.kt is a perfect template), and add **Monospace styles (12–13sp) for timestamps, codecs, file sizes, and subtitle cue counters** — the linuxify/nexpdf "technical values in mono" identity fits a subtitle tool perfectly.
6. **Motion tokens file:** create `MomoMotion` modeled on nexpdf's `NexMotion` — QUICK 120ms / STANDARD 220ms / EMPHASIZED 320ms, `CubicBezier(0.05,0.7,0.1,1.0)` decelerate, `CubicBezier(0.2,0,0,1.0)` standard, low-bouncy spring for chips/FABs. Apply to sheet reveals, tab switches, and export progress transitions.
7. **Nav structure:** M3 `NavigationBar` bottom bar driven by a `TopLevelDestination` **enum** (selected/unselected icon pair + `stringResource` label), 4–5 destinations (e.g., Projects, Subtitle Editor, Tools, Settings), `containerColor = surface`, `tonalElevation = 0.dp` (AuraGallery pattern). Use Navigation-Compose below it — do NOT copy zerium's multi-activity split or nexpdf's string-route state machine.
8. **Long-running work UI:** copy AuraGallery's `IndexingProgressStrip` — a slim strip in `secondaryContainer` with an 8dp primary dot + label docked above the bottom bar — for "Transcribing… / Rendering subtitles… / Exporting…". Pair with nexpdf's `Skeleton` shimmer placeholders while files/waveforms load, and `BadgedBox` badges on nav items.
9. **Reusable component kit:** implement `EmptyState` (illustration + headline + body + CTA), `PermissionGate` (READ_MEDIA_VIDEO / POST_NOTIFICATIONS with rationale), and status chips using **fixed semantic colors across themes, always paired with a text label, never color-only** (linuxify: success `#4ADE80`/`#15803D`, warning `#FBBF24`/`#B45309`, error `#F87171`/`#B91C1C`, info `#60A5FA`/`#1D4ED8`).
10. **Icons:** use material-icons-extended (nexpdf) or the zerium trick — tint-free `_c` vector variants tinted at composition time via `ColorFilter.tint()` with a default 22dp size; never hard-tint vector XMLs if you also run Compose.
11. **Manifest polish for a media app:** splash-screen theme on the launcher activity, `singleTop`/`singleTask` MainActivity, `windowSoftInputMode="adjustResize"`, `enableOnBackInvokedCallback`, `hardwareAccelerated`, `ACTION_VIEW` intent-filters for video/subtitle MIME types (nexpdf's PDF VIEW filter is the template), FileProvider for sharing exported `.srt/.vtt`, scoped-storage media permissions with `maxSdkVersion="32"` fallback (AuraGallery).
12. **Pitfalls seen in their code (do not repeat):** release builds with `isMinifyEnabled=false` (AuraGallery, zerium) — enable R8; keystore passwords/keystores committed or defaulted in build.gradle (zerium `"zerium"`, linuxify `keystore/` dir) — use env vars with no fallback; zerium's palette duplicated in XML + Compose (drift risk) — define colors once in Compose and generate/mirror, or vice versa; nexpdf's non-DayNight `Theme.Material.Light.NoActionBar` (light chrome flash in dark mode) — use a proper splash/DayNight base; AuraGallery's `largeHeap="true"` — avoid.

**Report file:** `/home/z/my-project/_research/UI_ANALYSIS.md` · **Cloned repos:** `/home/z/my-project/_research/repos/{bilal140202__AuraGallery, ansaribilal14__zerium-browser, superz1402__nexpdf, superz1402__linuxify}` (+ rejected clones kept for reference: momo-1.0, masthead, privacy-think, open-video-engine, antigravity-skill-recorder).
