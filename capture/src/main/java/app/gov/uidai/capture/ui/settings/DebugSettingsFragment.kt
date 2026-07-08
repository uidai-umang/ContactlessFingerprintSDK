package app.gov.uidai.capture.ui.settings

import android.os.Bundle
import android.text.InputType
import androidx.fragment.app.viewModels
import androidx.preference.DropDownPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType
import dagger.hilt.android.AndroidEntryPoint
import app.gov.uidai.capture.R
import kotlin.collections.forEach

@AndroidEntryPoint
class DebugSettingsFragment : PreferenceFragmentCompat() {

    private val viewModel: DebugSettingsViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.debug_preferences, rootKey)
        populatePreferences()
    }

    private fun populatePreferences() {
        val context = preferenceManager.context
        val screen = preferenceScreen

        viewModel.getSettings().forEach { group ->
            val category = PreferenceCategory(context).apply {
                title = group.title
            }
            screen.addPreference(category)

            group.all.forEach { setting ->
                val preference = createPreference(setting)
                category.addPreference(preference)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createPreference(setting: PreferenceParam<*>): Preference {
        return when (setting.type) {
            is PreferenceType.BOOLEAN -> createSwitchPreference(setting as PreferenceParam<Boolean>)
            is PreferenceType.INT -> createEditTextPreference(setting as PreferenceParam<Int>)
            is PreferenceType.FLOAT -> createEditTextPreference(setting as PreferenceParam<Float>)
            is PreferenceType.CHOICE<*> -> createDropDownPreference(setting)
        }
    }

    private fun createSwitchPreference(setting: PreferenceParam<Boolean>): SwitchPreferenceCompat {
        return SwitchPreferenceCompat(requireContext()).apply {
            key = setting.key
            title = setting.displayName
            isChecked = viewModel.get(setting)
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                viewModel.save(setting.apply { currentValue = newValue as Boolean })
                true
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createEditTextPreference(setting: PreferenceParam<out Number>): EditTextPreference {
        return EditTextPreference(requireContext()).apply {
            key = setting.key
            title = setting.displayName
            text = viewModel.get(setting).toString()
            summary = text
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
                try {
                    val numberValue: Number = when (setting.type) {
                        is PreferenceType.INT -> (newValue as String).toInt()
                        is PreferenceType.FLOAT -> (newValue as String).toFloat()
                        else -> 0
                    }
                    viewModel.save(setting.apply {
                        (this as PreferenceParam<Number>).currentValue = numberValue
                    })
                    pref.summary = newValue.toString()
                    true
                } catch (e: NumberFormatException) {
                    false
                }
            }
            setOnBindEditTextListener { editText ->
                editText.inputType = when (setting.type) {
                    is PreferenceType.INT -> InputType.TYPE_CLASS_NUMBER
                    is PreferenceType.FLOAT -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    else -> InputType.TYPE_CLASS_TEXT
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createDropDownPreference(setting: PreferenceParam<*>): DropDownPreference {
        val enumSetting = setting as PreferenceParam<Enum<*>>
        val choiceType = enumSetting.type as PreferenceType.CHOICE<*>
        val options = choiceType.options.map { it.name }

        return DropDownPreference(requireContext()).apply {
            key = enumSetting.key
            title = enumSetting.displayName
            entries = options.toTypedArray()
            entryValues = options.toTypedArray()
            value = (viewModel.get(enumSetting)).name
            summary = value
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { pref, newValue ->
                val selectedEnum = choiceType.options.find { it.name == newValue.toString() }
                if (selectedEnum != null) {
                    viewModel.save(enumSetting.apply {
                        this.currentValue = selectedEnum
                    })
                    pref.summary = newValue.toString()
                }
                true
            }
        }
    }
}