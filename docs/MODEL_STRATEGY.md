# Model strategy

## Operating modes

Whisper runs in two modes; Momo Subtitle defaults to **translate**:

| Mode | Purpose | Where |
| ---- | ------- | ----- |
| Transcribe | speech → original-language text | `mode=transcribe` |
| Translate | speech → **English** text | `mode=translate` (default) |

Whisper's translate task targets English only and does not guarantee perfect
translation, speaker separation, or word timing — the pipeline owns those
problems, not the model.

## Catalog (verified 2026-11-04 against HuggingFace `ggerganov/whisper.cpp`)

| Model | Size | SHA-256 (first 16) | Role |
| ----- | ---- | ------------------ | ---- |
| Tiny multilingual Q5_1 | 32,152,673 B | `818710568da3ca15` | Fast fallback, quick previews |
| Base multilingual Q5_1 | 59,707,625 B | `422f1ae452ade6f3` | **Default** — Moto G85 5G target |
| Small multilingual Q5_1 | 190,085,487 B | `ae85e4a935d7a567` | Accuracy mode — benchmark before defaulting |

Full digests are pinned in `core/models/ModelCatalog.kt`. Update them together
with any whisper.cpp release bump.

## Manager rules (blueprint §3)

- Weights are **never bundled in the APK and never committed to Git**.
- Downloads: streaming to `*.part`, HTTP Range resume, second-pass SHA-256
  verification; a mismatched digest is deleted, never loaded.
- Storage requirement shown before download; delete/redownload supported.
- Active model selection persisted in DataStore; the pipeline refuses to start
  without an installed model (explicit error, no silent fallback).

## Mixed-language recognition (Phase 1+ roadmap)

Interviews mixing Korean/Mandarin/Thai/English are handled chunk-wise today
(`language=auto` re-detects per chunk; detected language is reported in the
run). The full subsystem — overlapping windows, per-segment language
detection, overlap text comparison, uncertain-ID flagging — is the Phase 1/4
workstream. Never force one language for the whole video.

## Advanced translation (Phase 4 experiment)

Two-mode strategy:

1. **Fast (shipped):** Whisper built-in translate.
2. **Advanced (later):** original-language transcription + offline NMT
   (NLLB-class) evaluated for coverage, Android RAM, licensing and quality.
   **Do not assume it outperforms Whisper on a phone** — benchmark first
   (`benchmark/` methodology), and keep it out of MVP dependencies.

## Quantization note

Q5_1 keeps the Moto G85 5G inside its RAM budget while staying noticeably
better than Q2/Q3. If a future engine upgrade adds Q8_0/K-quants support on
arm64, benchmark before switching the default catalog.
