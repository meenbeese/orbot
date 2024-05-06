package org.torproject.android.core

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

import org.torproject.android.service.OrbotConstants

/**
 * Used to build Intents in Orbot, annoyingly we have to set this when passing Intents to
 * OrbotService to distinguish between Intents that are triggered from this codebase VS
 * Intents that the system sends to the VPNService on boot...
 */
fun Intent.putNotSystem(): Intent = this.putExtra(OrbotConstants.EXTRA_NOT_SYSTEM, true)

/**
 * Retrieves package information for a given package name. This function is compatible with
 * different Android versions. For Android Tiramisu and above, it uses the new method of
 * getting package info. For older versions, it uses the deprecated method.
 *
 * @param packageName The name of the package.
 * @param flags Additional option flags. Use 0 for default behavior.
 * @return The package information.
 */
fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
    }
