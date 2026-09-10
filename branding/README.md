# Still visual assets

The current Still identity is built from one centered continuous signal: an `S` drawn as a quiet path between two mint endpoints. The launcher artwork follows Android's adaptive-icon safe zone, while the standalone master includes the rounded deep-green tile seen in the product references.

Every logo is path-based. No asset depends on a font, embedded bitmap, system icon pack, or device density.

## Brand assets

| Asset | Use |
| --- | --- |
| `still_icon_master.svg` | Primary app icon and store artwork |
| `still_icon_foreground.svg` | Android adaptive-icon foreground |
| `still_icon_monochrome.svg` | Android 13+ themed icon |
| `still_icon_round_foreground.svg` | Inset foreground for circular launcher masks |
| `still_icon_round_monochrome.svg` | Inset themed foreground for circular launcher masks |
| `still_mark.svg` | Light mark on dark surfaces |
| `still_mark_ink.svg` | Ink mark on light surfaces |
| `still_lockup.svg` | Horizontal lockup for light surfaces |
| `still_lockup_inverse.svg` | Horizontal lockup for dark surfaces |
| `still_stacked.svg` | Vertical lockup |
| `still_wordmark.svg` | Path-only wordmark for light surfaces |
| `still_wordmark_inverse.svg` | Path-only wordmark for dark surfaces |
| `still_brand_mark.svg` | Compact rounded tile used by in-app brand components |

The wordmark lettering is custom vector geometry, including its corrected mirrored initial `S`; do not replace it with live text.

## Product icon system

`icons/` contains the SVG master for every icon used by the Compose UI. Each uses a 24×24 grid, a 1.8-unit optical stroke, round joins, and `currentColor`. Matching Android VectorDrawables live in `app/src/main/res/drawable/ic_ui_*.xml` and are referenced centrally by `StillIcons.kt`.

## Onboarding artwork

`onboarding/` contains four 160×160 SVG masters. Matching Android vectors use the same geometry and fixed palette, so the illustrations render consistently at every density.

## Core colors

- Deep green: `#0D1B17`
- Surface green: `#13231D`
- Structural green: `#395047`
- Paper: `#F1F7F3`
- Mint: `#79E6AA`
- Light-surface ink: `#102019`
- Light-surface green: `#24734A`
