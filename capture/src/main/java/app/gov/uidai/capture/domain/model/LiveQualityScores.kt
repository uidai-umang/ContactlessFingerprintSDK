package app.gov.uidai.capture.domain.model

data class LiveCheckScore(
    val label: String,
    val currentValue: Float,
    val acceptedMin: Float,
    val acceptedMax: Float,
    val passed: Boolean,
    val showValues: Boolean = false
) {
    companion object {
        fun default(label: String): LiveCheckScore = LiveCheckScore(
            label = label,
            currentValue = 0f,
            acceptedMin = 0f,
            acceptedMax = 0f,
            passed = false,
            showValues = false
        )
    }
}

data class LiveQualityScores(
    val blur: LiveCheckScore,
    val brightness: LiveCheckScore,
    val glare: LiveCheckScore,
    val fingerDetected: LiveCheckScore
) {
    companion object {
        fun defaultScores(): LiveQualityScores = LiveQualityScores(
            blur = LiveCheckScore.default("Blur"),
            brightness = LiveCheckScore.default("Brightness"),
            glare = LiveCheckScore.default("Glare"),
            fingerDetected = LiveCheckScore.default("Finger Detected")
        )
    }
}
