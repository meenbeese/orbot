package org.torproject.android.ui.settings

sealed class Setting(val key: String, val title: String, val summary: String? = null) {
    data class CheckBoxSetting(
        val k: String,
        val t: String,
        val s: String? = null,
        var value: Boolean = false
    ) : Setting(k, t, s)

    data class ListSetting(
        val k: String,
        val t: String,
        val options: List<String>,
        val optionValues: List<String>,
        var selected: String? = null
    ) : Setting(k, t)

    data class TextSetting(
        val k: String,
        val t: String,
        val s: String? = null,
        var value: String = ""
    ) : Setting(k, t, s)
}

data class SettingsCategory(
    val title: String,
    val settings: List<Setting>
)
