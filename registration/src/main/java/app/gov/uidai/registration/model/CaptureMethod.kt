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
    THUMBS
}
