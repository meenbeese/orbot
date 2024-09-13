package org.torproject.android.core.ui

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo

import androidx.annotation.XmlRes
import androidx.preference.*

import org.torproject.android.core.Languages
import org.torproject.android.core.R
import org.torproject.android.service.util.Prefs

class SettingsPreferencesFragment : PreferenceFragmentCompat() {
    private val PreferenceGroup.preferences: List<Preference>
        get() = (0 until preferenceCount).map { getPreference(it) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        initPrefs()
    }

    private fun initPrefs() {
        setNoPersonalizedLearningOnEditTextPreferences()

        findPreference<ListPreference>("pref_default_locale")?.apply {
            val languages = Languages[requireActivity()]
            entries = languages?.allNames
            entryValues = languages?.supportedLocales
            value = Prefs.getDefaultLocale()
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val language = newValue as String?
                requireActivity().apply {
                    setResult(RESULT_OK, Intent().putExtra("locale", language))
                    finish()
                }
                false
            }
        }

        val bridgesEnabled = Prefs.bridgesEnabled()
        findPreference<Preference>("pref_be_a_snowflake")?.isEnabled = !bridgesEnabled
        findPreference<Preference>("pref_be_a_snowflake_limit")?.isEnabled = !bridgesEnabled

        // kludge for #992
        val categoryNodeConfig = findPreference<Preference>("category_node_config")
        categoryNodeConfig?.title = "${categoryNodeConfig?.title}" + "\n\n" + "${categoryNodeConfig?.summary}"
        categoryNodeConfig?.summary = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // if defined in XML, disable the persistent notification preference on Oreo+
            findPreference<Preference>("pref_persistent_notifications")?.let {
                it.parent?.removePreference(it)
            }
        }

        val prefFlagSecure = findPreference<CheckBoxPreference>("pref_flag_secure")
        prefFlagSecure?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any? ->
            Prefs.setSecureWindow(newValue as Boolean)
            (activity as BaseActivity).resetSecureFlags()

            true
        }
    }

    private fun setNoPersonalizedLearningOnEditTextPreferences() {
        preferenceScreen.preferences
            .filterIsInstance<PreferenceCategory>()
            .flatMap { it.preferences }
            .filterIsInstance<EditTextPreference>()
            .forEach { preference ->
                preference.setOnBindEditTextListener {
                    it.imeOptions = it.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
            }
    }

    companion object {
        private const val BUNDLE_KEY_PREFERENCES_XML = "prefxml"

        @JvmStatic
        fun createIntent(context: Context?, @XmlRes xmlPrefId: Int): Intent =
                Intent(context, SettingsPreferencesFragment::class.java).apply {
                    putExtra(BUNDLE_KEY_PREFERENCES_XML, xmlPrefId)
                }
    }
}
