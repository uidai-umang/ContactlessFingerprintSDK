package `in`.gov.uidai.embedding.model

data class FingerQualityAttribute(
    val name: String,
    val score: Double = 0.0,
    val comments: String,
    val compliance: String
) {
    fun getFormatedName(): String {
        return name.removePrefix("FINGER_").replace('_', ' ')
    }
}