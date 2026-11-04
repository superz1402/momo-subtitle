# Processing pipeline

Every stage has explicit status, error handling, and recoverable output.

```
Import media → Media preparation → (chunked) Speech recognition → Segment
persistence → Subtitle build → Quality checks → Export
```

## Stage contract

| Stage | Run status | What happens | Recovery |
| ----- | ---------- | ------------ | -------- |
| Import | `QUEUED` | SAF URI persisted, project row created | — |
| Media preparation | `EXTRACTING` | decode → 16 kHz mono s16le WAV (`audio/extracted.wav`), duration recorded | WAV reuse: skipped if file exists |
| Chunk planning | — | `ChunkPlanner.plan(totalMs, chunkSeconds)` → `chunks/` rows | idempotent: existing chunk rows are kept |
| Recognition | `TRANSCRIBING` | per chunk: slice WAV → `whisper_full` → JSON segments → offset-corrected rows; chunk row updated (`RUNNING`/`DONE`/`FAILED`) | **checkpoint after every chunk**; `DONE` chunks are skipped on resume |
| Subtitle reconstruction | `BUILDING` | renumber segments chronologically; failed-chunk count decides `DONE` vs `PARTIAL` | cheap, re-runnable |
| Export | — | SAF create-document; `CueBuilder` + `SrtFormatter`/`VttFormatter`/`TxtFormatter` | subtitles always export independently of video |

## Chunking

- Never process a two-hour interview as one giant audio operation.
- Default chunk = 30 s (configurable 10–120 s; benchmark 30/45/60).
- Each chunk row stores: index, start/end ms, status, error info, segment count.
- Cancellation checkpoints sit **between** chunks; the in-flight chunk resets
  to `PENDING` on pause.
- M0.1 slices chunks without overlap — expect minor word seams; overlap
  merging + duplicate-word comparison lands in Phase 1 (blueprint §4).

## Timestamps

- whisper.cpp emits **centiseconds**; the JNI bridge multiplies by 10 → ms.
- Chunk-local segment times are offset by `chunk.startMs` before persistence.
- Whisper segment timestamps are NOT word-level precision — we do not fabricate
  it. Optional forced alignment is a later phase (Phase 4).
- Cue display rules: 1–2 lines, ≤42 chars/line, 0.8–7 s, no overlap, CPS
  target 17–20 (editorial defaults, configurable).

## Failure policy — never discard audio silently

- A failed chunk records its error and the run ends **`PARTIAL`** (not
  `FAILED`) when other chunks produced captions.
- `Resume` re-runs only non-`DONE` chunks (this is also the retry path).
- The pipeline cannot mark a run `DONE` with failed chunks.

## Data layout on disk

```
files/MomoSubtitle/
├── models/whisper/ggml-*.bin          (SHA-256 verified on install)
└── projects/{projectId}/
    ├── audio/extracted.wav            (16 kHz mono s16le)
    ├── chunks/chunk_NNNN.wav          (deleted after success, re-sliced on resume)
    └── exports/
```

Chunk WAVs are deliberately transient: resume re-slices from the extracted
WAV, so checkpoint recovery costs no permanent storage.
