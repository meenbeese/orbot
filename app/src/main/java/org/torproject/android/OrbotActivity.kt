package org.torproject.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.torproject.android.core.LocaleHelper
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.util.Prefs
import org.torproject.android.ui.LogBottomSheet
import org.torproject.android.ui.v3onionservice.PermissionManager


class OrbotActivity : AppCompatActivity() {

  //  private lateinit var torStatsGroup: Group
    private lateinit var bottomNavigationView: BottomNavigationView

    private lateinit var logBottomSheet: LogBottomSheet
    lateinit var fragConnect : ConnectFragment
    lateinit var fragMore : MoreFragment

    var previousReceivedTorStatus: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orbot)

        logBottomSheet = LogBottomSheet()

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        val navController = findNavController(R.id.nav_fragment)
        bottomNavigationView.setupWithNavController(navController)
        bottomNavigationView.selectedItemId = R.id.connectFragment

        with(LocalBroadcastManager.getInstance(this)) {
            registerReceiver(orbotServiceBroadcastReceiver, IntentFilter(OrbotConstants.LOCAL_ACTION_STATUS))
            registerReceiver(orbotServiceBroadcastReceiver, IntentFilter(OrbotConstants.LOCAL_ACTION_LOG))
            registerReceiver(orbotServiceBroadcastReceiver, IntentFilter(OrbotConstants.LOCAL_ACTION_PORTS))
            registerReceiver(orbotServiceBroadcastReceiver, IntentFilter(OrbotConstants.LOCAL_ACTION_SMART_CONNECT_EVENT))
        }

        requestNotificationPermission()

        Prefs.initWeeklyWorker()
    }

    private fun requestNotificationPermission () {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) -> {
                // You can use the API that requires the permission.
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Register the permissions callback, which handles the user's response to the
// system permissions dialog. Save the return value, an instance of
// ActivityResultLauncher. You can use either a val, as shown in this snippet,
// or a lateinit var in your onAttach() or onCreate() method.
    val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.
            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
            }
        }

    override fun onResume() {
        super.onResume()
        sendIntentToService(OrbotConstants.CMD_ACTIVE)
        LocaleHelper.onAttach(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(orbotServiceBroadcastReceiver)
    }


    private fun sendIntentToService(intent: Intent) = ContextCompat.startForegroundService(this, intent)
    private fun sendIntentToService(action: String) = sendIntentToService(
        android.content.Intent(this, org.torproject.android.service.OrbotService::class.java)
            .apply {
            this.action = action
        })

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_VPN && resultCode == RESULT_OK) {
            fragConnect.startTorAndVpn()
        } else if (requestCode == REQUEST_CODE_SETTINGS && resultCode == RESULT_OK) {
            Prefs.setDefaultLocale(data?.getStringExtra("locale"))
            (application as OrbotApp).setLocale()
            finish()
            startActivity(Intent(this, OrbotActivity::class.java))
        } else if (requestCode == REQUEST_VPN_APP_SELECT && resultCode == RESULT_OK) {
            sendIntentToService(OrbotConstants.ACTION_RESTART_VPN) // is this enough todo?
            fragConnect.refreshMenuList(this)
        }
    }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LocaleHelper.onAttach(newBase))

    var allCircumventionAttemptsFailed = false

    private val orbotServiceBroadcastReceiver = object : BroadcastReceiver(){
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(OrbotConstants.EXTRA_STATUS)
            when (intent?.action) {
                OrbotConstants.LOCAL_ACTION_STATUS -> {
                   if (status.equals(previousReceivedTorStatus)) return
                    when (status) {
                        OrbotConstants.STATUS_OFF -> {
                            if (previousReceivedTorStatus.equals(OrbotConstants.STATUS_STARTING)) {
                                if (allCircumventionAttemptsFailed) {
                                    allCircumventionAttemptsFailed = false
                                    return
                                }
                                var shouldDoOffLayout = true
                                if (Prefs.getConnectionPathway().equals(Prefs.PATHWAY_SMART)) {
                                    shouldDoOffLayout = false
                                }
                                if (shouldDoOffLayout) fragConnect.doLayoutOff()
                            }
                            else
                                fragConnect.doLayoutOff()
                        }
                        OrbotConstants.STATUS_STARTING -> fragConnect.doLayoutStarting(this@OrbotActivity)
                        OrbotConstants.STATUS_ON -> fragConnect.doLayoutOn(this@OrbotActivity)
                        OrbotConstants.STATUS_STOPPING -> {}
                    }

                    previousReceivedTorStatus = status

                }
                OrbotConstants.LOCAL_ACTION_LOG -> {
                    intent.getStringExtra(OrbotConstants.LOCAL_EXTRA_BOOTSTRAP_PERCENT)?.let {
                        // todo progress bar shouldn't be accessed directly here, *tell* the connect fragment to update
                        fragConnect.progressBar.progress = Integer.parseInt(it)
                    }
                    intent.getStringExtra(OrbotConstants.LOCAL_EXTRA_LOG)?.let {
                        logBottomSheet.appendLog(it)
                    }
                }
                OrbotConstants.LOCAL_ACTION_PORTS -> {
                    val socks = intent.getIntExtra(OrbotConstants.EXTRA_SOCKS_PROXY_PORT, -1)
                    val http = intent.getIntExtra(OrbotConstants.EXTRA_HTTP_PROXY_PORT, -1)
                    if (http > 0 && socks > 0) fragMore?.setPorts(http, socks)
                }
                else -> {}
            }
        }
    }

    companion object {
        const val REQUEST_CODE_VPN = 1234
        const val REQUEST_CODE_SETTINGS = 2345
        const val REQUEST_VPN_APP_SELECT = 2432
        val CAN_DO_APP_ROUTING = PermissionManager.isLollipopOrHigher()
    }

    fun showLog() {
        logBottomSheet.show(supportFragmentManager, OrbotActivity::class.java.simpleName)

    }
}