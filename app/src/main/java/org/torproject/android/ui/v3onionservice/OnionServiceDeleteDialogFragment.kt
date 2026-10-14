package org.torproject.android.ui.v3onionservice

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.provider.BaseColumns

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

import org.torproject.android.R
import org.torproject.android.util.DiskUtils.recursivelyDeleteDirectory
import org.torproject.android.service.OrbotConstants

import java.io.File

class OnionServiceDeleteDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_service_deletion)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int -> doDelete(arguments, requireContext()) }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.cancel() }
            .create()
    }

    companion object {
        const val TAG = "OnionServiceDeleteDialog"
        @JvmStatic
        fun show(fragmentManager: FragmentManager, arguments: Bundle?) =
            OnionServiceDeleteDialogFragment().apply {
                this.arguments = arguments
            }.show(fragmentManager, TAG)
    }

    private fun doDelete(arguments: Bundle?, context: Context) {
        context.contentResolver.delete(
            OnionServiceContentProvider.CONTENT_URI,
            BaseColumns._ID + '=' + requireArguments().getInt(
                OnionServiceActivity.BUNDLE_KEY_ID
            ),
            null
        )
        val base = context.filesDir.absolutePath + "/" + OrbotConstants.ONION_SERVICES_DIR
        val localPath = arguments?.getString(OnionServiceActivity.BUNDLE_KEY_PATH)
        localPath?.let { recursivelyDeleteDirectory(File(base, localPath)) }
        (requireActivity() as OnionServiceActivity).showBatteryOptimizationsMessageIfAppropriate()
    }
}
