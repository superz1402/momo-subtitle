# AGENTS.md — Operating Contract for AI Agents on Momo Subtitle

This file is the standing instruction set for every AI agent session (human or
automated) that works on this repository. It combines the product blueprint's
engineering rules with the operator's session-management rules. **Read this
file completely before doing any work.**

---

## 1. Mission

Momo Subtitle is a standalone, offline-first Android application that converts
long-form foreign-language interviews into accurate, synchronized English
subtitles using whisper.cpp on-device. No cloud transcription, no API keys, no
accounts, no subscriptions. The current phase is **Milestone 0.1** (native
engine proof) — see the roadmap in README.md and docs/ARCHITECTURE.md.

## 2. Session persistence rules (NON-NEGOTIABLE)

The development server is **session-sensitive and will be wiped**. Local
progress does not exist unless it is in Git.

1. **Git-first.** Commit and push at the end of EVERY task, and unconditionally
   before ANY session ends — even mid-feature. An incomplete but pushed state
   beats a complete but wiped one.
2. **Never trust the sandbox.** `scripts/autosave.sh` performs a checkpoint
   commit+push; run it liberally. The scheduled autosave agent does the same.
3. **GitHub Issues are the TODO system.** Every non-trivial task MUST:
   - be created as an issue first (title `[Mx.y] task`, body with acceptance
     criteria),
   - be implemented,
   - be closed by its implementation commit (`Closes #N` in the message),
   - never be closed without the work actually existing in Git.
4. **worklog.md is updated after every task** — what was done, issue ref,
   result, next step, with IST timestamps. An agent that finishes work without
   updating worklog.md has not finished the work.
5. **Session restart recovery protocol.** On starting a fresh session:
   1. `bash scripts/resume.sh` (prints git status, recent commits, worklog tail).
   2. Read the LAST section of worklog.md — continue from its "Next" item.
   3. Check open issues on GitHub — they are the authoritative task list.
   4. If a build was interrupted, re-run it; Gradle caches make this cheap.
   5. Commit+push any uncommitted state before starting new work.

## 3. Build policy

1. **Build locally on the server first.** The Android SDK/NDK/CMake live under
   `/home/z/my-project/sdk` (installed by `/home/z/my-project/scripts/setup_sdk.sh`),
   Gradle 8.11.1 at `/home/z/my-project/gradle-8.11.1`. Set:
   `export ANDROID_HOME=/home/z/my-project/sdk GRADLE_USER_HOME=/home/z/my-project/.gradle-home`
2. **GitHub Actions is the fallback**, not the default. If a local build cannot
   proceed (toolchain failure, disk exhaustion), push and let
   `.github/workflows/android-build.yml` build the APK, then download the
   artifact via the API.
3. Pin every version in `gradle/libs.versions.toml`. Never float dependencies.
4. **Never commit model weights** (`*.bin`, `*.gguf`, `*.onnx` are gitignored)
   or APKs. Releases go to GitHub Releases, tagged `v*`.
5. Keep native code isolated under `native/whisper`; the JNI bridge is the only
   seam between Kotlin and C.

## 4. UI study mandate

UI is a known weak point. **Before designing or changing UI**, analyze the
operator's public repos to learn their established patterns:

- https://github.com/bilal140202 (e.g. AuraGallery — Compose AI gallery)
- https://github.com/ansaribilal14 (e.g. zerium-browser — GeckoView + Compose)
- https://github.com/superz1402 (e.g. nexpdf, linuxify — Compose utilities)

Distilled findings already live in `docs/UI_REFERENCE.md` and
`docs/DESIGN_SYSTEM.md` — follow those documents. When introducing new UI,
match the existing M3 dark-first system instead of inventing new styles.

## 5. Security rules

1. **Never commit credentials.** Tokens/keys stay outside the repo (the
   operator's token lives in `/home/z/my-project/.secrets/`, referenced only
   via `GIT_ASKPASS` / curl header files — never echoed into logs, code, or
   docs).
2. If a credential was ever pasted in chat, advise rotating it after use.
3. The app itself must never embed secrets: model downloads use public URLs.

## 6. Engineering rules (from the product blueprint)

1. Every completed feature is committed to GitHub.
2. Build and test before merging; never leave main red at session close if it
   was green before.
3. Do not place model weights in Git.
4. Pin dependency versions.
5. Maintain a reproducible release build (tagged releases carry APKs).
6. Add GitHub Issues for incomplete work — no silent TODOs.
7. Maintain the changelog via tagged releases.
8. Engine-first development: prove the native pipeline before investing in UI.
9. Never silently discard unprocessed audio; every chunk is checkpointed.
10. Do not promise device results that have not been benchmarked; record RTF.

## 7. Commit conventions

- Conventional Commits: `feat(scope):`, `fix(scope):`, `docs:`, `chore:`.
- Reference issues: `Closes #N` (one issue per line or comma list).
- Keep commits logical (module-level), not monolithic dumps.
- The worklog update may ride along with its task's commit.

## 8. Repository map

```
app/            MainActivity, navigation, DI wiring
core/           common (contracts, Graph), database (Room), media (extract/WAV),
                models (catalog/downloader), processing (pipeline), speech (JNI),
                storage (paths/SAF), subtitles (Cue/SRT/VTT), translation (modes)
feature/        home, import, models, processing, export, editor, settings
native/whisper  vendored whisper.cpp v1.7.4 + CMake + JNI
benchmark/      RTF/RAM/thermal methodology + reports (no audio in Git)
docs/           ARCHITECTURE, PROCESSING_PIPELINE, MODEL_STRATEGY, TEST_PLAN,
                DESIGN_SYSTEM, UI_REFERENCE, RELEASE_CHECKLIST
scripts/        agent tooling: autosave.sh, resume.sh
```
