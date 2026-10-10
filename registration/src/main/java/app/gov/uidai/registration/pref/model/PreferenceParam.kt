package app.gov.uidai.registration.pref.model

sealed class PreferenceType<T> {
    data object BOOLEAN : PreferenceType<Boolean>()
    data object INT : PreferenceType<Int>()
    data object DOUBLE : PreferenceType<Double>()
    data object STRING : PreferenceType<String>()
    data class CHOICE<T : Enum<T>>(val options: List<T>) : PreferenceType<T>()
}

data class PreferenceParam<T>(
    val key: String,
    val displayName: String,
    val type: PreferenceType<T>,
    val defaultValue: T,
    var currentValue: T = defaultValue
)