# Custom theme creator

Mode: Operate. Platform: Android, Kotlin/Compose Material 3.

## Direction contract

The creator uses `settings/theme/create` and Still's existing settings top bar, typography, spacing, icons and Material controls. Its four focused steps are **Accent**, **Background**, **Fine-tune**, and **Preview & save**. Step progress and a contained live preview accompany each decision; the final step asks for a name and provides **Save and use theme**. Management and removal remain focused drawers. Editor controls keep the selected theme when readable, with a host-color recovery fallback for unreadable palettes; the contained preview uses the draft.

The step and draft survive configuration changes and are applied only on explicit save. Invalid hex edits keep the last valid preview and disable progression and save. Background and accent choices regenerate the palette; Fine-tune explains this reset and offers **Reset color details**. Its six tappable rows retain every authorable role (accent, text on accent, background, text on background, surface, and text on surface), each opening a native color-editing drawer. Back returns to the previous step before closing the creator. The body scrolls within a centered maximum width of 600dp. The persistent Next/Back/save footer stays above the keyboard; palette choices wrap and detail rows grow with enlarged text.

Preset accents adapt to meet 4.5:1 text contrast on the selected page, surface, and Material container surfaces. Explicit custom accent and fine-tuned role values remain exact; the creator warns when the palette pairs or accent on these surfaces may be hard to read. Save requires a nonblank name and valid six-digit hex values; a readability warning alone does not block saving.

Themes created here have one fixed palette. Theme Compose v1 export keeps compatibility by writing that palette identically into both legacy slots. Existing installations keep their selected legacy slot; System entries resolve once and persist that selection. Authorized theme expressions continue receiving actual system capability values.

Custom accent and fine-tune colors open in native drawers with a touch-controlled saturation/brightness area, a labeled hue slider, synchronized HEX entry, and Done. Incomplete HEX edits retain the last valid picker color. Visual edits preserve optional trailing alpha and the selected hue through grayscale/black choices. The hue slider exposes native accessibility adjustment; the color area exposes saturation and brightness actions.

## Quality bar

- Native route and Android Back, with no creation drawer or appearance tabs.
- A single explicit save; failed saves preserve the draft and provide recovery text.
- Complete mapping of all 48 Material color roles without copying a built-in or previous theme scheme.
- No seasonal artwork or widget graphics carried over from the previous theme. The contained preview uses Still's incumbent wordmark, tinted with the draft accent.
- Preset foreground/background pairs meet 4.5:1; accent text and icons must remain readable on page and container surfaces.
- Touch targets at least 48dp; intrinsic sizing and wrapping at normal, enlarged phone, and 800dp tablet width.
- Code-led implementation inside the established Still visual system. The supplied screenshot informs the guided flow; there is no new-world seed, approved generated comp, generated artwork, or web detector.

## Current guided-flow evidence

The final `ThemeCreatorScreen.kt` opening contract records the thesis, incumbent visual world, story, first viewport and native form. The finish review returned **SHIP** with no material fixes. The final debug build passed, and all 126 existing unit tests passed with zero failures. The emulator interaction check saved **Guided preview** and verified it in the theme list. These checks are recorded in [guided-creator-validation.json](../artifacts/guided-creator-validation.json).

Final layout captures under `.impeccable/review/` cover [Accent](../.impeccable/review/phone-accent.png), [Background](../.impeccable/review/phone-background.png), [Fine-tune](../.impeccable/review/phone-details.png), [the color drawer](../.impeccable/review/phone-color-drawer.png), and [Preview & save](../.impeccable/review/phone-save.png). Additional reviewed captures cover [font scale 1.3](../.impeccable/review/phone-enlarged.png), [800dp tablet width](../.impeccable/review/tablet.png), [light appearance](../.impeccable/review/phone-light.png), and [the docked keyboard](../.impeccable/review/phone-keyboard.png). The keyboard capture uses the dedicated workspace-local StillGuided emulator (`emulator-5556`, hardware keyboard disabled) and shows the name field and footer above a full-width IME. The other layout and save checks use the Pixel 10 Pro emulator (`emulator-5554`).

All final captures were accepted in the finish review. This is emulator layout and interaction evidence; preview usage is illustrative. Physical-device gestures, motion and refresh-rate behavior were not tested. No web detector ran because the surface is native Android.

## Historical evidence: preceding single-screen creator

The following captures and validation describe the earlier single-screen layout and its corrections; they are retained as history, not evidence of the current four-step composition.

Emulator captures live under `artifacts/theme-creator/`: [normal phone](../artifacts/theme-creator/creator-normal.png), [enlarged phone](../artifacts/theme-creator/creator-enlarged.png), [lower controls](../artifacts/theme-creator/creator-controls.png), [enlarged lower controls](../artifacts/theme-creator/creator-enlarged-controls.png), [800dp tablet width](../artifacts/theme-creator/creator-tablet.png), and [Today with the saved Ocean/Charcoal palette](../artifacts/theme-creator/custom-today.png). Today uses debug usage fixtures. These captures are layout evidence, not proof of gestures, motion, or physical-device behavior.

The final isolated preview build passed with 122 unit tests and no failures. Unit coverage exercises all 48 Material roles and all 128 preset combinations against actual Material surfaces. The isolated emulator runner passed 16 checks covering installation, migration, restore, freezing System entries, updates, permissions and removal. Android lint completed with two existing `NewApi` errors in `SeasonalLauncherIcon.kt`.

The full review returned **fix**, naming preset accent contrast and stale documentation. The correction adapts presets against actual page and container surfaces; this documentation pass removes the obsolete creation-drawer and dual-mode guidance. The final verdict-only pass marked both corrections resolved with disposition **ship**, and validated the replacement captures. Validation details are retained in [validation.json](../artifacts/theme-creator/validation.json). No web detector ran because this is a native Android surface.

## Recording regression: settings palette reset

The October 8 recording showed a custom palette on the main tabs while Settings and Theme reverted to the built-in green host palette. Recovery controls now retain readable custom colors. A local MaterialTheme fallback handles unreadable or transparent palettes without rewriting the activity window or clearing the active custom style.

Validation: 124 unit tests passed, including every 128 preset combination retaining its recovery colors and unreadable palettes falling back. On a separate Pixel 10 Pro emulator, a saved Lavender / Slate theme rendered background #151B22 and top bar #21262D consistently in Today, Settings, Theme and the creator. Settings-to-Theme navigation uses the real screens with preview fixtures. Evidence: `artifacts/theme-creator-fix/validation.json` and the four screen captures in that directory.

## Editing and details

Theme cards and details omit version labels. The details drawer presents equal-width Edit and Export buttons in one row. Edit opens the creator with the installed name and the currently selected fixed palette. Save replaces and activates the same theme in its current list position. The source retains metadata, request declarations, consent and bundled artwork. A linked theme becomes a local revision on explicit save; the editor discloses that linked updates stop. A digest guard rejects stale drafts, including after rotation.

All app top bars use the page background for both resting and scrolled states. The October 8 edit-flow check passed 126 unit tests and 20 device checks. On the isolated preview emulator, Berry opened with its saved colors; changing it to Berry edited and Charcoal updated one list entry. Captured headers matched page colors before (#151B22) and after (#121212). Evidence is in `artifacts/theme-edit/validation.json`, `details.png`, `editor.png`, and `edited-theme-list.png`. Usage inside the creator remains an explicitly labeled example.
