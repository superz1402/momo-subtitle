# Issues draft — v0.2.0 (filed on GitHub 2026-11-09 as #13–#16)

Recreated after the sandbox recycle that lost the 2026-11-08 15:30 session.
The four issues below were filed via the GitHub API before the rebuild
reached the build stage; they are the authoritative task list (add-on rules).

---

## #13 Background model downloads with notification progress + notification permission flow

Operator request: model downloads must survive backgrounding and app closes,
with a notification-bar progress bar, and POST_NOTIFICATIONS must be requested
with a proper rationale flow.

- `ModelDownloadService` (foreground service, dataSync type) mirroring
  `TranscriptionService`: progress % + bytes + ETA, Cancel action, terminal
  success/error notification.
- Download execution moved out of `viewModelScope` into the service; UI talks
  to it via `AppServicesBridge.ModelDownloadRunner` (installed in `MomoApp`).
- One shared `ModelRepository` instance (`ModelRepository.shared`) so screen +
  service observe the same progress StateFlow.
- POST_NOTIFICATIONS (API 33+) rationale dialog on the Models screen before the
  first download; "Not now" still downloads (progress just stays in-app).
- `START_REDELIVER_INTENT` so a killed service re-delivers the start intent and
  resumes from the `.part` file.

## #14 Fix "software caused connection abort" during model downloads

Root cause: a single-shot `HttpURLConnection` stream with no retry — any
Wi-Fi/cellular hiccup or Doze network restriction killed the whole download.

- Retry loop in `ModelRepository.download()`: up to 6 attempts, exponential
  backoff (1/2/4/8/15/30 s + jitter), each attempt resuming from the `.part`
  file via HTTP Range.
- `Accept-Encoding: identity` keeps Range offsets valid; HTTP 416 restarts from
  zero; friendly terminal error strings.
- Progress emissions throttled to ~512 KB granularity; speed (B/s) exposed for
  ETA display on screen and in the notification.

## #15 Isolate downloads and transcription from the UI process

- All download work executes in `ModelDownloadService`'s own coroutine scope
  (repository does the I/O on `Dispatchers.IO`) — never in a ViewModel scope.
- The UI only sends start/cancel intents and observes `downloads` StateFlow.
- Throttled progress emissions keep recomposition cheap for screen + notification.
- Both long-running jobs are foreground services (dataSync) — heavy work stays
  off the main thread and alive under Doze; `.part` files make every
  interruption resumable.

## #16 Same-key signed releases + publish v0.2.0

- Dedicated release keystore committed to the repo (`keystore/momo-release.jks`
  + `keystore/momo.properties`) — deliberate tradeoff: a public repo plus
  frequently wiped servers make repo-committed keys the only way to guarantee
  the same key forever.
- Gradle `momo` signingConfig applied to BOTH release and debug builds so no
  install ever conflicts.
- versionCode 3, versionName 0.2.0; apksigner must show debug and release
  sharing one cert SHA-256; tag v0.2.0 + GitHub Release with both signed APKs.
- NOTE: v0.1.1 was signed with the (lost) old debug key — one-time uninstall of
  v0.1.1 required before installing v0.2.0; every later update flows over.
