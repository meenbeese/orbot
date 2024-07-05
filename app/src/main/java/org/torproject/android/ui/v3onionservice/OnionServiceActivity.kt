package org.torproject.android.ui.v3onionservice

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ListView

import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat

import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

import org.torproject.android.R
import org.torproject.android.core.DiskUtils.createReadFileIntent
import org.torproject.android.core.LocaleHelper.onAttach

class OnionServiceActivity : AppCompatActivity() {
    private var btnShowUserServices: MaterialButton? = null
    private var btnShowAppServices: MaterialButton? = null
    private var fab: FloatingActionButton? = null
    private var mContentResolver: ContentResolver? = null
    private var mAdapter: OnionV3ListAdapter? = null
    private var mLayoutRoot: CoordinatorLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hosted_services)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.isTitleCentered = true
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher }

        mLayoutRoot = findViewById(R.id.hostedServiceCoordinatorLayout)
        fab = findViewById(R.id.fab)
        fab?.setOnClickListener {
            OnionServiceCreateDialogFragment().show(supportFragmentManager, OnionServiceCreateDialogFragment::class.java.simpleName)
        }

        mContentResolver = contentResolver
        mAdapter = OnionV3ListAdapter(
            this,
            mContentResolver?.query(
                OnionServiceContentProvider.CONTENT_URI,
                OnionServiceContentProvider.PROJECTION,
                BASE_WHERE_SELECTION_CLAUSE + '1',
                null,
                null
            )
        )
        mContentResolver?.registerContentObserver(
            OnionServiceContentProvider.CONTENT_URI, true, OnionServiceObserver(
                Handler(Looper.getMainLooper())
            )
        )

        val onionList = findViewById<ListView>(R.id.onion_list)

        btnShowUserServices = findViewById(R.id.radioUserServices)
        btnShowAppServices = findViewById(R.id.radioAppServices)

        val showUserServices = btnShowAppServices!!.isChecked || savedInstanceState == null || savedInstanceState.getBoolean(BUNDLE_KEY_SHOW_USER_SERVICES, false)
        if (showUserServices) {
            btnShowUserServices?.isChecked = true
        } else {
            btnShowAppServices?.isChecked = true
        }
        filterServices(showUserServices)
        onionList.adapter = mAdapter
        onionList.onItemClickListener = AdapterView.OnItemClickListener { parent: AdapterView<*>, _: View?, position: Int, _: Long ->
            val item = parent.getItemAtPosition(position) as Cursor
            val arguments = Bundle()
            arguments.putInt(
                BUNDLE_KEY_ID,
                item.getInt(item.getColumnIndexOrThrow(OnionServiceContentProvider.OnionService._ID))
            )
            arguments.putString(
                BUNDLE_KEY_PORT,
                item.getString(item.getColumnIndexOrThrow(OnionServiceContentProvider.OnionService.PORT))
            )
            arguments.putString(
                BUNDLE_KEY_DOMAIN,
                item.getString(item.getColumnIndexOrThrow(OnionServiceContentProvider.OnionService.DOMAIN))
            )
            arguments.putString(
                BUNDLE_KEY_PATH,
                item.getString(item.getColumnIndexOrThrow(OnionServiceContentProvider.OnionService.PATH))
            )
            val dialog = OnionServiceActionsDialogFragment(arguments)
            dialog.show(supportFragmentManager, OnionServiceActionsDialogFragment::class.java.simpleName)
        }
    }

    private fun filterServices(showUserServices: Boolean) {
        val predicate = if (showUserServices) "1" else "0"
        if (showUserServices) fab?.show() else fab?.hide()
        mAdapter?.changeCursor(
            mContentResolver?.query(
                OnionServiceContentProvider.CONTENT_URI, OnionServiceContentProvider.PROJECTION,
                BASE_WHERE_SELECTION_CLAUSE + predicate, null, null
            )
        )
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(onAttach(base))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.hs_menu, menu)
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(BUNDLE_KEY_SHOW_USER_SERVICES, btnShowUserServices!!.isChecked)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_restore_backup) {
            val readFileIntent = createReadFileIntent(ZipUtilities.ZIP_MIME_TYPE)
            startActivityForResult(readFileIntent, REQUEST_CODE_READ_ZIP_BACKUP)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, result: Int, data: Intent?) {
        super.onActivityResult(requestCode, result, data)
        if (requestCode == REQUEST_CODE_READ_ZIP_BACKUP && result == RESULT_OK) {
            V3BackupUtils(this).restoreZipBackupV3(data?.data)
        }
    }

    fun onButtonClick(view: View) {
        when (view.id) {
            R.id.radioUserServices -> {
                btnShowUserServices?.setBackgroundColor(ContextCompat.getColor(this, R.color.orbot_btn_enabled_purple))
                btnShowAppServices?.setBackgroundColor(ContextCompat.getColor(this, R.color.orbot_btn_disable_grey))
                filterServices(true)
            }
            R.id.radioAppServices -> {
                btnShowUserServices?.setBackgroundColor(ContextCompat.getColor(this, R.color.orbot_btn_disable_grey))
                btnShowAppServices?.setBackgroundColor(ContextCompat.getColor(this, R.color.orbot_btn_enabled_purple))
                filterServices(false)
            }
        }
    }

    private inner class OnionServiceObserver(handler: Handler?) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            filterServices(btnShowUserServices!!.isChecked) // updates adapter
            showBatteryOptimizationsMessageIfAppropriate()
        }
    }

    fun showBatteryOptimizationsMessageIfAppropriate() {
        val activeServices = contentResolver.query(
            OnionServiceContentProvider.CONTENT_URI, OnionServiceContentProvider.PROJECTION,
            OnionServiceContentProvider.OnionService.ENABLED + "=1", null, null
        )
        activeServices?.let {
            if (it.count > 0) PermissionManager.requestBatteryPermissions(this, mLayoutRoot)
            else PermissionManager.requestDropBatteryPermissions(this, mLayoutRoot)
            it.close()
        }
    }

    companion object {
        const val BUNDLE_KEY_ID = "id"
        const val BUNDLE_KEY_PORT = "port"
        const val BUNDLE_KEY_DOMAIN = "domain"
        const val BUNDLE_KEY_PATH = "path"
        private const val BASE_WHERE_SELECTION_CLAUSE = OnionServiceContentProvider.OnionService.CREATED_BY_USER + "="
        private const val BUNDLE_KEY_SHOW_USER_SERVICES = "show_user_key"
        private const val REQUEST_CODE_READ_ZIP_BACKUP = 347
    }
}
