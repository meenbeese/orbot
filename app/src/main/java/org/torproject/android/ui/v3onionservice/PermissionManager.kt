package org.torproject.android.ui.v3onionservice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.View

import androidx.fragment.app.FragmentActivity

import com.google.android.material.snackbar.Snackbar

import org.torproject.android.R

object PermissionManager {
    private const val SNACK_BAR_DURATION = 5000

    @JvmStatic
    fun requestBatteryPermissions(activity: FragmentActivity, view: View) {
        val packageName = activity.packageName
        val pm = activity.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            showSnackbar(
                view,
                R.string.consider_disable_battery_optimizations,
                R.string.disable
            ) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                activity.startActivity(intent)
            }
        }
    }

    @JvmStatic
    fun requestDropBatteryPermissions(activity: FragmentActivity, view: View) {
        val pm = activity.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(activity.packageName)) {
            showSnackbar(
                view,
                R.string.consider_enable_battery_optimizations,
                R.string.enable
            ) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                activity.startActivity(intent)
            }
        }
    }

    private fun showSnackbar(view: View, messageResId: Int, actionResId: Int, action: () -> Unit) {
        Snackbar.make(view, messageResId, SNACK_BAR_DURATION).setAction(actionResId) {
            action()
        }.show()
    }
}
