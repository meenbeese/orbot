/*
 * Copyright 2009 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.zxing.integration.android

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log

import androidx.fragment.app.Fragment

/**
 *
 * A utility class which helps ease integration with Barcode Scanner via [Intent]s. This is a simple
 * way to invoke barcode scanning and receive the result, without any need to integrate, modify, or learn the
 * project's source code.
 *
 * @author Sean Owen
 * @author Fred Lin
 * @author Isaac Potoczny-Jones
 * @author Brad Drehmer
 * @author gcstang
 */
class IntentIntegrator(private val activity: Activity) {
    private val fragment: Fragment? = null

    internal var title: String? = null
    private var message: String? = null
    private var buttonYes: String? = null
    private var buttonNo: String? = null
    private var targetApplications: List<String>? = null
    private val moreExtras: MutableMap<String, Any> = HashMap(3)

    init {
        title = DEFAULT_TITLE
        message = DEFAULT_MESSAGE
        buttonYes = DEFAULT_YES
        buttonNo = DEFAULT_NO
        targetApplications = TARGET_BARCODE_SCANNER_ONLY
    }

    /**
     * Initiates a scan, using the specified camera, only for a certain set of barcode types, given as strings corresponding
     * to their names in ZXing's `BarcodeFormat` class like "UPC_A". You can supply constants
     * like [.PRODUCT_CODE_TYPES] for example.
     *
     * @param desiredBarcodeFormats names of `BarcodeFormat`s to scan for
     * @param cameraId camera ID of the camera to use. A negative value means "no preference".
     * @return the [AlertDialog] that was shown to the user prompting them to download the app
     * if a prompt was needed, or null otherwise
     */
    @JvmOverloads
    fun initiateScan(
        desiredBarcodeFormats: Collection<String?>? = ALL_CODE_TYPES,
        cameraId: Int = -1
    ): AlertDialog? {
        val intentScan = Intent("$BS_PACKAGE.SCAN").apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        // Check which types of codes to scan for
        desiredBarcodeFormats?.joinToString(",")?.let {
            intentScan.putExtra("SCAN_FORMATS", it)
        }

        // Check requested camera ID
        if (cameraId >= 0) {
            intentScan.putExtra("SCAN_CAMERA_ID", cameraId)
        }

        val targetAppPackage = findTargetAppPackage(intentScan) ?: return showDownloadDialog()
        intentScan.apply {
            setPackage(targetAppPackage)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        }
        attachMoreExtras(intentScan)
        startActivityForResult(intentScan, REQUEST_CODE)
        return null
    }

    /**
     * Start an activity. This method is defined to allow different methods of activity starting for
     * newer versions of Android and for compatibility library.
     *
     * @param intent Intent to start.
     * @param code Request code for the activity
     * @see android.app.Activity.startActivityForResult
     * @see android.app.Fragment.startActivityForResult
     */
    private fun startActivityForResult(intent: Intent?, code: Int) {
        intent?.let {
            if (fragment == null) {
                activity.startActivityForResult(it, code)
            } else {
                fragment.startActivityForResult(it, code)
            }
        }
    }

    private fun findTargetAppPackage(intent: Intent): String? {
        val pm = activity.packageManager
        val availableApps = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return targetApplications?.firstOrNull { targetApp ->
            contains(availableApps, targetApp)
        }
    }

    private fun showDownloadDialog(): AlertDialog {
        val downloadDialog = AlertDialog.Builder(activity).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton(buttonYes) { _, _ ->
                val packageName = if (targetApplications!!.contains(BS_PACKAGE)) {
                    BS_PACKAGE
                } else {
                    targetApplications!![0]
                }
                val uri = Uri.parse("market://details?id=$packageName")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                try {
                    if (fragment == null) {
                        activity.startActivity(intent)
                    } else {
                        fragment.startActivity(intent)
                    }
                } catch (anfe: ActivityNotFoundException) {
                    Log.w(TAG, "Google Play is not installed; cannot install $packageName")
                }
            }
            setNegativeButton(buttonNo, null)
            setCancelable(true)
        }
        return downloadDialog.show()
    }

    /**
     * Shares the given text by encoding it as a barcode, such that another user can
     * scan the text off the screen of the device.
     *
     * @param text the text string to encode as a barcode
     * @param type type of data to encode. See `com.google.zxing.client.android.Contents.Type` constants.
     * @return the [AlertDialog] that was shown to the user prompting them to download the app
     * if a prompt was needed, or null otherwise
     */
    @JvmOverloads
    fun shareText(text: CharSequence?, type: CharSequence? = "TEXT_TYPE"): AlertDialog? {
        val intent = Intent().apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            action = "$BS_PACKAGE.ENCODE"
            putExtra("ENCODE_TYPE", type)
            putExtra("ENCODE_DATA", text)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        }

        val targetAppPackage = findTargetAppPackage(intent) ?: return showDownloadDialog()
        intent.setPackage(targetAppPackage)
        attachMoreExtras(intent)

        if (fragment == null) {
            activity.startActivity(intent)
        } else {
            fragment.startActivity(intent)
        }
        return null
    }

    private fun attachMoreExtras(intent: Intent) {
        moreExtras.forEach { (key, value) ->
            when (value) {
                is Int -> intent.putExtra(key, value)
                is Long -> intent.putExtra(key, value)
                is Boolean -> intent.putExtra(key, value)
                is Double -> intent.putExtra(key, value)
                is Float -> intent.putExtra(key, value)
                is Bundle -> intent.putExtra(key, value)
                else -> intent.putExtra(key, value.toString())
            }
        }
    }

    companion object {
        const val REQUEST_CODE: Int = 0x0000c0de // Only use bottom 16 bits
        private val TAG: String = IntentIntegrator::class.java.simpleName

        const val DEFAULT_TITLE: String = "Install Barcode Scanner?"
        const val DEFAULT_MESSAGE: String = "This application requires Barcode Scanner. Would you like to install it?"
        const val DEFAULT_YES: String = "Yes"
        const val DEFAULT_NO: String = "No"

        private const val BS_PACKAGE = "com.google.zxing.client.android"

        val ALL_CODE_TYPES: Collection<String?>? = null
        val TARGET_BARCODE_SCANNER_ONLY: List<String> = listOf(BS_PACKAGE)

        private fun contains(availableApps: Iterable<ResolveInfo>, targetApp: String): Boolean {
            for (availableApp in availableApps) {
                val packageName = availableApp.activityInfo.packageName
                if (targetApp == packageName) {
                    return true
                }
            }
            return false
        }

        /**
         *
         * Call this from your [Activity]'s
         * [Activity.onActivityResult] method.
         *
         * @param requestCode request code from `onActivityResult()`
         * @param resultCode result code from `onActivityResult()`
         * @param intent [Intent] from `onActivityResult()`
         * @return null if the event handled here was not related to this class, or
         * else an [IntentResult] containing the result of the scan. If the user cancelled scanning,
         * the fields will be null.
         */
        @JvmStatic
        fun parseActivityResult(requestCode: Int, resultCode: Int, intent: Intent): IntentResult? {
            if (requestCode == REQUEST_CODE) {
                if (resultCode == Activity.RESULT_OK) {
                    val contents = intent.getStringExtra("SCAN_RESULT")
                    val formatName = intent.getStringExtra("SCAN_RESULT_FORMAT")
                    val rawBytes = intent.getByteArrayExtra("SCAN_RESULT_BYTES")
                    val intentOrientation = intent.getIntExtra("SCAN_RESULT_ORIENTATION", Int.MIN_VALUE)
                    val orientation = if (intentOrientation == Int.MIN_VALUE) null else intentOrientation
                    val errorCorrectionLevel = intent.getStringExtra("SCAN_RESULT_ERROR_CORRECTION_LEVEL")
                    return IntentResult(
                        contents,
                        formatName,
                        rawBytes,
                        orientation,
                        errorCorrectionLevel
                    )
                }
                return IntentResult()
            }
            return null
        }
    }
}
