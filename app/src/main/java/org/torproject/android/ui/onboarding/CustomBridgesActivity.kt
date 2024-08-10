/* Copyright (c) 2020, Benjamin Erhart, Orbot / The Guardian Project - https://guardianproject.info */
/* See LICENSE for licensing information */
package org.torproject.android.ui.onboarding

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

import com.google.zxing.integration.android.IntentIntegrator

import net.freehaven.tor.control.TorControlCommands

import org.json.JSONArray
import org.torproject.android.R
import org.torproject.android.core.ClipboardUtils.copyToClipboard
import org.torproject.android.service.OrbotService
import org.torproject.android.service.util.Prefs

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class CustomBridgesActivity : AppCompatActivity(), TextWatcher {
    private var mEtPastedBridges: EditText? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_bridges)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<TextView>(R.id.tvDescription).text = getString(R.string.in_a_browser, URL_TOR_BRIDGES)

        findViewById<View>(R.id.btCopyUrl).setOnClickListener {
            copyToClipboard("bridge_url", URL_TOR_BRIDGES, getString(R.string.done), this)
        }

        var bridges: String? = Prefs.getBridgesList().trim { it <= ' ' }
        if (!Prefs.bridgesEnabled() || userHasSetPreconfiguredBridge(bridges)) {
            bridges = null
        }

        mEtPastedBridges = findViewById(R.id.etPastedBridges)
        mEtPastedBridges?.setOnTouchListener { v, event ->
            if (v.hasFocus()) {
                v.parent.requestDisallowInterceptTouchEvent(true)
                if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_SCROLL) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    return@setOnTouchListener true
                }
            }
            false
        }

        mEtPastedBridges?.setText(bridges)
        mEtPastedBridges?.addTextChangedListener(this)
        val integrator = IntentIntegrator(this)

        findViewById<View>(R.id.btScanQr).setOnClickListener { integrator.initiateScan() }
        findViewById<View>(R.id.btShareQr).setOnClickListener { shareQrCode(integrator) }
        findViewById<View>(R.id.btEmail).setOnClickListener { sendEmail() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(request: Int, response: Int, data: Intent?) {
        super.onActivityResult(request, response, data)

        val scanResult = IntentIntegrator.parseActivityResult(request, response, data)

        scanResult?.contents?.let { results ->
            handleScanResult(results)
            setResult(RESULT_OK)
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { /* no-op */ }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { /* no-op */ }

    override fun afterTextChanged(editable: Editable) {
        setNewBridges(editable.toString(), false)
    }

    private fun setNewBridges(bridges: String, updateEditText: Boolean = true) {
        var trimmedBridges: String? = bridges.trim { it <= ' ' }
        if (TextUtils.isEmpty(trimmedBridges)) {
            trimmedBridges = null
        }

        if (updateEditText) {
            mEtPastedBridges?.setText(trimmedBridges)
        }

        Prefs.setBridgesList(trimmedBridges)
        Prefs.putBridgesEnabled(trimmedBridges != null)

        val intent = Intent(this, OrbotService::class.java).apply {
            action = TorControlCommands.SIGNAL_RELOAD
        }
        startService(intent)
    }

    private fun shareQrCode(integrator: IntentIntegrator) {
        val setBridges = Prefs.getBridgesList()
        if (!TextUtils.isEmpty(setBridges)) {
            try {
                integrator.shareText("bridge://${URLEncoder.encode(setBridges, "UTF-8")}")
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }
        }
    }

    private fun sendEmail() {
        val requestText = "get transport"
        val emailUrl = String.format(
            "mailto:%s?subject=%s&body=%s",
            Uri.encode(EMAIL_TOR_BRIDGES),
            Uri.encode(requestText),
            Uri.encode(requestText)
        )
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse(emailUrl)).apply {
            putExtra(Intent.EXTRA_SUBJECT, requestText)
            putExtra(Intent.EXTRA_TEXT, requestText)
        }
        startActivity(Intent.createChooser(emailIntent, getString(R.string.send_email)))
    }

    private fun handleScanResult(results: String) {
        try {
            val urlIdx = results.indexOf("://")
            if (urlIdx != -1) {
                val decodedResults = URLDecoder.decode(results, DEFAULT_ENCODING)
                setNewBridges(decodedResults.substring(urlIdx + 3))
            } else {
                val bridgeJson = JSONArray(results)
                val bridgeLines = StringBuilder()
                for (i in 0 until bridgeJson.length()) {
                    bridgeLines.append(bridgeJson.getString(i)).append("\n")
                }
                setNewBridges(bridgeLines.toString())
            }
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "unsupported", e)
        }
    }

    companion object {
        private val DEFAULT_ENCODING: String = StandardCharsets.UTF_8.name()
        private const val EMAIL_TOR_BRIDGES = "bridges@torproject.org"
        private const val URL_TOR_BRIDGES = "https://bridges.torproject.org/bridges"

        private fun userHasSetPreconfiguredBridge(bridges: String?): Boolean {
            return bridges in listOf("obfs4", "meek", "snowflake", "snowflake-amp")
        }
    }
}
