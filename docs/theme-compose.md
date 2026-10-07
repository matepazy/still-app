# Theme Compose 1 in Still

Theme Compose is the product-independent language name. Source uses `.tc`, bundles use `.tcb`; `.stc`, `.stcb`, and `theme.stc` remain accepted. The header remains **`compose-version: 1`**. These additions are part of version 1.

The reference and local studio live in still-web at `/theme-compose` and `/theme-compose/studio`. The TypeScript parser is `lib/theme-compose.ts`; Android's parser and closed interpreter are in `app/src/main/java/app/still/data/themes/`. A shared example is in `still-web/public/theme-compose/consent.tc` and Android test resources.

```text
compose-version: 1
target: still
id: my-theme
version: 1.0.0
title: My Theme
author: Your name

#request sysLightMode:
  id LightMode
  as string
  reason "Adapt the accent to system appearance"

@common
colors:
  light:
    primary: = LightMode == "dark" ? "#345D42" : "#286C43"
    ...
  dark:
    ...
```

Both palettes require all six common roles. Request fields accept optional colons. Core capabilities are `sysLightMode` (string, `light`/`dark`), `sysReducedMotion` (boolean), and `sysFontScale` (number). Other data must be explicitly registered by a host; the studio parser accepts an optional host registry. Still deliberately provides no usage history, app list, credentials, files, or network capabilities to themes.

Expressions support literals, request aliases, grouping, `!`, comparisons, `==`/`!=`, `&&`/`||`, `??`, and `? :`. They have no calls, interpolation, property access, arithmetic, loops, or code execution. Conditions and ordering are type checked. Requests are nullable, and visual results must be literal strings in every branch. Use `(FontScale ?? 1) > 1.2` or `(ReducedMotion ?? false)` to handle denial.

## Consent and lifecycle

- Community themes appear below special themes in the Theme menu. The system document picker handles files; direct links are downloaded only after the user chooses that action.
- Every request starts denied. Installation and Manage → Review permissions show the host's capability description and the author's reason separately. Consent keys include the source, alias, type and reason.
- A denied capability is never queried. Its expression value is `null`. All evaluation and asset decoding happen locally.
- System appearance and font configuration changes refresh rendering; resume also refreshes granted system settings. Availability is checked against the local date. Outside the interval, the app uses its built-in preference.
- Permissions, source review, theme management, and the recovery path use trusted host controls. Community colors cannot disguise these controls.
- Choosing a built-in theme clears the active community selection. Removing a theme clears its consent and pending update. Widgets follow community colors when their appearance follows the app; theme artwork respects their graphics switch.
- Android retains its packaged launcher icon. The installer explicitly explains this when a theme includes `branding.launcher-icon`.

## File and network boundaries

Source is strictly decoded UTF-8, limited to 1 MB, 10,000 lines, 4096 fields, 32 mapping levels and 32 requests. Expressions are limited to 2048 characters, 256 tokens and 32 nested levels.

Bundles are bounded to 10 MB compressed, 20 MB expanded and 32 entries. No paths are extracted to disk. Duplicate, absolute, traversal and unsupported entries are rejected. Only registered product artwork is decoded; unknown product sections and their assets are safely skipped. Still's image slots allow PNG/JPEG/WebP up to 2048 pixels per side and 8 megapixels total. SVG is never decoded by Still.

Packages and the catalog use immutable digest filenames and atomic writes in `noBackupFilesDir`. Consent and source links stay in private storage and are excluded from Android backup. Installed and pending packages together are limited to 40 MB and 12 installed themes.

Links require public HTTPS on port 443, without credentials, fragments or redirects. DNS results are checked at connection time; private and reserved IPv4/IPv6 destinations are rejected. Downloads have connect, read, total time and byte limits. No cookies, authorization, usage data, or request values are sent. A source host sees the normal request, including the phone's IP address. The UI states this before download and before enabling daily checks.

Daily checks use Android JobScheduler and are approximate. Downloads remain pending; they never activate or grant access. A candidate must keep the installed id and increase numeric `major.minor.patch`. The same version with changed bytes, unsupported requests, or invalid source cannot replace a theme. New or revised requests are denied on review; unchanged explicit grants can be retained. Disable checks in Manage to cancel the recurring job.

Authors are not authenticated, and HTTPS does not establish author identity. Review is the trust boundary; the runtime additionally restricts what a theme can do.

## Verification

Web: `npm run test:theme`, `tsc --noEmit`, `eslint .`, and `next build`.

Android: use `.gradle-home`, `.android-home`, `--offline --no-daemon`, and `-Pkotlin.incremental=false`. `:app:testDebugUnitTest` includes parser, expressions, URL policy, archive limits, version and consent tests. `-PthemePreview=true` builds the isolated `app.still.themepreview` package without replacing the installed app.

`CommunityThemeInstrumentation` runs with a deterministic injected transport on that preview package. Build it with `assembleDebugAndroidTest -PthemePreview=true -PstillTestRunner=app.still.data.themes.CommunityThemeInstrumentation`, install both preview APKs, then run `adb shell am instrument -w app.still.themepreview.test/app.still.data.themes.CommunityThemeInstrumentation`. It checks cancelled downloads do not commit, staged updates, default denial for new requests, revocation, private persistence, identity changes, same-version mutations, unsupported requests, scheduler cancellation and removal. It contacts no remote service. Real HTTPS server availability and a full 24-hour scheduler interval are separate from this evidence.

Downloads and manual update checks have an enabled Cancel action that cancels their coroutine and HTTP call. Daily checks start unchecked for newly linked themes; an update review preserves the installed theme's choice.

Verified on October 7, 2026: 24 web tests, TypeScript, ESLint and the production web build; 113 Android unit tests, preview APK assembly and all 10 Android integration assertions. Native import, permission editing, persistence, phone/tablet layout and 1.3 font scale were exercised. Android lint remains blocked by existing errors in `SeasonalLauncherIcon.kt` and `OnboardingScreen.kt`.
