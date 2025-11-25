package org.torproject.android.ui.settings

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.torproject.android.OrbotApp
import org.torproject.android.R
import org.torproject.android.service.OrbotConstants
import org.torproject.android.ui.core.BaseActivity
import org.torproject.android.util.Prefs
import org.torproject.android.util.sendIntentToService

class SettingsFragment : Fragment() {
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SettingsScreen(
                    categories = settingsCategories,
                    navController = findNavController(),
                    onSettingChanged = { setting ->
                        when (setting.key) {
                            "pref_flag_secure" -> {
                                if (setting is Setting.CheckBoxSetting) {
                                    Prefs.isSecureWindow = setting.value
                                    (activity as? BaseActivity)?.resetSecureFlags()
                                }
                            }
                            "pref_default_locale" -> {
                                if (setting is Setting.ListSetting) {
                                    val language = setting.selected ?: ""
                                    Prefs.defaultLocale = language
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val newLocale = LocaleListCompat.forLanguageTags(language)
                                        AppCompatDelegate.setApplicationLocales(newLocale)
                                    } else {
                                        requireContext().sendIntentToService(OrbotConstants.ACTION_LOCAL_LOCALE_SET)
                                        (requireContext().applicationContext as OrbotApp).setLocale()
                                    }
                                }
                            }
                            "pref_key_camo_dialog" -> {
                                findNavController().navigate(R.id.open_camo)
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!onBackPressedCallback.isEnabled) {
            onBackPressedCallback.isEnabled = true
        }
    }
}