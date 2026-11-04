# Design system (Milestone 0.1)

Dark-first Material 3, tuned to feel like a professional media utility, not a
developer tool. Palette and patterns follow the operator's own apps
(see docs/UI_REFERENCE.md).

## Color tokens

| Token | Hex | Use |
| ----- | --- | --- |
| InkBg | `#0E1116` | App background |
| InkSurface | `#151A21` | Cards, sheets |
| InkSurfaceHigh | `#1C232C` | Elevated surfaces, inputs |
| InkSurfaceHighest | `#242D38` | Highest elevation |
| InkOutline | `#2E3947` | Borders, dividers |
| Aqua | `#5CC8D7` | Primary accent (linuxify cyan) |
| Periwinkle | `#BAC3FF` | Secondary accent (zerium indigo tint) |
| OkGreen | `#4ADE80` | Complete/installed |
| WarnAmber | `#FBBF24` | Paused/partial |
| ErrRed | `#F87171` | Failed/error |
| InfoBlue | `#60A5FA` | Neutral info states |

Rules:
- Dark is the only theme in M0.1 (light scheme exists in code for Phase 2).
- Status colors are **fixed semantic values**, always paired with a text label
  (`StatusChip`) — never color-only.
- One accent (Aqua) drives interactive elements; Periwinkle is decoration only.

## Typography

- M3 default `Typography()`; no bundled fonts in M0.1.
- **Monospace styles for anything mechanical**: timestamps (`MonoSmall`,
  `MonoBody`), chunk counts, SHA-256 prefixes, storage sizes. This matches the
  operator's identity in nexpdf/linuxify.
- Headlines: bold, sentence case ("Momo Subtitle"); kickers: uppercase labels
  ("YOUR LOCAL SUBTITLE WORKSPACE").

## Layout & components

- Screen padding: 20 dp horizontal, 24 dp vertical top-level.
- Cards are the default content container (no dialogs for flows).
- `NavigationBar` with 3 top-level destinations (Home / Models / Settings),
  driven by `TopLevelDestination` enum + Navigation-Compose.
- Slim progress strip (4 dp `LinearProgressIndicator`, lambda progress API)
  docked under headers on processing screens.
- FlowRow filter chips for mode/language/chunk/model selection.
- Skeleton loaders and empty states: simple Card + text in M0.1; animated
  skeletons in Phase 2.
- Motion: M3 defaults in M0.1; adopt the operator's 120/220/320 ms
  `CubicBezier(0.05,0.7,0.1,1.0)` tokens in Phase 2.

## Component conventions

- `StatusChip` is duplicated in `feature:home` and `feature:processing` until
  `core/ui` grows shared composables (Phase 2 refactor).
- Headers are hand-rolled rows (IconButton + Text) — no `TopAppBar`
  experimental APIs.
- Icons: `material-icons-core` baseline set only (no extended icons bundle).
