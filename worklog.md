# Momo Subtitle — worklog

Rules (AGENTS.md §2): this file is updated after EVERY task, with IST
timestamps, issue references, results, and an explicit "Next" item. A fresh
session resumes from the last section of this file.

---

## Session 1 — 2026-11-04 (Asia/Kolkata)

### 10:05 — [#2][#3] Toolchain + third-party pins
- Installed on-server: Android cmdline-tools, platform-35, build-tools 35.0.0,
  NDK 27.2.12479018, CMake 3.22.1, Gradle 8.11.1 (`/home/z/my-project/sdk`,
  `/home/z/my-project/gradle-8.11.1`).
- Vendored whisper.cpp **v1.7.4** (commit `8a9ad7844d6e2a10cddf4b92de4089d7ac2b14a9`)
  into `native/whisper/whisper` (include/src/ggml/LICENSE only, 7.3 MB).
- Fetched REAL model sizes + SHA-256 from HuggingFace API for tiny/base/small
  Q5_1 → hardcoded in `core/models/ModelCatalog.kt`.
- Learned: background processes do NOT survive between tool calls on this
  server → all installs/builds must run in foreground long calls.

### 11:00 — [#1][#2][#3][#4][#5][#6][#7][#8] Milestone 0.1 code
- Repo skeleton: 18 Gradle modules (app, 10 core incl. core/ui, 7 feature),
  version catalog with pinned versions, CI workflow (build + tests + release).
- Native: `native/whisper/CMakeLists.txt` (CPU backend defines mirrored from
  ggml CMake: GGML_USE_CPU, GGML_USE_CPU_AARCH64, GGML_SCHED_MAX_COPIES=4) and
  `jni/momo_jni.c` (strict 16k mono WAV loader, whisper_full with
  no_context=true per chunk, JSON segments output, centisecond→ms conversion).
- Core: MediaCodec→16 kHz mono WAV extractor with linear resampler (streamed,
  bounded RAM); WAV slicer that reads only the needed byte range; WhisperEngine
  (model opened once per job); ChunkPlanner + TranscriptionJobManager with
  per-chunk Room checkpoints, resume, PARTIAL status for failed chunks (no
  silent data loss); ModelRepository (Range-resume .part downloads, second-pass
  SHA-256 verify); CueBuilder/SRT/VTT/TXT with 6 unit tests; Room schema
  (projects/runs/chunks/segments); DataStore settings.
- UI: dark-first M3 theme (ramp from the operator's repos, cyan/indigo accents,
  mono timestamps), bottom nav (Home/Models/Settings), import flow (SAF, mode/
  language/chunk/model selection), processing dashboard (slim progress strip,
  per-chunk stats, pause/resume/cancel, SRT/VTT/TXT export via SAF), model
  manager (download progress, set active, delete), settings (threads, chunk,
  device info, storage), editor read-only preview (Phase 3 placeholder).
- Docs: full set written (ARCHITECTURE, PROCESSING_PIPELINE, MODEL_STRATEGY,
  TEST_PLAN, DESIGN_SYSTEM, UI_REFERENCE, RELEASE_CHECKLIST) + AGENTS.md
  (operating contract incl. operator's session rules) + this worklog.

### 11:35 — [#9] Local build green, APK produced
- Fixed on the way (all committed):
  - system java was JRE-only → installed Temurin JDK 17 under
    `/home/z/my-project/jdk17`; setup_sdk.sh now ensures it for future sessions.
  - KDoc containing `whisper/*.bin` opened a nested Kotlin comment (syntax
    error) → reworded.
  - MediaExtractor `.use` unavailable → explicit try/finally release.
  - `json` name shadowing in WhisperEngine → `jsonFormat`.
  - DataStore `Preferences` leaked through SettingsRepository's public API
    (inferred return types + public keys) → explicit Unit setters, private
    companion; consumers no longer need the DataStore dependency.
  - activity-compose dependency added to feature/import + feature/processing.
  - Memory tuning for the 4 GB server: parallel=false, in-process Kotlin
    compilation, 1280 m daemon heap, MaxMetaspace 384 m.
- Result: `:app:assembleDebug` BUILD SUCCESSFUL; `:core:subtitles:test` 9/9
  passing; APK 15.4 MB with libmomo_whisper_jni.so for arm64-v8a + x86_64;
  aapt2 badging verified (com.momosubtitle.app, minSdk 26, target 35).
- Tag `v0.1.0-m0.1` + GitHub Release with APK asset; APK + source ZIP copied
  to `/home/z/my-project/download/`.

### Next
- Device QA pass on the Moto G85 5G (docs/TEST_PLAN.md matrix) — needs the
  physical device.
- Phase 1: overlap merging, FFmpeg extraction, bilingual export.
- Scheduled autosave/health agent job armed (every 30 min).

---

## Session 2 — 2026-11-08 (Asia/Kolkata)

### 10:05 — Server wiped again; full recovery
- `/home/z/my-project` was reset by the platform: repo, JDK, SDK all gone.
- Re-cloned `superz1402/momo-subtitle` from GitHub (HEAD `067b3af` — nothing
  lost; the "commit everything" rule paid off). Healthcheck cron handled the
  clone + dropped a file-mode-noise checkpoint via `core.fileMode=false`.
- git credentials re-provisioned via `git credential store` (token kept in
  `~/.git-credentials`, OUTSIDE the repo).

### 10:20 — [#10][#11][#12] Operator request: background + Play-ready UI + releases
- Created GitHub issues #10 (foreground service + notification progress),
  #11 (UI rehaul), #12 (release v0.1.1).
- Rebuilt toolchain with new idempotent `scripts/setup_sdk.sh` (now committed
  in-repo): Temurin JDK 17.0.21, cmdline-tools, platform-35, build-tools
  35.0.0, NDK 27.2.12479018, CMake 3.22.1.
- UI study (issue #11 prep): cloned owner repos (Your-Files-Redesign, nexpdf,
  tabit-android, HeyYou) and extracted design tokens + 12 actionable rules;
  stored patterns in the design report (dark-first, 16dp cards, alpha-tinted
  icon tiles, splash on 31+, edge-to-edge, no device-info clutter).

### 11:55 — [#10] Foreground service shipped
- `TranscriptionService` (dataSync FGS): progress notification with per-chunk
  %, stage text, Pause/Resume/Cancel actions, terminal "Subtitles ready"
  notification; channel IMPORTANCE_LOW, silent, onlyAlertOnce.
- Entry points installed via `AppServicesBridge.TranscriptionRunner` so
  feature modules and the notification share one control path (ImportScreen
  starts it; ProcessingScreen routes pause/resume/cancel through it).
- POST_NOTIFICATIONS runtime request on first import (API 33+).
- Manifest: FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC +
  POST_NOTIFICATIONS; service registered `exported=false`.

### 12:30 — [#11] UI rehaul shipped
- `core/ui`: Spacing/Radius/Motion tokens; M3 Shapes (10/16/20dp); real
  Typography (weight-contrast, tightened display tracking); shared
  EmptyStateView / SectionLabel / MomoBackHeader.
- App shell: edge-to-edge cleaned theme XML (no pinned bar colors), API 31+
  splash (brand bg + launcher icon).
- Home: stat tiles + hero CTA + EmptyStateView; model/mode mono clutter moved
  off card titles. Models: letter tiles, delete confirm dialog, `refresh:`
  debug leak removed. Processing: hero percent + animated bar +
  Extract→Transcribe→Build stepper. Import: drop-zone sheet + file card.
  Settings: Device card (ABI/cores/systemInfo) removed. Editor: "Planned
  capabilities" roadmap card removed. Nav: Models tab uses Build icon.

### 12:40 — [#12] Build green, tests green, release published
- Build resilience: Maven Central 429-rate-limits the server → JetBrains
  cache-redirector + Aliyun mirrors first in settings.gradle.kts; daemon heap
  1536m + workers.max=2 for 4 GB hosts.
- Compile fixes on the way: feature/import missing Surface import + core:ui
  ref inlined; feature/models missing remember import; :app missing Build
  icon import.
- `:app:assembleDebug` BUILD SUCCESSFUL (349 tasks) — APK 15.4 MB, badging
  verified (com.momosubtitle.app, v0.1.1, vc2, FGS perms, service with
  dataSync type, libmomo_whisper_jni.so arm64+x86_64).
- `test` all modules: 2 suites, 9/9 passing.
- Committed (the 12:31 cron checkpoint b497194 swept mid-edit work; follow-up
  7e52d78 adds the last fixes), pushed, tagged **v0.1.1**, GitHub Release
  438239382 with `momo-subtitle-v0.1.1-debug.apk` asset. APK also copied to
  `/home/z/my-project/download/`.

### Next
- Device QA on the Moto G85 5G (docs/TEST_PLAN.md): notification behavior
  under Doze, backgrounded 1 h+ run, RTF/thermals — needs physical device.
- Issue #10 follow-up: service restart-after-process-death (START_STICKY +
  auto-resume from checkpoint) if QA shows kills.
- Phase 1 features (overlap merging, FFmpeg extraction, bilingual export) —
  start only on operator go-ahead.
- Rotate the GitHub PAT pasted in chat (shared in plaintext).

## 2026-11-09 01:30 — RECOVERY: v0.2.0 commits lost in sandbox recycle; push unblocked

- Operator re-supplied the GitHub PAT; credential store now lives outside the
  repo (`/home/z/my-project/.secrets/git-credentials`) and `git ls-remote`
  + Releases API auth are verified working.
- **Data loss:** the v0.2.0 work from the 2026-11-08 15:30 session was never
  pushed (push then failed: no credentials) and that sandbox was recycled and
  re-cloned — commits b3d0fb9 + e51b744, the in-repo keystore, the signed
  v0.2.0 APKs and docs/ISSUES_DRAFT.md are all gone. Origin still has only
  v0.1.1 (fef2649); no v0.2.0 branch/tag/release exists on GitHub (checked).
- Lost work to rebuild (all was green: tests 9/9, release 12.0 MB):
  1. Background model download (WorkManager + FGS, notification progress)
  2. POST_NOTIFICATIONS runtime permission flow
  3. Download retry w/ backoff + HTTP Range resume (fixes "software caused
     connection abort")
  4. Work isolation for downloads vs transcription
  5. Taste-rule UI polish; same-key signing (keystore committed in-repo,
     versionCode 3, v0.2.0); issues #13–#16; tag v0.2.0 + Release.
- **Lesson enforced from now on:** push after EVERY completed task — never
  let finished work sit unpushed across sessions.
- Next: rebuild v0.2.0 feature set in a working session (build → test →
  commit → PUSH each step), then tag v0.2.0 and publish the Release.
- Reminder: rotate the PAT (same one pasted in plaintext again).

## 2026-11-09 02:15 — v0.2.0 REBUILT and RELEASED (issues #13–#16 closed)

- The lost 2026-11-08 v0.2.0 work was fully rebuilt and shipped:
  - **#13** `ModelDownloadService` (dataSync FGS): notification progress
    (%, MB, ETA, 500 ms throttle), Cancel action, terminal success/error,
    `START_REDELIVER_INTENT` auto-resume; `AppServicesBridge.ModelDownloadRunner`
    entry points; `ModelRepository.shared()` single instance shared by
    screen + service; POST_NOTIFICATIONS rationale dialog on Models screen
    ("Not now" still downloads).
  - **#14** `ModelRepository.download()`: 6 attempts, backoff 1/2/4/8/15/30 s
    + jitter, HTTP Range resume from `.part`, `Accept-Encoding: identity`,
    HTTP 416 → restart, friendly error strings, 512 KB emission throttle,
    speed B/s → ETA.
  - **#15** downloads run in the service's scope (repo I/O on Dispatchers.IO),
    UI only intents + StateFlow; both long jobs are dataSync FGS.
  - **#16** in-repo keystore (`keystore/momo-release.jks` + `momo.properties`),
    `momo` signingConfig on release AND debug; cert SHA-256
    `e9f67c10…be4e08` verified identical on both APKs via apksigner.
- Build green: `:app:assembleDebug :app:assembleRelease test` BUILD
  SUCCESSFUL; tests 9/9. Badging: vc3, v0.2.0, both services dataSync,
  POST_NOTIFICATIONS + FGS perms. Release APK 12.0 MB, debug 15.5 MB.
- Build fix on the way: gradle.properties metaspace 384m→768m (OOM: Metaspace
  with in-process Kotlin compiler on 4 GB host).
- Pushed every step this time (lesson enforced): 5c37e1e (wip) → 1c670bc
  (v0.2.0) → tag v0.2.0. Release 438427677 published with both APKs; issues
  #13–#16 closed via commit message + API.
- One-time operator note: uninstall v0.1.1 before installing v0.2.0 (old key
  was lost); v0.2.0 onward updates install over each other.
- Next: Moto G85 device QA (docs/TEST_PLAN.md — download through Doze +
  Wi-Fi-drop retry, notification behavior, RTF/thermals); START_STICKY
  follow-up only if QA shows kills; Phase 1 only on operator go-ahead;
  rotate the PAT (same one again pasted in plaintext).
