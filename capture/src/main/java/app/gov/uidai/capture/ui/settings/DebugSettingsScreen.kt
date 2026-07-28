package app.gov.uidai.capture.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: DebugSettingsViewModel = hiltViewModel()
) {
    val groups = remember { viewModel.getSettings() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            groups.forEach { group ->
                item {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
                    )
                }
                items(group.all) { setting ->
                    SettingRow(setting = setting, viewModel = viewModel)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun SettingRow(setting: PreferenceParam<*>, viewModel: DebugSettingsViewModel) {
    when (setting.type) {
        is PreferenceType.BOOLEAN -> BooleanRow(setting as PreferenceParam<Boolean>, viewModel)
        is PreferenceType.INT -> NumberRow(setting as PreferenceParam<Int>, viewModel, isInt = true)
        is PreferenceType.FLOAT -> NumberRow(setting as PreferenceParam<Float>, viewModel, isInt = false)
        is PreferenceType.CHOICE<*> -> ChoiceRow(setting, viewModel)
    }
}

@Composable
private fun BooleanRow(setting: PreferenceParam<Boolean>, viewModel: DebugSettingsViewModel) {
    var checked by remember { mutableStateOf(viewModel.get(setting)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(setting.displayName, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = { newValue ->
                checked = newValue
                viewModel.save(setting.apply { currentValue = newValue })
            }
        )
    }
}

@Composable
private fun <T : Number> NumberRow(setting: PreferenceParam<T>, viewModel: DebugSettingsViewModel, isInt: Boolean) {
    var text by remember { mutableStateOf(viewModel.get(setting).toString()) }
    var isError by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(setting.displayName, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                val parsed: Number? = if (isInt) newText.toIntOrNull() else newText.toFloatOrNull()
                if (parsed != null) {
                    isError = false
                    @Suppress("UNCHECKED_CAST")
                    viewModel.save(setting.apply { currentValue = parsed as T })
                } else {
                    isError = true
                }
            },
            isError = isError,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isInt) KeyboardType.Number else KeyboardType.Decimal
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(setting: PreferenceParam<*>, viewModel: DebugSettingsViewModel) {
    @Suppress("UNCHECKED_CAST")
    val enumSetting = setting as PreferenceParam<Enum<*>>
    val choiceType = enumSetting.type as PreferenceType.CHOICE<*>
    val options = choiceType.options

    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(viewModel.get(enumSetting)) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(enumSetting.displayName, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(4.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected.name,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name) },
                        onClick = {
                            selected = option
                            expanded = false
                            viewModel.save(enumSetting.apply { currentValue = option })
                        }
                    )
                }
            }
        }
    }
}