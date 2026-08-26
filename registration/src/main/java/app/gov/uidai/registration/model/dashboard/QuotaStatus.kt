package app.gov.uidai.registration.model.dashboard

enum class QuotaStatus {
    OPEN,
    WARN,
    FULL,
    UNKNOWN;

    companion object {
        fun fromRaw(value: String?): QuotaStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
