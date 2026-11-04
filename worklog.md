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

### Next
- Push skeleton + code to github.com/superz1402/momo-subtitle (issues first).
- Run `./gradlew :app:assembleDebug` on-server; fix until green.
- Tag `v0.1.0-m0.1`, attach APK to GitHub Release, deliver APK + source ZIP.
- Create the scheduled autosave/health job.
