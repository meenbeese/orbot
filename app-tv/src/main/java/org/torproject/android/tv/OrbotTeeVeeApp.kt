package org.torproject.android.tv

import android.app.Application
import android.content.Context
import android.content.res.Configuration

import org.torproject.android.core.Languages.Companion.setLanguage
import org.torproject.android.core.Languages.Companion.setup
import org.torproject.android.core.LocaleHelper.onAttach
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.util.Prefs

import java.util.Locale

class OrbotTeeVeeApp : Application(), OrbotConstants {
    override fun onCreate() {
        super.onCreate()
        setup(TeeveeMainActivity::class.java, R.string.menu_settings)

        if (Prefs.getDefaultLocale() != Locale.getDefault().language) {
            setLanguage(this, Prefs.getDefaultLocale(), true)
        }

        // No hosting of onion services!
        Prefs.putHostOnionServicesEnabled(false)
    }

    override fun attachBaseContext(base: Context) {
        Prefs.setContext(base)
        super.attachBaseContext(onAttach(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (Prefs.getDefaultLocale() != Locale.getDefault().language) {
            setLanguage(this, Prefs.getDefaultLocale(), true)
        }
    }
}
