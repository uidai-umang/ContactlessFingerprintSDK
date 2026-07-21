package app.gov.uidai.capture.domain.model

// Distinct from BlurCheckMethodType (DenseNet-only, governs Stage 2's
// fixed dedicated check). This spans both model families — neural
// (DenseNet variants) and classical (Laplacian) — since the live Stage 1
// gate can now use either, per strategy, via Debug Settings.
enum class LiveBlurMethodType {
    Densenet,
    NewDensenet,
    Laplacian
}