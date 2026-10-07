# Theme Compose UI extension

This surface extends the incumbent Still UI. [PRODUCT.md](../PRODUCT.md) records product commitments; [DESIGN.md](../DESIGN.md) records the native visual system; [theme-compose.md](theme-compose.md) describes the language, runtime boundaries and verification. The web project's existing PRODUCT.md and DESIGN.md remain its own authority.

## Native surface

Custom themes follow Special themes in the existing Theme screen. Installed cards match seasonal cards: a 104dp appearance preview, 16dp outer corners, the same border/inset treatment, and a host-colored metadata footer. Tapping a card selects the theme; the primary border and circular check indicate selection. A round More control opens management. Create a theme uses an outlined navigation row with `StillIcons.Add` and opens the bounded `StillDrawer` theme creator.

The theme creator provides an interactive live preview of both light and dark modes with the canonical Still wordmark, surface card, mock content bars, and primary accent badge. Users specify a theme name and configure colors through dual modes:
- **Simple mode:** Curated accent swatches or custom hex input, with selectable light and dark background tones (white, warm, tinted, neutral, charcoal, black, slate) and automated luminance-based contrast ink calculations.
- **Advanced mode:** Expandable fine-tuning of all 6 Material roles (`primary`, `on-primary`, `background`, `on-background`, `surface`, `on-surface`) for both light and dark palettes.

Saving formats a valid Theme Compose 1 source file, loads and installs it into local atomic storage, and activates it immediately.

Management allows switching appearance between System, Light, and Dark (stored per theme). "Export theme" opens Android's document picker to output the theme as a portable `.tc` file. "Remove theme" deletes the theme using the semantic error container styling.

The Theme screen and custom theme drawers always restore built-in light/dark host colors when needed. `TrustedThemeControls` wraps the settings recovery path in the host theme and surface so theme colors cannot disguise management controls.

## Web authoring surface

The companion studio at `/theme-compose/studio` inherits Still web's existing identity. Its editor-first simulation keeps source editing and theme preview together, including request-denial/permission states and export. This is an authoring companion, not the Android installation or consent boundary. Do not transfer its web layout or tokens into native settings.

## Evidence and disposition

The completed review disposition is **ship** after resolving the listed cancellation, opt-in and persistence issues. Existing evidence is retained; this documentation pass did not rerun tests or interact with a device or browser.

The follow-up review addressed the community section's fit with the existing Special theme cards: matching preview geometry, border/inset, host metadata hierarchy, card selection, round management/check controls and the outlined Add row. Fresh phone, enlarged-font and tablet captures were reviewed with disposition **ship** and no material fixes remaining. Final `assembleDebug` passed, including the empty-section spacing adjustment. Recorded captures: [phone](../.impeccable/review/community-aligned-phone.png), [font scale 1.3](../.impeccable/review/community-aligned-font-1.3.png) and [tablet](../.impeccable/review/community-aligned-tablet.png).

Native captures in `../.impeccable/review/`:

| Capture | Surface recorded |
| --- | --- |
| [phone-add.png](../.impeccable/review/phone-add.png) | File/link source chooser |
| [phone-link.png](../.impeccable/review/phone-link.png) | Direct-link entry and disclosure |
| [phone-review.png](../.impeccable/review/phone-review.png) | Theme preview and consent review |
| [phone-font-1.3.png](../.impeccable/review/phone-font-1.3.png) | Enlarged font-scale layout |
| [tablet-permissions.png](../.impeccable/review/tablet-permissions.png) | Permission review on tablet |

Web captures live in `still-web/.impeccable/review/`: `desktop.jpg`, `mobile.jpg`, `mobile-preview.jpg` and `user-1280.jpg`. These are recorded surface evidence, not additional design authority.

The existing verification record in `theme-compose.md` reports 24 web tests, TypeScript, ESLint and production build; 113 Android unit tests, isolated preview assembly and 10 integration assertions on October 7, 2026. It distinguishes deterministic local integration evidence from real remote availability and a full daily scheduler interval. Android lint retains its reported existing failures in `SeasonalLauncherIcon.kt` and `OnboardingScreen.kt`.
