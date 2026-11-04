# Release checklist

## Before every tagged release (`v*`)

1. `./gradlew :app:assembleDebug :core:subtitles:test` green on the pinned
   toolchain (JDK 17, AGP 8.7.3, NDK 27.2.12479018).
2. CI workflow green on main.
3. Version bump: `app/build.gradle.kts` `versionName`/`versionCode`.
4. Tag + GitHub Release with APK asset (CI release job or `gh release create`).

## Milestone 0.1 release (v0.1.0-m0.1)

- [x] Offline Whisper → English SRT workflow
- [x] Checkpoint/resume
- [x] Model manager with SHA-256 + resume
- [x] Dark M3 UI
- [x] Unit tests for subtitle core
- [ ] Device QA pass on Moto G85 5G (docs/TEST_PLAN.md matrix)

## Before public distribution (Phase 2 gate)

1. **License audit** — whisper.cpp code and model licenses audited separately;
   FFmpeg build configuration and included codecs reviewed when FFmpeg lands
   (Phase 1); third-party translation/diarization model licenses checked.
2. Third-party notices current in `LICENSE`.
3. Privacy/data-safety disclosures: all processing local except explicitly
   supported online import operations; model downloads hit HuggingFace.
4. No claim of unrestricted YouTube downloading; DRM circumvention excluded by
   design.
5. R8/minification decision: currently **disabled**. Enabling requires:
   `-keep` rules verified for `WhisperJni` (present in `proguard-rules.pro`),
   kotlinx-serialization rules tested, and a full regression pass.
6. Signing: replace debug APK distribution with a proper keystore (never commit
   the keystore or its passwords).
7. Changelog via tagged releases; AGENTS.md engineering rules re-verified.
