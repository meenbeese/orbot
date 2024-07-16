package org.torproject.android.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View

import com.google.android.material.snackbar.Snackbar

object ClipboardUtils {
    @JvmStatic
    fun copyToClipboard(label: String, value: String, successMsg: String, context: Context): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Snackbar.make(View(context), successMsg, Snackbar.LENGTH_LONG).show()
        return true
    }
}