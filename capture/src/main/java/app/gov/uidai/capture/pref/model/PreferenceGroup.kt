package app.gov.uidai.capture.pref.model

interface PreferenceGroup {
    val title: String

    val all: List<PreferenceParam<*>>
        get() = this::class.members
            .filter { it.returnType.classifier == PreferenceParam::class }
            .sortedBy {
                val isEnable = it.name.startsWith("enable", ignoreCase = true)
                if (isEnable) -1 else {
                    val param = it.call(this) as PreferenceParam<*>
                    when (param.type) {
                        is PreferenceType.BOOLEAN -> 0
                        is PreferenceType.CHOICE<*> -> 1
                        else -> 2
                    }
                }
            }
            .map {
                it.call(this) as PreferenceParam<*>
            }
}