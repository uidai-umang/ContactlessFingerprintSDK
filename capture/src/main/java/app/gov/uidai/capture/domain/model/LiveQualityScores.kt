package app.gov.uidai.capture.domain.model

data class LiveCheckScore(
    val label: String,
    val currentValue: Float,
    val acceptedMin: Float,
    val acceptedMax: Float,
    val passed: Boolean
)

data class LiveQualityScores(
    val blur: LiveCheckScore,
    val brightness: LiveCheckScore,
    val glare: LiveCheckScore,
    val fingerDetected: LiveCheckScore
)
