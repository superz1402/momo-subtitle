# Momo Subtitle

**Your local subtitle workspace.** A standalone Android app that turns
long-form foreign-language interviews into accurate, synchronized English
subtitles — entirely on-device, powered by [whisper.cpp](https://github.com/ggml-org/whisper.cpp).

> Import anything you are authorized to process, understand every spoken
> language, produce natural English subtitles, correct them, and export a
> perfectly synchronized subtitle file.

- No account · no API keys · no subscription · no cloud transcription
- Internet is used **only** to download model weights; processing is offline
- Built for the Moto G85 5G and comparable Android devices (arm64, Android 8+)

## Status: Milestone 0.1 — native engine proof ✅

Offline Whisper transcription → English SRT, with chunked processing,
checkpoint recovery, a model manager (SHA-256 verified downloads with resume),
and a dark Material 3 UI. The subtitle editing studio lands in Phase 3.

| Phase | Deliverable |
| ----- | ----------- |
| 0.1 ✅ | whisper.cpp + JNI on Android, SRT generation, model manager, chunked pipeline |
| 1 | Full video pipeline: FFmpeg extraction, overlap merging, bilingual export |
| 2 | Full app: project management, notifications, background service, YouTube authorized-source import |
| 3 | Subtitle studio: video preview, editing, undo/redo, timing tools |
| 4 | Translation quality: local NMT experiments, alignment, mixed-language tuning |
| 5 | Pro: diarization, waveform editing, subtitle burn-in, transcript search |

## Build

Requirements: JDK 17+, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1.

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or open in Android Studio and press Run. CI builds every push
(`.github/workflows/android-build.yml`) and attaches APKs to `v*` releases.

## First run

1. Install the APK and open **Models**.
2. Download **Base multilingual Q5_1** (57 MB, SHA-256 verified, resumable).
3. Import an audio/video file, choose output mode (English translate or
   original transcript), start.
4. Watch per-chunk progress; pause/resume any time — completed chunks are
   checkpointed in Room.
5. Export **SRT / VTT / TXT** to any destination. Subtitles always export
   independently of the video.

## Architecture

Single repo, 18 Gradle modules, engine-first:

- `core/speech` + `native/whisper` — vendored whisper.cpp v1.7.4 behind a JNI bridge
- `core/processing` — chunk planner + checkpointed pipeline (30 s chunks, Room-backed)
- `core/models` — downloadable model catalog with digest verification
- `core/subtitles` — cue building (42 chars/2 lines/0.8–7 s) + SRT/VTT/TXT
- `feature/*` — Compose Material 3 screens (dark-first)

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) ·
[docs/PROCESSING_PIPELINE.md](docs/PROCESSING_PIPELINE.md) ·
[docs/MODEL_STRATEGY.md](docs/MODEL_STRATEGY.md)

## Licensing & acceptable use

App code: MIT. Vendored whisper.cpp: MIT (see `native/whisper/whisper/LICENSE`).
Model weights: OpenAI Whisper-derived, MIT. **Only process media you are
authorized to process.** The planned YouTube import (Phase 2) respects platform
terms — no DRM circumvention, no unrestricted downloading.

## For agents

Read [AGENTS.md](AGENTS.md) before working on this repo — it defines the
git-persistence, issue-tracking, worklog, and session-recovery rules.
