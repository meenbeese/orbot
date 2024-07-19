package org.torproject.android

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textview.MaterialTextView
import org.torproject.android.core.putNotSystem
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.service.util.Prefs

class KindnessFragment : Fragment() {

    private lateinit var tvAllTimeTotal: MaterialTextView
    private lateinit var tvWeeklyTotal: MaterialTextView
    private lateinit var swVolunteerMode: MaterialSwitch
    private lateinit var btnActionActivate: MaterialButton
    private lateinit var pnlActivate: View
    private lateinit var pnlStatus: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_kindness, container, false)
        tvAllTimeTotal = view.findViewById(R.id.tvAlltimeTotal)
        tvWeeklyTotal = view.findViewById(R.id.tvWeeklyTotal)
        swVolunteerMode = view.findViewById(R.id.swVolunteerMode)
        btnActionActivate = view.findViewById(R.id.btnActionActivate)
        pnlActivate = view.findViewById(R.id.panel_kindness_activate)
        pnlStatus = view.findViewById(R.id.panel_kindness_status)
        tvAllTimeTotal.text = Prefs.getSnowflakesServed().toString()
        tvWeeklyTotal.text = (Prefs.getSnowflakesServedWeekly()).toString()

        swVolunteerMode.isChecked = Prefs.beSnowflakeProxy()
        swVolunteerMode.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setBeSnowflakeProxy(isChecked)
            showPanelStatus(isChecked)
            sendIntentToService(OrbotConstants.CMD_SNOWFLAKE_PROXY)
        }

        view.findViewById<TextView>(R.id.swVolunteerAdjust).setOnClickListener {
            KindessConfigBottomSheet().show(requireActivity().supportFragmentManager, CustomBridgeBottomSheet.TAG)
        }

        btnActionActivate.setOnClickListener {
            swVolunteerMode.isChecked = true
        }

        showPanelStatus(Prefs.beSnowflakeProxy())
        return view
    }

    private fun sendIntentToService(action: String) {
        val intent = Intent(requireContext(), OrbotService::class.java)
            .setAction(action)
            .putNotSystem()
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    private fun showPanelStatus(isActivated: Boolean) {
        val duration = 250L
        if (isActivated) {
            pnlActivate.animate().alpha(0f).setDuration(duration).withEndAction {
                pnlActivate.visibility = View.GONE
            }

            pnlStatus.visibility = View.VISIBLE
            pnlStatus.animate().alpha(1f).duration = duration
        } else {
            pnlActivate.visibility = View.VISIBLE
            pnlActivate.animate().alpha(1f).duration = duration

            pnlStatus.animate().alpha(0f).setDuration(duration).withEndAction {
                pnlStatus.visibility = View.GONE
            }
        }
    }
}