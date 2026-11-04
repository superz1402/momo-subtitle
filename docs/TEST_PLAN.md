# Test plan

## Unit tests (run in CI: `./gradlew :core:subtitles:test`)

- `CueBuilderTest` — line wrapping ≤42 chars, ≤2 lines, minimum duration,
  overlap clamping, micro-caption merging, blank-segment dropping.
- `SrtFormatterTest` — `00:00:02,100` comma-milliseconds formatting, WebVTT
  dot-milliseconds + header, chronological numbered blocks.

## Milestone acceptance matrix (blueprint §13)

| Test | Acceptance criteria | Status |
| ---- | ------------------- | ------ |
| Korean interview | English subtitles generated | device QA |
| Mandarin interview | English subtitles generated | device QA |
| Thai interview | English subtitles generated | device QA |
| Japanese interview | English subtitles generated | device QA |
| Korean + English | Language switching preserved (`auto` per chunk) | device QA |
| Multiple speakers | Dialogue retained; speaker labels are Phase 5 | partial |
| Background music | Recognition evaluated, reported in benchmark | device QA |
| Two-hour interview | Complete processing + recovery | device QA |
| App force-stop | Resume from saved chunk | device QA |
| Screen locked | Background behavior verified (FGS in Phase 2) | known limitation |
| Low storage | Graceful error, no silent data loss | code review |
| SRT export | Valid syntax, chronological timestamps | unit-tested |
| VTT export | Valid WebVTT format | unit-tested |
| Offline mode | No network dependency during processing | by design (INTERNET only for downloads) |

## Quality metrics (measured separately — never one blended accuracy number)

- WER for original-language transcription, per language.
- Translation quality via human-reviewed bilingual samples.
- Timestamp alignment error.
- Subtitle reading speed (CPS) distribution.
- Processing speed as RTF (0.5 = 60 min audio in ~30 min).
- Peak RSS + thermal behavior (`benchmark/` methodology).

## Device QA loop

1. `adb install app-debug.apk`.
2. Download Base multilingual Q5_1 in-app (verify SHA-256 toast/log).
3. Import test media, run, export SRT; validate syntax with `srt` tooling or
   manual inspection.
4. Kill the process mid-run; reopen; resume; confirm chunk skip counts.
5. Record RTF + RAM into `benchmark/reports/`.
