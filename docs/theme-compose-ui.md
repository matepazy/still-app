# Theme Compose UI extension

This surface extends the incumbent Still UI. [PRODUCT.md](../PRODUCT.md) records product commitments; [DESIGN.md](../DESIGN.md) records the native visual system; [theme-compose.md](theme-compose.md) describes the language, runtime boundaries and verification. The web project's existing PRODUCT.md and DESIGN.md remain its own authority.

## Native surface

Custom themes follow Special themes in the existing Theme screen. Installed cards match seasonal cards: a 104dp appearance preview, 16dp outer corners, the same border/inset treatment, and a host-colored metadata footer. Tapping a card selects the theme; the primary border and circular check indicate selection. A round More control opens management. Create a theme uses an outlined navigation row with `StillIcons.Add` and opens the dedicated `settings/theme/create` screen, documented in [the custom theme creator brief](theme-creator-ui.md).

The creator uses one live preview and one fixed palette. Users choose an accent and background tone, enter a name, and save. Optional color details expose all six authorable roles (`primary`, `on-primary`, `background`, `on-background`, `surface`, `on-surface`). Preset accents adapt for readable text on the page and Material containers; custom hex and fine-tuned colors retain their exact values with readability warnings. Invalid hex keeps the last valid preview and disables Save. There are no light/dark creator modes.

Saving formats a valid Theme Compose 1 source file, writes the same palette into both legacy slots, installs it into local atomic storage, and activates it immediately. All 48 Material roles derive from that palette. Existing themes keep their selected legacy appearance; System entries resolve once and persist that selection. Authorized imported expressions still receive actual system capability values.

Management remains a focused drawer. "Export theme" opens Android's document picker to output the theme as a portable `.tc` file. "Remove theme" deletes the theme using the semantic error container styling. The drawer no longer offers an appearance-mode selector.

Settings, the Theme screen and creator controls keep readable custom colors. `TrustedThemeControls` falls back to host colors only when recovery controls would be unreadable or transparent, without resetting the active style or activity window. Seasonal artwork and widget graphics remain isolated from custom palettes.

## Web authoring surface

The companion studio at `/theme-compose/studio` inherits Still web's existing identity. Its editor-first simulation keeps source editing and theme preview together, including request-denial/permission states and export. This is an authoring companion, not the Android installation or consent boundary. Do not transfer its web layout or tokens into native settings.

## Evidence and disposition

The review and captures below record the earlier community-theme surfaces. The replacement creator has its own [behavior and evidence record](theme-creator-ui.md); these earlier ship dispositions do not certify that new screen.

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

Custom theme details show Edit and Export side by side, without a version label. Editing retains the installed identity, metadata, consent and artwork, freezes both legacy palette slots to the edited colors, and stops linked updates after the explicit Save action. All app bars share the page background color.
