# Colors / Spacing migration checklist

- [x] Step 1 — Create `Colors.kt` and `Spacing.kt` in `registration/ui/theme`
- [x] Step 2 — Wire `LightColors` in `Theme.kt` to `Colors`
- [x] Step 3a — Migrate old capture `Colors.colour*`
- [x] Step 3b — Migrate `capture_method_*`
- [x] Step 3c — Migrate `md_theme_*`
- [x] Step 3d — Migrate `dash_*`
- [x] Step 3e — Migrate `errorContainer` / `successContainer` / `pendingContainer`
- [x] Step 3 — `FingerListColors` -> tokens in `RegistrationRoute.kt`, delete object
- [x] Step 3 — `ScreenColors.kt` removal + title colour in RegisterOperatorScreen / UidEntryRoute
- [x] Step 3 — `BackgroundWithGradient()` end colour + background
- [x] Step 3 — Delete old `ui/theme/Color.kt` and `capture/ui/theme/Colors.kt`
- [x] Step 4 — Replace spacing `.dp` with `Spacing.*`, screen by screen
- [x] Step 5 — Verify grep is empty, `assembleDebug` + `assembleRelease` pass, final report
