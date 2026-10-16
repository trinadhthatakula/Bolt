package com.valhalla.bolt.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valhalla.bolt.R
import com.valhalla.bolt.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun ThemeSettingsScreen(modifier: Modifier = Modifier, store: ThemePreferencesStore = koinInject()) {
    val preferences by store.state.collectAsStateWithLifecycle()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode -> FilterChip(
                            selected = preferences.mode == mode, onClick = { store.setMode(mode) },
                            label = { Text(stringResource(when (mode) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            })) }
                        ) }
                    }
                    Text(stringResource(R.string.asgard_theme_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { AppearanceSwitch(stringResource(R.string.amoled_mode), stringResource(R.string.amoled_description), preferences.amoled, store::setAmoled) }
        item { AppearanceSwitch(
            stringResource(R.string.dynamic_colors),
            stringResource(if (Build.VERSION.SDK_INT >= 31) R.string.dynamic_colors_description else R.string.dynamic_colors_unavailable),
            preferences.dynamicColors, store::setDynamicColors, Build.VERSION.SDK_INT >= 31,
        ) }
        item {
            Text(stringResource(R.string.fonts), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.fonts_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(FontPreset.entries.size) { index ->
            val preset = FontPreset.entries[index]
            val fontStyles = typographyFor(preset)
            val technical = fontFamiliesFor(preset).technical
            val fontName = stringResource(if (preset == FontPreset.ASGARD) R.string.font_asgard else R.string.font_system)
            OutlinedCard(onClick = { store.setFontPreset(preset) }, modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "$fontName font"
                selected = preferences.fontPreset == preset
                role = Role.RadioButton
            }) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = preferences.fontPreset == preset, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(if (preset == FontPreset.ASGARD) R.string.font_asgard else R.string.font_system), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(stringResource(R.string.font_preview_heading), style = fontStyles.headlineMedium)
                    Text(stringResource(R.string.font_preview_body), style = fontStyles.bodyMedium)
                    Text(stringResource(R.string.font_preview_label), style = fontStyles.labelLarge)
                    Text("boot_a · SHA-256", fontFamily = technical, style = fontStyles.bodySmall)
                }
            }
        }
        item { Text(stringResource(R.string.font_size_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AppearanceSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Card {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
        }
    }
}
