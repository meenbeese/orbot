package org.torproject.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingItem(
    setting: Setting,
    onSettingChanged: (Setting) -> Unit
) {
    when (setting) {
        is Setting.CheckBoxSetting -> {
            var checked by remember { mutableStateOf(setting.value) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = setting.title,
                        color = Color.White
                    )
                    setting.summary?.let {
                        Text(
                            text = it,
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        setting.value = it
                        onSettingChanged(setting)
                    }
                )
            }
        }

        is Setting.ListSetting -> {
            var expanded by remember { mutableStateOf(false) }
            var selectedOption by remember { mutableStateOf(setting.selected ?: "") }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = setting.title,
                    color = Color.White
                )
                setting.summary?.let {
                    Text(
                        text = it,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = selectedOption,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(setting.title) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        setting.options.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedOption = option
                                    setting.selected = setting.optionValues[index]
                                    onSettingChanged(setting)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        is Setting.TextSetting -> {
            var textValue by remember { mutableStateOf(setting.value) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = setting.title,
                    color = Color.White
                )
                setting.summary?.let {
                    Text(
                        text = it,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        setting.value = it
                        onSettingChanged(setting)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
