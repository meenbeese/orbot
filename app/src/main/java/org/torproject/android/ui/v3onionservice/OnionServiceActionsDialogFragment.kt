package org.torproject.android.ui.v3onionservice

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.AdapterView
import android.widget.Toast

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

import org.torproject.android.R
import org.torproject.android.core.ClipboardUtils.copyToClipboard
import org.torproject.android.core.DiskUtils.createWriteFileIntent

class OnionServiceActionsDialogFragment internal constructor(arguments: Bundle?) :
    DialogFragment() {
    init {
        setArguments(arguments)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = arguments
        val ad = AlertDialog.Builder(requireActivity())
            .setItems(
                arrayOf(
                    getString(R.string.copy_address_to_clipboard),
                    Html.fromHtml(getString(R.string.backup_service), Html.FROM_HTML_MODE_LEGACY),
                    getString(R.string.delete_service)
                ), null
            )
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            .setTitle(R.string.hidden_services)
            .create()

        // Done this way so we can startActivityForResult on backup without the dialog vanishing
        ad.listView.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                when (position) {
                    0 -> doCopy(requireArguments(), context)
                    1 -> doBackup(requireArguments(), context)
                    2 -> {
                        OnionServiceDeleteDialogFragment(requireArguments()).show(
                            parentFragmentManager,
                            OnionServiceDeleteDialogFragment::class.java.simpleName
                        )
                    }
                }
                if (position != 1) dismiss()
            }
        return ad
    }

    private fun doCopy(arguments: Bundle, context: Context?) {
        val onion = arguments.getString(OnionServiceActivity.BUNDLE_KEY_DOMAIN)
        if (onion == null) {
            Toast.makeText(context, R.string.please_restart_Orbot_to_enable_the_changes, Toast.LENGTH_LONG).show()
        } else {
            copyToClipboard("onion", onion, getString(R.string.done), requireContext())
        }
    }

    private fun doBackup(arguments: Bundle, context: Context?) {
        val filename = "onion_service" + arguments.getString(OnionServiceActivity.BUNDLE_KEY_PORT) + ".zip"
        if (arguments.getString(OnionServiceActivity.BUNDLE_KEY_DOMAIN) == null) {
            Toast.makeText(context, R.string.please_restart_Orbot_to_enable_the_changes, Toast.LENGTH_LONG).show()
            return
        }
        val createFileIntent = createWriteFileIntent(filename, "application/zip")
        startActivityForResult(createFileIntent, REQUEST_CODE_WRITE_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_WRITE_FILE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                attemptToWriteBackup(data.data)
            }
        }
    }

    private fun attemptToWriteBackup(outputFile: Uri?) {
        val relativePath = requireArguments().getString(OnionServiceActivity.BUNDLE_KEY_PATH)
        val v3BackupUtils = V3BackupUtils(context)
        val backup = v3BackupUtils.createV3ZipBackup(relativePath, outputFile)
        val message = if (backup != null) R.string.backup_saved_at_external_storage else R.string.error
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        dismiss()
    }

    companion object {
        private const val REQUEST_CODE_WRITE_FILE = 343
    }
}
