# Theme Compose UI extension

This surface extends the incumbent Still UI. [PRODUCT.md](../PRODUCT.md) records product commitments; [DESIGN.md](../DESIGN.md) records the native visual system; [theme-compose.md](theme-compose.md) describes the language, runtime boundaries and verification. The web project's existing PRODUCT.md and DESIGN.md remain its own authority.

## Native surface

Community themes follow Special themes in the existing Theme screen. Installed cards match seasonal cards: a 104dp appearance preview, 16dp outer corners, the same border/inset treatment, and a host-colored metadata footer. Tapping a card selects the theme; the primary border and circular check indicate selection. A round More control opens management. Unavailable and pending-update states remain visible in the footer. Add a theme uses an outlined navigation row with the existing icon family and opens the bounded `StillDrawer` source chooser. Files use the Android document picker.

The appearance preview uses the theme's index image when present, otherwise its colors, canonical Still wordmark and the same bar vocabulary as the built-in previews. Image thumbnails decode off the UI thread and are sampled to at most 512 pixels per side. Theme colors are confined to this preview; metadata, selection and management retain host colors.

The link drawer provides a labeled HTTPS field, the IP-address disclosure and Download and review. Errors and progress appear in the current surface. Download and manual-check operations keep Cancel enabled; dismissal cancels the coroutine and HTTP request. During short atomic storage operations, dismissal waits for completion.

Review separates a contained theme-colored preview from trusted host controls. The preview resolves appearance with requests denied. Host capability descriptions, Allow/Deny state and the author's reason are separate text roles. Permission rows wrap beside their checkbox and make the full row toggleable. Every new request starts denied; unchanged explicit grants can survive update review, while revised requests require consent again.

Management starts with two tonal navigation rows: Permissions shows how many requests are allowed, and Updates shows its source/check status or a ready version. Each row has a distinct host `surfaceContainerHigh` background. Remove uses the semantic error container; Done is a filled closing action. Only the details drawer shows the author and description; list cards keep the title and version. Permissions use short setting names and trailing switches. Author reasons are hidden under “Why these permissions?” when managing an installed theme and remain visible during installation review. Details include System / Light / Dark appearance choices, stored per theme and preserved across updates. Protected settings and management drawers follow the active theme’s selected appearance with readable host colors; the rest of the app and widgets use its palette. Done animates the sheet closed before removing it. Permissions and linked updates return to details with either the Back action or Android back gesture. Installed-theme permission switches save immediately; there is no separate Save action.

For link sources, Updates groups the source host, daily checks, manual checking, and pending review/discard. New linked themes start with daily checks off; update review preserves the existing preference. The IP-address disclosure remains next to the toggle. For file themes, Updates opens the document picker directly and accepts only a newer version with the same theme id. Its review preserves unchanged grants and denies changed requests before installation.

The Theme screen restores built-in light/dark host colors when a community theme is active. Community drawers always use those trusted colors; `TrustedThemeControls` wraps the settings recovery path in the System host theme and surface. Theme colors cannot hide permission, removal or built-in-theme recovery controls. Android's packaged launcher icon remains unchanged and review explains this when launcher artwork is present.

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
