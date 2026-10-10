package app.gov.uidai.registration.model

enum class CaptureMethod {
    SLAP,
    SEQUENTIAL
}

// Visual-only sub-options shown under the Slap capture card once selected —
// no click behavior wired up yet, just the mockup's three chips.
enum class SlapSubOption {
    LEFT_SLAP,
    RIGHT_SLAP,
    LEFT_THUMB,
    RIGHT_THUMB
}

// Single source of truth for "which SlapSubOption maps to which capture
// path" -- shared by CaptureMethodViewModel (status lookups) and
// RegistrationActivity (launching the right SDK flow).
fun SlapSubOption.toFingerPositionOrNull(): FingerPosition? = when (this) {
    SlapSubOption.LEFT_SLAP -> FingerPosition.LEFT_SLAP
    SlapSubOption.RIGHT_SLAP -> FingerPosition.RIGHT_SLAP
    SlapSubOption.LEFT_THUMB -> FingerPosition.LEFT_THUMB
    SlapSubOption.RIGHT_THUMB -> FingerPosition.RIGHT_THUMB
}

// Thumbs go through the single-finger SDK activity (GuidelineScreen ->
// CameraScreen), same as Sequential capture -- Left/Right slap go through
// SlapCaptureLauncher's dedicated 4-finger activity.
val SlapSubOption.usesSingleFingerCapture: Boolean
    get() = this == SlapSubOption.LEFT_THUMB || this == SlapSubOption.RIGHT_THUMB