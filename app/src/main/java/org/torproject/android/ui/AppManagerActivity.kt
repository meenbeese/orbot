/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package org.torproject.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.GridView
import android.widget.ImageView
import android.widget.ListAdapter
import android.widget.ProgressBar
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.torproject.android.BuildConfig
import org.torproject.android.R
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.util.Prefs
import org.torproject.android.service.vpn.TorifiedApp

class AppManagerActivity : AppCompatActivity(), View.OnClickListener, OrbotConstants {
    inner class TorifiedAppWrapper {
        var header: String? = null
        var subheader: String? = null
        var app: TorifiedApp? = null
    }

    private var pMgr: PackageManager? = null
    private var mPrefs: SharedPreferences? = null
    private var listAppsAll: GridView? = null
    private var adapterAppsAll: ListAdapter? = null
    private var progressBar: ProgressBar? = null
    private var alSuggested: List<String>? = null

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pMgr = packageManager
        this.setContentView(R.layout.layout_apps)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        listAppsAll = findViewById(R.id.applistview)
        progressBar = findViewById(R.id.progressBar)

        // Need a better way to manage this list
        alSuggested = OrbotConstants.VPN_SUGGESTED_APPS
    }

    override fun onResume() {
        super.onResume()
        mPrefs = Prefs.getSharedPrefs(applicationContext)
        reloadApps()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.app_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_save_apps) {
            saveAppSettings()
            finish()
        } else if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun reloadApps() {
        scope.launch {
            progressBar?.visibility = View.VISIBLE
            withContext(Dispatchers.IO) {
                loadApps()
            }
            listAppsAll?.adapter = adapterAppsAll
            progressBar?.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private var allApps: List<TorifiedApp>? = null
    private var suggestedApps: List<TorifiedApp>? = null
    var uiList: MutableList<TorifiedAppWrapper> = ArrayList()

    private fun loadApps() {
        allApps = allApps ?: getApps(this@AppManagerActivity, mPrefs, null, alSuggested).also { TorifiedApp.sortAppsForTorifiedAndAbc(it) }
        suggestedApps = suggestedApps ?: getApps(this@AppManagerActivity, mPrefs, alSuggested, null)

        uiList.apply {
            if (suggestedApps!!.isNotEmpty()) {
                add(TorifiedAppWrapper().apply { header = getString(R.string.apps_suggested_title) })
                add(TorifiedAppWrapper().apply { subheader = getString(R.string.app_suggested_subtitle) })
                suggestedApps!!.mapTo(this) { TorifiedAppWrapper().apply { app = it } }
                add(TorifiedAppWrapper().apply { header = getString(R.string.apps_other_apps) })
            }
            allApps!!.mapTo(this) { TorifiedAppWrapper().apply { app = it } }
        }

        adapterAppsAll = object : ArrayAdapter<TorifiedAppWrapper?>(
            this,
            R.layout.layout_apps_item,
            R.id.itemtext,
            uiList as List<TorifiedAppWrapper?>
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val entry = convertView?.tag as? ListEntry ?: ListEntry().apply {
                    container = convertView?.findViewById(R.id.appContainer)
                    icon = convertView?.findViewById(R.id.itemicon)
                    box = convertView?.findViewById(R.id.itemcheck)
                    text = convertView?.findViewById(R.id.itemtext)
                    header = convertView?.findViewById(R.id.tvHeader)
                    subheader = convertView?.findViewById(R.id.tvSubheader)
                }

                val taw = uiList[position]
                when {
                    taw.header != null -> {
                        entry.header?.text = taw.header
                        entry.header?.visibility = View.VISIBLE
                        entry.subheader?.visibility = View.GONE
                        entry.container?.visibility = View.GONE
                    }
                    taw.subheader != null -> {
                        entry.subheader?.visibility = View.VISIBLE
                        entry.subheader?.text = taw.subheader
                        entry.container?.visibility = View.GONE
                        entry.header?.visibility = View.GONE
                    }
                    else -> {
                        val app = taw.app
                        entry.header?.visibility = View.GONE
                        entry.subheader?.visibility = View.GONE
                        entry.container?.visibility = View.VISIBLE
                        entry.icon?.let {
                            try {
                                it.setImageDrawable(pMgr?.getApplicationIcon(app!!.packageName))
                                it.tag = entry.box
                                it.setOnClickListener(this@AppManagerActivity)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        entry.text?.let {
                            it.text = app?.name
                            it.tag = entry.box
                            it.setOnClickListener(this@AppManagerActivity)
                        }
                        entry.box?.let {
                            it.isChecked = app?.isTorified ?: false
                            it.tag = app
                            it.setOnClickListener(this@AppManagerActivity)
                        }
                    }
                }

                convertView?.onFocusChangeListener = OnFocusChangeListener { v: View, hasFocus: Boolean ->
                    v.setBackgroundColor(
                        ContextCompat.getColor(
                            context,
                            if (hasFocus) R.color.dark_purple else android.R.color.transparent
                        )
                    )
                }

                return convertView ?: layoutInflater.inflate(R.layout.layout_apps_item, parent, false).apply {
                    tag = entry
                }
            }
        }
    }

    private fun saveAppSettings() {
        val tordApps = StringBuilder()
        val response = Intent()
        val allTorifiedApps = (allApps ?: emptyList()) + (suggestedApps ?: emptyList())

        allTorifiedApps.filter { it.isTorified }.forEach { tApp ->
            tordApps.append(tApp.packageName).append("|")
            response.putExtra(tApp.packageName, true)
        }

        mPrefs?.edit()?.apply {
            putString(OrbotConstants.PREFS_KEY_TORIFIED, tordApps.toString())
            apply()
        }

        setResult(RESULT_OK, response)
    }

    override fun onClick(v: View) {
        val cbox = when {
            v is CheckBox -> v
            v.tag is CheckBox -> v.tag as CheckBox
            v.tag is ListEntry -> (v.tag as ListEntry).box
            else -> null
        }
        cbox?.let {
            val app = it.tag as TorifiedApp
            app.isTorified = !app.isTorified
            it.isChecked = app.isTorified
        }
    }

    private class ListEntry {
        var box: CheckBox? = null
        var text: TextView? = null // app name
        var icon: ImageView? = null
        var container: View? = null
        var header: TextView? = null
        var subheader: TextView? = null
    }

    companion object {
        /**
         * @return true if the app is "enabled", not Orbot, and not in
         * [.BYPASS_VPN_PACKAGES]
         */
        private fun includeAppInUi(applicationInfo: ApplicationInfo): Boolean {
            if (!applicationInfo.enabled) return false
            if (OrbotConstants.BYPASS_VPN_PACKAGES.contains(applicationInfo.packageName)) return false
            return BuildConfig.APPLICATION_ID != applicationInfo.packageName
        }

        fun getApps(
            context: Context,
            prefs: SharedPreferences?,
            filterInclude: List<String>?,
            filterRemove: List<String>?
        ): ArrayList<TorifiedApp> {
            val pMgr = context.packageManager
            val tordApps = prefs?.getString(OrbotConstants.PREFS_KEY_TORIFIED, "")?.split("|")?.sorted()
            val lAppInfo = pMgr.getInstalledApplications(0)
            val apps = ArrayList<TorifiedApp>()

            for (aInfo in lAppInfo) {
                if (!includeAppInUi(aInfo)) continue
                if (filterInclude != null && aInfo.packageName !in filterInclude) continue
                if (filterRemove != null && aInfo.packageName in filterRemove) continue

                val app = TorifiedApp().apply {
                    try {
                        val pInfo = pMgr.getPackageInfo(aInfo.packageName, PackageManager.GET_PERMISSIONS)
                        setUsesInternet(Manifest.permission.INTERNET in (pInfo?.requestedPermissions ?: emptyArray()))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    try {
                        name = pMgr.getApplicationLabel(aInfo).toString()
                    } catch (e: Exception) {
                        continue
                    }

                    if (!usesInternet()) continue

                    isEnabled = aInfo.enabled
                    uid = aInfo.uid
                    username = pMgr.getNameForUid(uid)
                    procname = aInfo.processName
                    packageName = aInfo.packageName

                    isTorified = packageName in (tordApps ?: emptyList())
                }

                apps.add(app)
            }

            apps.sort()

            return apps
        }
    }
}
