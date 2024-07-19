package org.torproject.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import org.torproject.android.service.util.Prefs

class KindessConfigBottomSheet : OrbotBottomSheetDialogFragment() {

    private lateinit var btnAction: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.kindess_config_bottom_sheet, container, false)
        v.findViewById<View>(R.id.tvCancel).setOnClickListener { dismiss() }
        btnAction = v.findViewById(R.id.btnAction)

        val configWifi = v.findViewById<MaterialSwitch>(R.id.swKindnessConfigWifi)
        val configCharging = v.findViewById<MaterialSwitch>(R.id.swKindnessConfigCharging)

        btnAction.setOnClickListener {

            Prefs.setBeSnowflakeProxyLimitWifi(configWifi.isChecked)
            Prefs.setBeSnowflakeProxyLimitCharging(configCharging.isChecked)

            closeAllSheets()
        }

        configWifi.isChecked = Prefs.limitSnowflakeProxyingWifi()
        configCharging.isChecked = Prefs.limitSnowflakeProxyingCharging()
        return v
    }
}