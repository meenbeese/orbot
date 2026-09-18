package org.torproject.android.ui.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import org.torproject.android.R
import org.torproject.android.databinding.LogBottomSheetBinding
import org.torproject.android.util.showToast
import org.torproject.android.ui.OrbotBottomSheetDialogFragment
import org.torproject.android.util.Prefs

class LogBottomSheet : OrbotBottomSheetDialogFragment() {

    lateinit var binding: LogBottomSheetBinding

    override fun onStart() {
        super.onStart()
        PreferenceManager
            .getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(logPrefObserver)
        binding.orbotLog.text = Prefs.getOrbotServiceLog()
        scrollToBottom()
    }

    override fun onStop() {
        super.onStop()
        PreferenceManager
            .getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(logPrefObserver)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = LogBottomSheetBinding.inflate(layoutInflater)
        binding.orbotLog.text = Prefs.getOrbotServiceLog()
        binding.btnCopyLog.setOnClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("log", binding.orbotLog.text)
            clipboard.setPrimaryClip(clip)
            requireContext().showToast(R.string.log_copied)
        }
        scrollToBottom()
        return binding.root
    }

    private fun scrollToBottom() {
        binding.logScrollView.post {
            binding.logScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private val logPrefObserver =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key != Prefs.PREF_ORBOT_SERVICE_LOG) return@OnSharedPreferenceChangeListener
            val newLog = Prefs.getOrbotServiceLog()
            val delta = appendableDelta(binding.orbotLog.text.toString(), newLog)
            if (delta != null) binding.orbotLog.append(delta) else binding.orbotLog.text = newLog
            scrollToBottom()
        }

    companion object {
        const val TAG = "LogBottomSheet"
        fun show(fragmentManager: FragmentManager) {
            LogBottomSheet().show(fragmentManager, TAG)
        }

        // the service clears and regrows the stored log across restarts, so longer does not mean grown
        fun appendableDelta(displayed: String, stored: String): String? =
            if (stored.length > displayed.length && stored.startsWith(displayed))
                stored.substring(displayed.length)
            else null
    }

}