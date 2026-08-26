package app.gov.uidai.registration.ui.theme

import androidx.compose.ui.graphics.Color

// Material Theme Colors (Light)
val md_theme_primary = Color(0xFF2F100C)
val md_theme_onPrimary = Color(0xFFFFFFFF)
val md_theme_primaryContainer = Color(0xFF31120E)
val md_theme_onPrimaryContainer = Color(0xFFA97770)

val md_theme_secondary = Color(0xFF874D31)
val md_theme_onSecondary = Color(0xFFFFFFFF)
val md_theme_secondaryContainer = Color(0xFFA46547)
val md_theme_onSecondaryContainer = Color(0xFFFFFBFF)

val md_theme_tertiary = Color(0xFF006674)
val md_theme_onTertiary = Color(0xFFFFFFFF)
val md_theme_tertiaryContainer = Color(0xFF008092)
val md_theme_onTertiaryContainer = Color(0xFFF8FDFF)

val md_theme_error = Color(0xFFAF0213)
val md_theme_onError = Color(0xFFFFFFFF)
val md_theme_errorContainer = Color(0xFFCC7178)
val md_theme_onErrorContainer = Color(0xFFFFEEEC)

val md_theme_background = Color(0xFFFFF8F7)
val md_theme_onBackground = Color(0xFF1F1B1A)
val md_theme_surface = Color(0xFFFFF8F7)
val md_theme_onSurface = Color(0xFF1F1B1A)

val md_theme_surfaceVariant = Color(0xFFF2DEDB)
val md_theme_onSurfaceVariant = Color(0xFF514442)
val md_theme_outline = Color(0xFF837371)
val md_theme_outlineVariant = Color(0xFFD5C2BF)
val md_theme_scrim = Color(0xFF000000)

val md_theme_inverseSurface = Color(0xFF231F1E)
val md_theme_inverseOnSurface = Color(0xFFF9EEED)
val md_theme_inversePrimary = Color(0xFFF3B9B0)

val md_theme_surfaceDim = Color(0xFFE2D8D6)
val md_theme_surfaceBright = Color(0xFFFFF8F7)
val md_theme_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_surfaceContainerLow = Color(0xFFFCF1F0)
val md_theme_surfaceContainer = Color(0xFFF6ECEA)
val md_theme_surfaceContainerHigh = Color(0xFFF0E6E4)
val md_theme_surfaceContainerHighDark = Color(0xFF2E2928)
val md_theme_surfaceContainerHighest = Color(0xFFEAE0DF)
val md_theme_surfaceContainerHighestDark = Color(0xFF393333)

val errorContainer = Color(0xFFCC7178)
val successContainer = Color(0xFF9BC1BC)
val pendingContainer = Color(0xFF99C1DE)

// ── Dashboard-specific tokens (matches SITAA Operator Dashboard mockup exactly) ──

// App bar / navy surfaces
val dash_navy = Color(0xFF0B1F3A)
val dash_screen_bg = Color(0xFFF0F4FA)

// Hero card gradient
val dash_hero_gradient_start = Color(0xFF0F3460)
val dash_hero_gradient_end = Color(0xFF1A56A0)
val dash_finger_hero_end = Color(0xFF1A3A6B)

// Avatar chip (teal)
val dash_avatar_bg = Color(0x3300BFA5)      // rgba(0,191,165,.2)
val dash_avatar_border = Color(0x6600BFA5)  // rgba(0,191,165,.4)
val dash_avatar_text = Color(0xFF00BFA5)

// Ticker (red banner)
val dash_ticker_bg = Color(0xFF7F1D1D)
val dash_ticker_text = Color(0xFFFEE2E2)
val dash_ticker_dot = Color(0xFFDC2626)
val dash_ticker_sep = Color(0x66DC2626)

// Tab bar
val dash_tab_inactive = Color(0xFF8A9AB8)
val dash_tab_active = Color(0xFF1A56A0)
val dash_card_border = Color(0xFFD8E3F0)
val dash_sec_title = Color(0xFF8A9AB8)
val dash_text_primary = Color(0xFF0B1628)
val dash_text_secondary = Color(0xFF4A5568)
val dash_text_muted = Color(0xFF6B7A99)

// Gender tile colors: Male (blue), Female (green), Other (purple)
val dash_male_bg = Color(0xFFEBF3FD); val dash_male_border = Color(0xFF93C5FD); val dash_male_text = Color(0xFF1A56A0)
val dash_female_bg = Color(0xFFF0FDF4); val dash_female_border = Color(0xFF86EFAC); val dash_female_text = Color(0xFF16A34A)
val dash_other_bg = Color(0xFFEEEDFE); val dash_other_border = Color(0xFFC4B5FD); val dash_other_text = Color(0xFF534AB7)

// Age tile colors: 5-17 (purple), 18-40 (blue), 41-60 (green), 60+ (amber)
val dash_age1_bg = Color(0xFFF5F3FF); val dash_age1_border = Color(0xFFDDD6FE); val dash_age1_text = Color(0xFF534AB7)
val dash_age2_bg = Color(0xFFEBF3FD); val dash_age2_border = Color(0xFF93C5FD); val dash_age2_text = Color(0xFF1A56A0)
val dash_age3_bg = Color(0xFFF0FDF4); val dash_age3_border = Color(0xFF86EFAC); val dash_age3_text = Color(0xFF16A34A)
val dash_age4_bg = Color(0xFFFEF9EE); val dash_age4_border = Color(0xFFFCD34D); val dash_age4_text = Color(0xFFD97706)

// Finger-count bands: 1-3 red, 4 amber, 5-7 green, 8-10 blue
val dash_band_red_bg = Color(0xFFFEE2E2); val dash_band_red_border = Color(0xFFFCA5A5); val dash_band_red_text = Color(0xFFDC2626)
val dash_band_amber_bg = Color(0xFFFEF3C7); val dash_band_amber_border = Color(0xFFFCD34D); val dash_band_amber_text = Color(0xFFD97706)
val dash_band_green_bg = Color(0xFFF0FDF4); val dash_band_green_border = Color(0xFF86EFAC); val dash_band_green_text = Color(0xFF16A34A)
val dash_band_blue_bg = Color(0xFFEBF3FD); val dash_band_blue_border = Color(0xFF93C5FD); val dash_band_blue_text = Color(0xFF1A56A0)

// Quota status (Diversity tab)
val dash_status_open = Color(0xFF16A34A)
val dash_status_warn = Color(0xFFD97706)
val dash_status_full = Color(0xFFDC2626)
val dash_override_note_bg = Color(0xFFEDE9FE)
val dash_override_note_border = Color(0xFFC4B5FD)
val dash_override_note_text = Color(0xFF5B21B6)

// Soft-prompt modal — WARN variant
val dash_modal_warn_icon_bg = Color(0xFFFEF3C7)
val dash_modal_warn_snapshot_bg = Color(0xFFFEF3C7)
val dash_modal_warn_snapshot_border = Color(0xFFFCD34D)
val dash_modal_warn_label = Color(0xFF92400E)
val dash_modal_warn_value = Color(0xFFD97706)
val dash_modal_warn_bar_track = Color(0xFFFDE68A)
val dash_modal_warn_bar_fill = Color(0xFFD97706)
val dash_modal_warn_note = Color(0xFF92400E)
val dash_modal_warn_button = Color(0xFFD97706)

// Soft-prompt modal — FULL variant
val dash_modal_full_icon_bg = Color(0xFFFEE2E2)
val dash_modal_full_title = Color(0xFF991B1B)
val dash_modal_full_snapshot_bg = Color(0xFFFEE2E2)
val dash_modal_full_snapshot_border = Color(0xFFFCA5A5)
val dash_modal_full_label = Color(0xFF991B1B)
val dash_modal_full_value = Color(0xFFDC2626)
val dash_modal_full_bar_track = Color(0xFFFECACA)
val dash_modal_full_bar_fill = Color(0xFFDC2626)
val dash_modal_full_note = Color(0xFFB91C1C)
val dash_modal_full_button = Color(0xFFDC2626)
val dash_modal_ack_bg = Color(0xFFFEF3C7)
val dash_modal_ack_border = Color(0xFFFCD34D)
val dash_modal_ack_text = Color(0xFF92400E)

// Alternatives suggestion rows (green, shown in both modal variants)
val dash_alt_row_bg = Color(0xFFF6FEF9)
val dash_alt_row_border = Color(0xFF86EFAC)
val dash_alt_tag_bg = Color(0xFFDCFCE7)
val dash_alt_tag_text = Color(0xFF166534)
val dash_alt_tag_border = Color(0xFF86EFAC)
val dash_alt_slots_text = Color(0xFF16A34A)

// Modal back button
val dash_modal_back_border = Color(0xFFC8D5E8)
val dash_modal_back_text = Color(0xFF4A5568)