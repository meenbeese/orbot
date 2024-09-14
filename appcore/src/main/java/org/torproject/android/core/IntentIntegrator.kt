package org.torproject.android.core

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log

import androidx.fragment.app.Fragment

/**
 *
 * A utility class which helps ease integration with Barcode Scanner via [Intent]s. This is a simple
 * way to invoke barcode scanning and receive the result, without any need to integrate, modify, or learn the
 * project's source code.
 */
class IntentIntegrator(private val activity: Activity) {
    private val fragment: Fragment? = null

    var title: String? = null
    var message: String? = null
    var buttonYes: String? = null
    var buttonNo: String? = null
    var targetApplications: List<String>? = null
    val moreExtras: MutableMap<String, Any> = HashMap(3)

    init {
        title = DEFAULT_TITLE
        message = DEFAULT_MESSAGE
        buttonYes = DEFAULT_YES
        buttonNo = DEFAULT_NO
        targetApplications = TARGET_ALL_KNOWN
    }

    @JvmOverloads
    fun initiateScan(
        desiredBarcodeFormats: Collection<String?>? = null,
        cameraId: Int = -1
    ): AlertDialog? {
        val intentScan = Intent("$BS_PACKAGE.SCAN").apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        // Set the desired barcode types
        desiredBarcodeFormats?.joinToString(",")?.let {
            intentScan.putExtra("SCAN_FORMATS", it)
        }

        // Set the requested camera ID
        if (cameraId >= 0) {
            intentScan.putExtra("SCAN_CAMERA_ID", cameraId)
        }

        val targetAppPackage = findTargetAppPackage(intentScan) ?: return showDownloadDialog()
        intentScan.apply {
            setPackage(targetAppPackage)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
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
        if (fragment == null) {
            activity.startActivityForResult(intent, code)
        } else {
            fragment.startActivityForResult(intent, code)
        }
    }

    private fun findTargetAppPackage(intent: Intent): String? {
        val pm = activity.packageManager
        val availableApps = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return targetApplications?.firstOrNull { targetApp ->
            availableApps.any { availableApp ->
                availableApp.activityInfo.packageName == targetApp
            }
        }
    }

    private fun showDownloadDialog(): AlertDialog {
        return AlertDialog.Builder(activity).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton(buttonYes) { _, _ ->
                val packageName = targetApplications?.let {
                    if (it.contains(BS_PACKAGE)) BS_PACKAGE else it[0]
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
        }.show()
    }

    @JvmOverloads
    fun shareText(text: CharSequence?, type: CharSequence? = "TEXT_TYPE"): AlertDialog? {
        val intent = Intent("$BS_PACKAGE.ENCODE").apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra("ENCODE_TYPE", type)
            putExtra("ENCODE_DATA", text)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
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
        for ((key, value) in moreExtras) {
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

        const val DEFAULT_TITLE = "Install Barcode Scanner?"
        const val DEFAULT_MESSAGE = "This application requires Barcode Scanner. Would you like to install it?"
        const val DEFAULT_YES = "Yes"
        const val DEFAULT_NO = "No"

        private const val BS_PACKAGE = "com.google.zxing.client.android"

        val TARGET_ALL_KNOWN = listOf(
            BS_PACKAGE // Barcode Scanner
        )

        /**
         * Parses the given request code, result code, and intent
         * to return it wrapped inside an IntentResult object.
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
            return if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
                val intentOrientation = intent.getIntExtra("SCAN_RESULT_ORIENTATION", Int.MIN_VALUE)
                IntentResult(
                    contents = intent.getStringExtra("SCAN_RESULT"),
                    formatName = intent.getStringExtra("SCAN_RESULT_FORMAT"),
                    rawBytes = intent.getByteArrayExtra("SCAN_RESULT_BYTES"),
                    orientation = intentOrientation.takeIf { it != Int.MIN_VALUE },
                    errorCorrectionLevel = intent.getStringExtra("SCAN_RESULT_ERROR_CORRECTION_LEVEL")
                )
            } else if (requestCode == REQUEST_CODE) {
                IntentResult()
            } else {
                null
            }
        }
    }
}
