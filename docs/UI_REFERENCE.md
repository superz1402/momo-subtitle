# UI reference — patterns extracted from the operator's repositories

Source analysis: `_research/UI_ANALYSIS.md` (full report, agent-generated from
public repos of bilal140202, ansaribilal14, superz1402 on 2026-11-04).

## Repos analyzed

| Repo | Stack | UI takeaways |
| ---- | ----- | ------------ |
| AuraGallery (bilal140202) | Kotlin + Compose M3 | AMOLED pure-black option, complete `Typography()` override template |
| zerium-browser (ansaribilal14) | Java Views + embedded Compose | Full M3 tonal palettes in XML `values/` + `values-night/`, indigo seed `#4355B9`/`#BAC3FF` |
| nexpdf (superz1402) | Kotlin + Compose M3 | Monospace identity for document metadata, motion tokens (`NexMotion`), skeleton loaders |
| linuxify (superz1402) | Kotlin + Compose M3 | Elevated dark ramp `#0E1116→#151A21→#1C232C→#242D38`, cyan accent `#5CC8D7`, slim progress strip |

## Framework mix observed

- Modern screens: **Jetpack Compose + Material 3**, single-activity, minSdk 26
  / targetSdk 35, Compose BOM, version catalogs.
- Navigation: bottom bars driven by destination enums; no multi-activity UI;
  several repos hand-roll route state instead of Navigation-Compose — Momo
  Subtitle standardizes on Navigation-Compose instead.

## Lessons applied to Momo Subtitle

1. One seed hue → full M3 role mapping, light+dark (zerium indigo family as
   secondary, linuxify cyan as primary accent).
2. Dark-first with the 4-step elevated ramp; AMOLED option deferred to
   settings (Phase 2).
3. Dynamic color (Android 12+) optional behind a setting — brand scheme is the
   default for a professional tool.
4. Complete `Typography()` override + **monospace for timestamps/codecs/counts**
   (linuxify/nexpdf identity).
5. Motion tokens file (120/220/320 ms, `CubicBezier(0.05,0.7,0.1,1.0)`,
   low-bouncy spring) — adopt in Phase 2.
6. M3 `NavigationBar` + `TopLevelDestination` enum + Navigation-Compose.
7. Slim progress dock above the bar for transcribe/export states; skeleton
   loaders for async areas.
8. Fixed semantic status colors with labels, never color-only
   (`#4ADE80/#FBBF24/#F87171/#60A5FA`).
9. Manifest polish: edge-to-edge, `adjustResize`, explicit `configChanges`.
10. Pitfalls seen in the operator's repos and avoided here: R8 left disabled in
    release (kept disabled in M0.1 but tracked in RELEASE_CHECKLIST), palette
    duplicated between XML and Compose (single Kotlin source of truth here),
    non-DayNight base theme.

## For future agents

Before adding new UI, re-clone the operator's repos (AGENTS.md §4), compare
against this document, and extend it rather than diverging.
