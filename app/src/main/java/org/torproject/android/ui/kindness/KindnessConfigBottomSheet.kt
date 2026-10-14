package org.torproject.android.ui.kindness

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import org.torproject.android.databinding.KindnessConfigBottomSheetBinding
import org.torproject.android.ui.OrbotBottomSheetDialogFragment
import org.torproject.android.util.Prefs

class KindnessConfigBottomSheet : OrbotBottomSheetDialogFragment() {

    init {
        minMode = true
    }

    private lateinit var mBinding: KindnessConfigBottomSheetBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        mBinding = KindnessConfigBottomSheetBinding.inflate(inflater, container, false)
        mBinding.tvCancel.setOnClickListener { dismiss() }
        mBinding.btnAction.setOnClickListener {
            var changed = false
            if (mBinding.swKindnessConfigWifi.isChecked != Prefs.limitSnowflakeProxyingWifi()) {
                Prefs.setBeSnowflakeProxyLimitWifi(mBinding.swKindnessConfigWifi.isChecked)
                changed = true
            }
            if (mBinding.swKindnessConfigCharging.isChecked != Prefs.limitSnowflakeProxyingCharging()) {
                Prefs.setBeSnowflakeProxyLimitCharging(mBinding.swKindnessConfigCharging.isChecked)
                changed = true
            }

            if (changed) setFragmentResult(KEY_CONFIG_CHANGED, Bundle())
            dismiss()
        }

        mBinding.swKindnessConfigWifi.isChecked = Prefs.limitSnowflakeProxyingWifi()
        mBinding.swKindnessConfigCharging.isChecked = Prefs.limitSnowflakeProxyingCharging()

        return mBinding.root
    }

    companion object {
        const val KEY_CONFIG_CHANGED = "kindness_config_changed"
        fun show(fragmentManager: FragmentManager) =
            KindnessConfigBottomSheet().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_MIN_MODE, true)
                }
            }.show(
                fragmentManager,
                "KindnessConfig"
            )
    }
}
