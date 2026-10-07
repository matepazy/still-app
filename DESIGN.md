---
name: Still Android
description: Native Material hierarchy with quiet tonal surfaces and bounded bottom drawers.
colors:
  light-primary: "#27683C"
  light-primary-container: "#B8F2C5"
  light-background: "#F7F9F6"
  light-on-surface: "#172019"
  light-surface-container-low: "#F1F5F1"
  light-on-surface-variant: "#526057"
  light-outline-variant: "#D4DDD5"
  dark-primary: "#9FE3B2"
  dark-primary-container: "#203D2A"
  dark-background: "#111713"
  dark-on-surface: "#E9F5EC"
  dark-surface-container-low: "#151C17"
  dark-on-surface-variant: "#AAB7AD"
  dark-outline-variant: "#303A33"
typography:
  display-large:
    fontFamily: sans-serif
    fontSize: "64sp"
    fontWeight: 600
    lineHeight: "68sp"
    letterSpacing: "-2sp"
  title-large:
    fontFamily: sans-serif
    fontSize: "18sp"
    fontWeight: 600
    lineHeight: "24sp"
  title-medium:
    fontFamily: sans-serif
    fontSize: "15sp"
    fontWeight: 500
    lineHeight: "21sp"
  body-medium:
    fontFamily: sans-serif
    fontSize: "13sp"
    fontWeight: 400
    lineHeight: "19sp"
  body-small:
    fontFamily: sans-serif
    fontSize: "11sp"
    fontWeight: 400
    lineHeight: "16sp"
  label-large:
    fontFamily: sans-serif
    fontSize: "13sp"
    fontWeight: 500
    lineHeight: "18sp"
rounded:
  theme-card: "16dp"
  preview-accent: "8dp"
spacing:
  xSmall: "4dp"
  small: "8dp"
  medium: "12dp"
  large: "20dp"
  xLarge: "28dp"
  section: "32dp"
---

# Design System: Still Android

## Overview

Still uses its incumbent Kotlin/Compose Material 3 system: compact sans-serif hierarchy, tonal containers, clear selection indicators and native bottom drawers. Extend the existing components rather than introducing a separate visual identity. Android dimensions above retain their source units: spacing uses density-independent pixels; type uses scalable pixels.

**Key Characteristics:**

- Native controls with readable labels and explicit states.
- Theme-aware surfaces and restrained borders.
- Content-sized, scrolling drawers that accommodate enlarged text.
- Host-styled settings and recovery controls when community artwork is active.

The sources of authority are `app/src/main/java/app/still/ui/theme/Theme.kt`, `Dimensions.kt`, `ui/components/StillDrawer.kt` and the existing settings screens. Surface-specific choices and evidence belong in [the Theme Compose UI sidecar](docs/theme-compose-ui.md).

## Colors

The frontmatter records a reusable subset of the built-in light and dark green palettes. `Theme.kt` remains the complete source, including secondary and tertiary roles, semantic error colors, simple colors, seasonal themes and wallpaper-derived schemes. Use `MaterialTheme.colorScheme` roles rather than hardcoding this subset into screens.

Primary colors identify actions and selections; primary containers carry supporting emphasis. Background and low containers distinguish the page from grouped content. On-surface colors carry main text, on-surface-variant carries supporting text, and outline-variant carries quiet unselected borders.

**The Host Controls Rule.** Permission, source, management and recovery surfaces retain trusted host colors. Community appearance belongs in the app appearance and its contained preview, not in the controls used to revoke it.

## Typography

`StillTypography` uses Android `FontFamily.SansSerif`. Semibold display and titles provide hierarchy; normal body text explains actions and medium labels describe state. The frontmatter extracts recurring roles; the complete scale stays in `Theme.kt`.

Settings section and drawer headings use `titleLarge`; installed-theme titles use `titleMedium`; permission descriptions use `titleSmall`. Explanations use `bodyMedium`, metadata uses `bodySmall`, and selection/update state uses label roles. Preserve system font scaling and wrapping rather than fitting text into fixed-height rows.

## Layout

Settings use a vertically scrolling column. `StillSpacing.large` supplies horizontal page and drawer padding, `medium` separates related content, and `xLarge` separates sections and supplies drawer bottom padding. Existing theme grids adapt their column count to available width and font scale.

`StillBottomSheet` caps the whole drawer at 85% of the window, reserving space for a 48dp handle area and the safe bottom inset. Its inner content is constrained so sheet anchors remain in window coordinates. `StillDrawer` adds scrolling and safe padding; `ActionDrawer` wraps actions with `FlowRow`. Allow content to determine height within this cap.

## Elevation & Depth

Tonal surface containers distinguish groups without introducing custom shadow effects. Drawers inherit Material 3 modal sheet depth and the standard drag handle. Retain this native treatment.

## Shapes

Theme cards and selection borders use the rounded theme-card shape. Small contained preview accents use the preview-accent shape. Selection is shown with a primary border and a circular check indicator; unselected borders use outline-variant. Material buttons, fields, checkboxes and sheet shapes retain their component defaults.

## Components

- **Theme choices:** existing preview cards, labels, radio/check semantics and primary selection indicators.
- **Installed theme cards:** low tonal containers, title and author/version metadata, explicit selected/update state, Use theme and Manage actions.
- **Drawers:** shared `StillDrawer` for focused settings, source choices, review and management; `ActionDrawer` for confirmations and wrapping action groups.
- **Actions and inputs:** Material `Button`, `OutlinedButton`, `TextButton` and `OutlinedTextField`; choose variants according to the incumbent flow hierarchy.
- **Permission choices:** a whole toggleable row with checkbox semantics; weighted, wrapping text separates the host capability label, Allow/Deny state and author's reason.
- **Trusted recovery:** `TrustedThemeControls` provides a host-themed surface; the Theme screen and community drawers also restore built-in host colors when needed.

## Do's and Don'ts

Do:

- Reuse Material roles, `StillSpacing`, typography and shared drawers.
- Let long labels wrap and let bounded drawer content scroll.
- Keep consent, removal and built-in-theme recovery visible in host styling.

Don't:

- Promote an individual theme's palette or artwork into the host design system.
- Replace native settings controls with a separate web-style visual language.
- Introduce fixed text heights or a new token scale to fit one surface.
