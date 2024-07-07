package org.torproject.android.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import com.google.android.material.floatingactionbutton.FloatingActionButton

import org.torproject.android.OrbotBottomSheetDialogFragment
import org.torproject.android.R
import org.torproject.android.core.ClipboardUtils.copyToClipboard

class LogBottomSheet : OrbotBottomSheetDialogFragment() {

    private lateinit var tvLog: TextView
    private val buffer = StringBuilder()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.log_bottom_sheet, container, false)
        tvLog = v.findViewById(R.id.orbotLog)
        tvLog.text = buffer.toString()

        v.findViewById<FloatingActionButton>(R.id.btnCopyLog).setOnClickListener {
            copyToClipboard("log", tvLog.text.toString(), getString(R.string.log_copied), requireContext())
        }
        return v
    }

    fun appendLog(logLine: String) {
        if (this::tvLog.isInitialized) {
            tvLog.append(logLine)
            tvLog.append("\n")
        }
        buffer.append(logLine).append("\n")
    }

    fun resetLog() {
        if (this::tvLog.isInitialized) tvLog.text = ""
        buffer.clear()
    }
}