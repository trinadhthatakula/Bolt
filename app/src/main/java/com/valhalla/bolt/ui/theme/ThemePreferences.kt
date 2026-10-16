package com.valhalla.bolt.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class FontPreset(val storageValue: String) {
    ASGARD("asgard"), SYSTEM("system");

    companion object {
        fun fromStorageValue(value: String?) = entries.firstOrNull { it.storageValue == value } ?: ASGARD
    }
}

data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColors: Boolean = false,
    val fontPreset: FontPreset = FontPreset.ASGARD,
)

/** Independent of device-bound recovery records; appearance applies immediately across routes. */
class ThemePreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mutable.value = read() }

    init { preferences.registerOnSharedPreferenceChangeListener(listener) }

    private fun read() = ThemePreferences(
        mode = runCatching { ThemeMode.valueOf(preferences.getString("theme_mode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        amoled = preferences.getBoolean("amoled", false),
        dynamicColors = preferences.getBoolean("dynamic_colors", false),
        fontPreset = FontPreset.fromStorageValue(preferences.getString("font_preset", null)),
    )

    fun setMode(value: ThemeMode) { preferences.edit().putString("theme_mode", value.name).apply() }
    fun setAmoled(value: Boolean) { preferences.edit().putBoolean("amoled", value).apply() }
    fun setDynamicColors(value: Boolean) { preferences.edit().putBoolean("dynamic_colors", value).apply() }
    fun setFontPreset(value: FontPreset) { preferences.edit().putString("font_preset", value.storageValue).apply() }
}
