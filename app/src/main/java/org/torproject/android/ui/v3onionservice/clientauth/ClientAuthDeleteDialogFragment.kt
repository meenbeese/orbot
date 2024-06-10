package org.torproject.android.ui.v3onionservice.clientauth

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle

import androidx.fragment.app.DialogFragment

import org.torproject.android.R

class ClientAuthDeleteDialogFragment : DialogFragment {
    constructor()
    constructor(args: Bundle?) : super() {
        arguments = args
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(activity)
            .setTitle(R.string.v3_delete_client_authorization)
            .setPositiveButton(R.string.v3_delete_client_authorization_confirm) { _: DialogInterface?, _: Int -> doDelete() }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            .create()
    }

    private fun doDelete() {
        val id = requireArguments().getInt(ClientAuthActivity.BUNDLE_KEY_ID)
        requireContext().contentResolver.delete(
            ClientAuthContentProvider.CONTENT_URI,
            ClientAuthContentProvider.V3ClientAuth._ID + "=" + id,
            null
        )
    }
}
