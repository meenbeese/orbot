/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */ /* See LICENSE for licensing information */
package org.torproject.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.GridView
import android.widget.ImageView
import android.widget.ListAdapter
import android.widget.ProgressBar
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity

import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

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

import java.util.Arrays
import java.util.StringTokenizer

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
    private var searchBar: TextInputEditText? = null
    private var cachedAppListHash: Int = 0

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pMgr = packageManager
        this.setContentView(R.layout.layout_apps)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        listAppsAll = findViewById(R.id.applistview)
        progressBar = findViewById(R.id.progressBar)
        searchBar = findViewById(R.id.searchBar)

        // Need a better way to manage this list
        alSuggested = OrbotConstants.VPN_SUGGESTED_APPS

        searchBar?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val searchBarLayout = findViewById<TextInputLayout>(R.id.searchBarLayout)
        searchBarLayout.setEndIconOnClickListener {
            searchBar?.text?.clear()
            searchBar?.clearFocus()
            hideKeyboard()
            reloadApps()
        }
    }

    override fun onResume() {
        super.onResume()
        mPrefs = Prefs.getSharedPrefs(applicationContext)
        reloadApps()
    }

    override fun onClick(v: View) {
        val cbox = when (v) {
            is CheckBox -> v
            else -> v.tag as? CheckBox ?: (v.tag as? ListEntry)?.box
        }

        cbox?.let {
            val app = it.tag as TorifiedApp
            app.isTorified = !app.isTorified
            it.isChecked = app.isTorified
        }
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

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchBar?.windowToken, 0)
    }

    private fun calculateAppListHash(apps: List<TorifiedApp>?): Int {
        return apps?.sumOf { it.packageName.hashCode() }!!
    }

    private fun reloadApps() {
        scope.launch {
            val currentAppListHash = calculateAppListHash(getApps(this@AppManagerActivity, mPrefs, null, alSuggested))
            if (currentAppListHash != cachedAppListHash) {
                progressBar?.visibility = View.VISIBLE
                loadAppsAsync()
                cachedAppListHash = currentAppListHash
                listAppsAll?.adapter = adapterAppsAll
                progressBar?.visibility = View.GONE
            }
        }
    }

    private var allApps: List<TorifiedApp>? = null
    private var suggestedApps: List<TorifiedApp>? = null
    private var uiList: MutableList<TorifiedAppWrapper> = ArrayList()

    private suspend fun loadAppsAsync() {
        withContext(Dispatchers.Default) {
            allApps = allApps ?: getApps(this@AppManagerActivity, mPrefs, null, alSuggested)
            TorifiedApp.sortAppsForTorifiedAndAbc(allApps)
            suggestedApps = suggestedApps ?: getApps(this@AppManagerActivity, mPrefs, alSuggested, null)
            populateUiList()
            adapterAppsAll = createAdapter(uiList)
        }
    }

    private fun populateUiList() {
        uiList.clear()
        if (suggestedApps!!.isNotEmpty()) {
            val headerSuggested = TorifiedAppWrapper()
            headerSuggested.header = getString(R.string.apps_suggested_title)
            uiList.add(headerSuggested)

            val subheaderSuggested = TorifiedAppWrapper()
            subheaderSuggested.subheader = getString(R.string.app_suggested_subtitle)
            uiList.add(subheaderSuggested)

            suggestedApps?.mapTo(uiList) { TorifiedAppWrapper().apply { app = it } }

            val headerAllApps = TorifiedAppWrapper()
            headerAllApps.header = getString(R.string.apps_other_apps)
            uiList.add(headerAllApps)
        }
        allApps?.mapTo(uiList) { TorifiedAppWrapper().apply { app = it } }
    }

    private fun createAdapter(list: List<TorifiedAppWrapper>): ArrayAdapter<TorifiedAppWrapper> {
        return object : ArrayAdapter<TorifiedAppWrapper>(
            this,
            R.layout.layout_apps_item,
            R.id.itemtext,
            list
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var convertView = convertView
                var entry: ListEntry? = null
                if (convertView == null) convertView =
                    layoutInflater.inflate(R.layout.layout_apps_item, parent, false) else entry =
                    convertView.tag as ListEntry
                if (entry == null) {
                    // Inflate a new view
                    entry = ListEntry()
                    entry.container = convertView!!.findViewById(R.id.appContainer)
                    entry.icon = convertView.findViewById(R.id.itemicon)
                    entry.box = convertView.findViewById(R.id.itemcheck)
                    entry.text = convertView.findViewById(R.id.itemtext)
                    entry.header = convertView.findViewById(R.id.tvHeader)
                    entry.subheader = convertView.findViewById(R.id.tvSubheader)
                    convertView.tag = entry
                }
                val taw = list[position]
                if (taw.header != null) {
                    entry.header!!.text = taw.header
                    entry.header!!.visibility = View.VISIBLE
                    entry.subheader!!.visibility = View.GONE
                    entry.container!!.visibility = View.GONE
                } else if (taw.subheader != null) {
                    entry.subheader!!.visibility = View.VISIBLE
                    entry.subheader!!.text = taw.subheader
                    entry.container!!.visibility = View.GONE
                    entry.header!!.visibility = View.GONE
                } else {
                    val app = taw.app
                    entry.header!!.visibility = View.GONE
                    entry.subheader!!.visibility = View.GONE
                    entry.container!!.visibility = View.VISIBLE
                    if (entry.icon != null) {
                        try {
                            entry.icon!!.setImageDrawable(pMgr!!.getApplicationIcon(app!!.packageName))
                            entry.icon!!.tag = entry.box
                            entry.icon!!.setOnClickListener(this@AppManagerActivity)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    if (entry.text != null) {
                        entry.text!!.text = app!!.name
                        entry.text!!.tag = entry.box
                        entry.text!!.setOnClickListener(this@AppManagerActivity)
                    }
                    if (entry.box != null) {
                        entry.box!!.isChecked = app!!.isTorified
                        entry.box!!.tag = app
                        entry.box!!.setOnClickListener(this@AppManagerActivity)
                    }
                }
                return convertView!!
            }
        }
    }

    private fun filterApps(query: String) {
        val filteredList = uiList.filter {
            it.app?.name?.contains(query, ignoreCase = true) == true
        }
        adapterAppsAll = createAdapter(filteredList)
        listAppsAll?.adapter = adapterAppsAll
    }

    private fun saveAppSettings() {
        val tordApps = StringBuilder()
        val response = Intent()

        val saveTorifiedApps: (List<TorifiedApp>?) -> Unit = { apps ->
            apps?.filter { it.isTorified }?.forEach { tApp ->
                tordApps.append(tApp.packageName).append("|")
                response.putExtra(tApp.packageName, true)
            }
        }

        saveTorifiedApps(allApps)
        saveTorifiedApps(suggestedApps)

        mPrefs?.edit()?.apply {
            putString(OrbotConstants.PREFS_KEY_TORIFIED, tordApps.toString())
            apply()
        }

        setResult(RESULT_OK, response)
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
            return if (OrbotConstants.BYPASS_VPN_PACKAGES.contains(applicationInfo.packageName)) false else BuildConfig.APPLICATION_ID != applicationInfo.packageName
        }

        fun getApps(
            context: Context,
            prefs: SharedPreferences?,
            filterInclude: List<String>?,
            filterRemove: List<String>?
        ): ArrayList<TorifiedApp> {
            val pMgr = context.packageManager
            val tordAppString = prefs!!.getString(OrbotConstants.PREFS_KEY_TORIFIED, "")
            val tordApps: Array<String?>
            val st = StringTokenizer(tordAppString, "|")
            tordApps = arrayOfNulls(st.countTokens())
            var tordIdx = 0
            while (st.hasMoreTokens()) {
                tordApps[tordIdx++] = st.nextToken()
            }
            Arrays.sort(tordApps)
            val lAppInfo = pMgr.getInstalledApplications(0)
            val itAppInfo: Iterator<ApplicationInfo> = lAppInfo.iterator()
            val apps = ArrayList<TorifiedApp>()
            while (itAppInfo.hasNext()) {
                val aInfo = itAppInfo.next()
                if (!includeAppInUi(aInfo)) continue
                if (filterInclude != null) {
                    var wasFound = false
                    for (filterId in filterInclude) if (filterId == aInfo.packageName) {
                        wasFound = true
                        break
                    }
                    if (!wasFound) continue
                }
                if (filterRemove != null) {
                    var wasFound = false
                    for (filterId in filterRemove) if (filterId == aInfo.packageName) {
                        wasFound = true
                        break
                    }
                    if (wasFound) continue
                }
                val app = TorifiedApp()
                try {
                    val pInfo = pMgr.getPackageInfo(aInfo.packageName, PackageManager.GET_PERMISSIONS)
                    if (pInfo?.requestedPermissions != null) {
                        for (permInfo in pInfo.requestedPermissions) {
                            if (permInfo == Manifest.permission.INTERNET) {
                                app.setUsesInternet(true)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // TODO Auto-generated catch block
                    e.printStackTrace()
                }

                try {
                    app.name = pMgr.getApplicationLabel(aInfo).toString()
                } catch (e: Exception) {
                    // No name, we only show apps with names
                    continue
                }

                if (!app.usesInternet()) continue else {
                    apps.add(app)
                }

                app.isEnabled = aInfo.enabled
                app.uid = aInfo.uid
                app.username = pMgr.getNameForUid(app.uid)
                app.procname = aInfo.processName
                app.packageName = aInfo.packageName

                // Check if this application is allowed
                app.isTorified = Arrays.binarySearch(tordApps, app.packageName) >= 0
            }
            apps.sort()

            return apps
        }
    }
}
