package `in`.gov.uidai.embedding.model


data class FingerQuality(
    val overallScore: Double,
    val compliance: String,
    val attributes: List<FingerQualityAttribute>,
    val comment: String
) {
    fun getAttributeScore(name: String): Double? {
        return attributes.find { it.name.contains(name) }?.score
    }

    fun getMinutia(): Double?{
        return getAttributeScore("MINUTIA_COUNT")
    }

    fun getPropreietaryQuality(): Double?{
        return getAttributeScore("PROPREIETARY_QUALITY")
    }

    fun getContactArea(): Double?{
        return getAttributeScore("CONTACT_AREA")
    }
}
