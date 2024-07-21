package org.torproject.android.connection

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log

import androidx.fragment.app.DialogFragment

import org.torproject.android.R
import org.torproject.android.service.util.Prefs
import org.torproject.android.service.util.Utils

import java.text.Collator
import java.util.Locale
import java.util.TreeMap

class ExitNodeDialogFragment(private val callback: ExitNodeSelectedCallback) : DialogFragment() {

    interface ExitNodeSelectedCallback {
        fun onExitNodeSelected(countryCode: String, displayCountryName: String)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentExit = Prefs.getExitNodes().replace("{", "").replace("}", "")
        val sortedCountries = TreeMap<String, Locale>(Collator.getInstance())
        COUNTRY_CODES.forEach {
            val locale = Locale("", it)
            sortedCountries[locale.displayCountry] = locale
        }

        val globe = getString(R.string.globe)

        val array = arrayOfNulls<String>(COUNTRY_CODES.size + 1)
        array[0] = "$globe ${getString(R.string.vpn_default_world)}"

        sortedCountries.keys.forEachIndexed { index, displayCountry ->
            val countryCode = sortedCountries[displayCountry]?.country ?: ""
            val checkmark = if (countryCode == currentExit) "✔ " else ""
            array[index + 1] = "$checkmark${Utils.convertCountryCodeToFlagEmoji(countryCode)} $displayCountry"
        }

        return AlertDialog.Builder(requireContext())
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .setTitle(R.string.btn_change_exit)
            .setItems(array) { _, pos ->
                val country = if (pos == 0) "" else sortedCountries.values.elementAtOrNull(pos - 1)?.country ?: ""
                callback.onExitNodeSelected(country, array[pos] ?: "")
                Log.d(TAG, "Country Code: $country, Display Country: ${array[pos]?.drop(5)}")
            }
            .create()
    }

    companion object {
        private val COUNTRY_CODES = arrayOf(
            "DE",
            "AT",
            "SE",
            "CH",
            "IS",
            "CA",
            "US",
            "ES",
            "FR",
            "BG",
            "PL",
            "AU",
            "BR",
            "CZ",
            "DK",
            "FI",
            "GB",
            "HU",
            "NL",
            "JP",
            "RO",
            "RU",
            "SG",
            "SK",
        )
        const val TAG = "ExitNodeDialogFragment"
    }
}