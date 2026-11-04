# Benchmark corpus

`audio/` holds locally recorded or properly licensed interview samples used to
measure RTF (real-time factor), RAM and thermal behavior on the Moto G85 5G.
Audio is never committed to Git (.gitignore enforces this).

`expected/` holds hand-verified reference transcripts (`*.txt`) and reference
SRT files for the samples above, used by the QA loop in docs/TEST_PLAN.md.

`reports/` holds committed benchmark reports (markdown) named
`YYYY-MM-DD_device_model.md` with: device, model, chunk length, threads,
duration processed, wall time, RTF, peak RSS, battery delta, thermal notes.

Methodology (blueprint §8):

1. Fresh boot, battery above 80%, airplane mode ON.
2. Start `adb logcat` capture; note start battery level and skin temperature.
3. Process the sample; record chunk timings from the processing dashboard.
4. Compute RTF = wall time / audio duration; log peak memory via
   `dumpsys meminfo com.momosubtitle.app`.
5. Repeat with chunk length 30 / 45 / 60 s and threads 2 / 4 / 6.
