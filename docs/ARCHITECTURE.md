# Architecture

Momo Subtitle is a single Android repository with independent modules, built
MVVM + Clean Architecture style. UI, processing engine, translation engine and
native libraries stay decoupled; dependency direction always points inward
(features → contracts → engines).

```
Jetpack Compose UI (Material 3, dark-first)
        │
feature/*  home · import · models · processing · export · editor · settings
        │
core/common  contracts: JobController, RunStatus, ImportRequest, Graph, settings
        │
core/processing  pipeline orchestration (chunk planner, checkpoints)
   ├── core/media       MediaCodec → 16 kHz mono WAV (FFmpeg in Phase 1)
   ├── core/speech      WhisperEngine ── JNI ──► native/whisper (whisper.cpp 1.7.4)
   ├── core/models      catalog + resumable downloader + SHA-256 verify
   ├── core/subtitles   CueBuilder, SRT/VTT/TXT (pure JVM, unit-tested)
   ├── core/database    Room: projects / runs / chunks / segments
   └── core/storage     app-private paths, SAF helpers
```

## Module rules

- **feature modules never import :app** and never import each other except
  `feature:processing → feature:export` (SAF writers) — shared UI primitives
  live in `core/ui`.
- **`core/common` owns the contracts.** `JobController` is implemented by
  `core/processing` and installed at process start by the app (`MomoApp`), so
  features depend only on the interface.
- **`core/subtitles` is a plain JVM module** — cue logic is unit-testable
  without Android.
- **Native code is isolated** under `native/whisper`; `momo_jni.c` is the only
  seam. Kotlin calls `WhisperJni` (object with `external` methods).

## Technology choices (pinned in gradle/libs.versions.toml)

| Layer | Technology |
| ----- | ---------- |
| Language | Kotlin 2.0.21 (JVM target 17) |
| UI | Jetpack Compose + Material 3 (BOM 2024.12.01) |
| Architecture | MVVM + Clean Architecture |
| Speech recognition | whisper.cpp v1.7.4 (vendored) |
| Native integration | C / CMake 3.22.1 / NDK 27.2.12479018 |
| Video playback (Phase 3) | AndroidX Media3 1.5.0 (pinned, unused yet) |
| Audio extraction (M0.1) | platform MediaCodec/MediaExtractor |
| Audio extraction (Phase 1) | FFmpeg (replaces MediaCodec behind same API) |
| Background jobs | in-process coroutine pipeline now; FGS in Phase 2 |
| Database | Room 2.6.1 (KSP) |
| Preferences | DataStore 1.1.1 |
| File access | Storage Access Framework |
| DI | manual (`Graph` + `AppServicesBridge`) — no Hilt in M0.1 |

## Key design decisions

1. **Engine-first.** The risk is multilingual translate quality/speed on a
   phone CPU, not UI. The native pipeline is proven (Milestone 0.1) before the
   editor is built (Phase 3).
2. **One context per job.** The model loads once per run; chunk WAVs are
   sliced from disk on demand — a 2-hour file never lives in RAM.
3. **Room is the source of truth** for run/chunk/segment state. A crash at 73%
   resumes from the last completed chunk.
4. **Media3 = playback, MediaCodec/FFmpeg = extraction.** Responsibilities
   never mix.
5. **No Hilt.** 18 modules with KSP + Hilt adds build fragility; a two-object
   locator (`Graph`, `AppServicesBridge`) keeps the dependency graph explicit.

## Known limitations in Milestone 0.1

- Chunks are hard-cut at 30 s boundaries — word seams expected; overlap
  merging ships in Phase 1.
- Transcription runs in-process; switching apps on aggressive OEM builds may
  pause the job — resume recovers from checkpoints (Phase 2 adds a foreground
  service).
- armeabi-v7a is not built (Moto G85 5G is arm64; x86_64 covers emulator QA).
