package app.gov.uidai.capture.ui.theme

import androidx.compose.ui.graphics.Color

object Colors {
    // ── Confirmed directly from your Figma examples ──
    val colourStatusNegativeOnContainer: Color = Color(0xFFBC3737)
    val colourSurfaceOnContainer: Color = Color(0xFF23313C)
    val colourSurfaceOnBase: Color = Color(0xFF010F2A)

    // ── Base surfaces ──
    val colourBase: Color = Color(0xFFF5F0EC)           // screen / phone background
    val colourContainerBase: Color = Color(0xFFEDE8E3)  // card / section / row background
    val colourBorderBase: Color = Color(0xFFD8D0C8)     // default hairline borders
    val colourBorderMuted: Color = Color(0xFFB8AFA8)    // dashed/skip borders

    // ── Text ──
    val colourSurfaceMuted: Color = Color(0xFF8A7F78)   // secondary/muted text, inactive tabs
    val colourSurfaceOnCard: Color = Color(0xFF1A1A1A)  // primary text on cream/card surfaces
    val colourConsentText: Color = Color(0xFF4A4540)    // consent body copy, unchecked

    // ── Accent / brand ──
    val colourPrimary: Color = Color(0xFF1A56A0)        // primary blue — buttons, active states, links
    val colourAccentOnBase: Color = Color(0xFF7C3D1E)   // terracotta — section labels, subtitles

    // ── Status roles — each with Container (bg), ContainerBorder, and OnContainer (text/icon) ──
    val colourStatusPositiveOnContainer: Color = Color(0xFF16A34A)   // green — done, success, ready
    val colourStatusPositiveContainer: Color = Color(0xFFDCFCE7)
    val colourStatusPositiveContainerBorder: Color = Color(0xFF86EFAC)
    val colourStatusPositiveContainerText: Color = Color(0xFF166534) // darker green for on-light-bg text

    val colourStatusWarningOnContainer: Color = Color(0xFFD97706)    // amber — detecting, syncing, min-required
    val colourStatusWarningContainer: Color = Color(0xFFFEF3C7)
    val colourStatusWarningContainerBorder: Color = Color(0xFFFCD34D)
    val colourStatusWarningContainerText: Color = Color(0xFF92400E)

    val colourStatusNegativeContainer: Color = Color(0xFFFEE2E2)     // inferred — not in mockup's blur-error state
    // (that state uses raw #DC2626 on dark bg,
    // no light container variant shown yet)
    val colourStatusNegativeContainerBorder: Color = Color(0xFFFECACA)
    val colourStatusNegativeContainerText: Color = Color(0xFF991B1B)
    val colourStatusNegative: Color = Color(0xFFDC2626)              // red — blur detected, rejected

    // ── Info/blue-light — reused across min-note, checked consent row, and "capturing" badge ──
    val colourInfoContainer: Color = Color(0xFFEBF3FD)
    val colourInfoContainerBorder: Color = Color(0xFF93C5FD)
    val colourInfoOnContainer: Color = Color(0xFF1A56A0)

    // ── Dark camera-screen surfaces (Step 3 — capture flow only) ──
    val colourCameraBackground: Color = Color(0xFF1A1A1A)
    val colourCameraOverlayIdle: Color = Color(0xFFFFFFFF)   // used at .6 alpha for idle oval border
    val colourCameraTextMuted: Color = Color(0xFFFFFFFF)     // used at .5/.7/.25 alpha for version text, labels
}