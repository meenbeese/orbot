/* Copyright (c) 2009-2011, Nathan Freitas, Orbot / The Guardian Project - https://guardianproject.info/apps/orbot */ /* See LICENSE for licensing information */
package org.torproject.android.service

import IPtProxy.IPtProxy

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.BaseColumns
import android.text.TextUtils
import android.util.Log
import android.widget.Toast

import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

import net.freehaven.tor.control.TorControlCommands
import net.freehaven.tor.control.TorControlConnection

import org.torproject.android.service.util.CustomTorResourceInstaller
import org.torproject.android.service.util.PowerConnectionReceiver
import org.torproject.android.service.util.Prefs
import org.torproject.android.service.util.Utils
import org.torproject.android.service.vpn.OrbotVpnManager
import org.torproject.jni.TorService
import org.torproject.jni.TorService.LocalBinder

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
import java.io.PrintWriter
import java.security.SecureRandom
import java.text.NumberFormat
import java.util.Locale
import java.util.StringTokenizer
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

open class OrbotService : VpnService(), OrbotConstants {
    private val mExecutor: ExecutorService = Executors.newCachedThreadPool()
    var mOrbotRawEventListener: OrbotRawEventListener? = null
    var mVpnManager: OrbotVpnManager? = null
    private var mHandler: Handler? = null
    private var mActionBroadcastReceiver: ActionBroadcastReceiver? = null
    var currentStatus: String? = OrbotConstants.STATUS_OFF
        private set
    @JvmField
    var conn: TorControlConnection? = null
    private var torServiceConnection: ServiceConnection? = null
    private var shouldUnbindTorService = false
    private var mNotificationManager: NotificationManager? = null
    private var mNotifyBuilder: NotificationCompat.Builder? = null
    private var mV3OnionBasePath: File? = null
    private var mV3AuthBasePath: File? = null

    private var mPowerReceiver: PowerConnectionReceiver? = null

    private var mHasPower = false
    private var mHasWifi = false

    fun debug(msg: String) {
        Log.d(OrbotConstants.TAG, msg)

        if (Prefs.useDebugLogging()) {
            sendCallbackLogMessage(msg)
        }
    }

    private fun logException(msg: String, e: Exception) {
        if (Prefs.useDebugLogging()) {
            Log.e(OrbotConstants.TAG, msg, e)
            val baos = ByteArrayOutputStream()
            e.printStackTrace(PrintStream(baos))

            sendCallbackLogMessage(
                """
    $msg
    $baos
    """.trimIndent()
            )
        } else sendCallbackLogMessage(msg)
    }

    private fun showConnectedToTorNetworkNotification() {
        mNotifyBuilder!!.setProgress(0, 0, false)
        showToolbarNotification(
            getString(R.string.status_activated),
            NOTIFY_ID,
            R.drawable.ic_stat_tor
        )
    }

    override fun onLowMemory() {
        super.onLowMemory()
        //this doesn't need to be shown to the user unless there is something to do
        debug(getString(R.string.log_notice_low_memory_warning))
    }

    private fun clearNotifications() {
        if (mNotificationManager != null) mNotificationManager!!.cancelAll()

        if (mOrbotRawEventListener != null) mOrbotRawEventListener!!.nodes.clear()
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val mChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        mChannel.description = getString(R.string.app_description)
        mChannel.enableLights(false)
        mChannel.enableVibration(false)
        mChannel.setShowBadge(false)
        mChannel.lockscreenVisibility = Notification.VISIBILITY_SECRET
        mNotificationManager.createNotificationChannel(mChannel)
    }

    @SuppressLint("NewApi", "RestrictedApi")
    protected fun showToolbarNotification(notifyMsg: String, notifyType: Int, icon: Int) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendIntent =
            PendingIntent.getActivity(this@OrbotService, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        if (mNotifyBuilder == null) {
            mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            mNotifyBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_tor).setContentIntent(pendIntent).setCategory(
                Notification.CATEGORY_SERVICE
            )
        }

        mNotifyBuilder!!.setOngoing(true)

        var title = getString(R.string.status_disabled)
        if (currentStatus == OrbotConstants.STATUS_STARTING || notifyMsg == getString(R.string.status_starting_up)) title =
            getString(R.string.status_starting_up)
        else if (currentStatus == OrbotConstants.STATUS_ON) {
            title = getString(R.string.status_activated)
        }

        mNotifyBuilder!!.setContentTitle(title)

        mNotifyBuilder!!.mActions.clear() // clear out any notification actions, if any
        if (conn != null && currentStatus == OrbotConstants.STATUS_ON) { // only add new identity action when there is a connection
            val pendingIntentNewNym = PendingIntent.getBroadcast(
                this,
                0,
                Intent(TorControlCommands.SIGNAL_NEWNYM),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            mNotifyBuilder!!.addAction(
                R.drawable.ic_refresh_white_24dp,
                getString(R.string.menu_new_identity),
                pendingIntentNewNym
            )
        } else if (currentStatus == OrbotConstants.STATUS_OFF) {
            val pendingIntentConnect = PendingIntent.getBroadcast(
                this,
                0,
                Intent(OrbotConstants.LOCAL_ACTION_NOTIFICATION_START),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            mNotifyBuilder!!.addAction(
                R.drawable.ic_stat_tor,
                getString(R.string.connect_to_tor),
                pendingIntentConnect
            )
        }

        mNotifyBuilder!!.setContentText(notifyMsg).setSmallIcon(icon)
            .setTicker(if (notifyType != NOTIFY_ID) notifyMsg else null)

        if (currentStatus != OrbotConstants.STATUS_ON) {
            mNotifyBuilder!!.setSubText(null)
        }

        if (currentStatus != OrbotConstants.STATUS_STARTING) {
            mNotifyBuilder!!.setProgress(0, 0, false) // removes progress bar
        }

        startForeground(NOTIFY_ID, mNotifyBuilder!!.build())
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        try {
            val shouldStartVpnFromSystemIntent =
                !intent.getBooleanExtra(OrbotConstants.EXTRA_NOT_SYSTEM, false)

            if (currentStatus == OrbotConstants.STATUS_OFF) showToolbarNotification(
                getString(R.string.open_orbot_to_connect_to_tor),
                NOTIFY_ID,
                R.drawable.ic_stat_tor
            )

            if (shouldStartVpnFromSystemIntent) {
                Log.d(OrbotConstants.TAG, "Starting VPN from system intent: $intent")
                showToolbarNotification(
                    getString(R.string.status_starting_up),
                    NOTIFY_ID,
                    R.drawable.ic_stat_tor
                )
                if (prepare(this) == null) {
                    // Power-user mode doesn't matter here. If the system is starting the VPN, i.e.
                    // via always-on VPN, we need to start it regardless.
                    Prefs.putUseVpn(true)
                    mExecutor.execute(IncomingIntentRouter(Intent(OrbotConstants.ACTION_START)))
                    mExecutor.execute(IncomingIntentRouter(Intent(OrbotConstants.ACTION_START_VPN)))
                } else {
                    Log.wtf(
                        OrbotConstants.TAG,
                        "Could not start VPN from system because it is not prepared, which should be impossible!"
                    )
                }
            } else {
                mExecutor.execute(IncomingIntentRouter(intent))
            }
        } catch (re: RuntimeException) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }

        return START_REDELIVER_INTENT
    }

    private fun showDeactivatedNotification() {
        showToolbarNotification(
            getString(R.string.open_orbot_to_connect_to_tor),
            NOTIFY_ID,
            R.drawable.ic_stat_tor
        )
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(mActionBroadcastReceiver)

            unregisterReceiver(mPowerReceiver)
        } catch (iae: IllegalArgumentException) {
            //not registered yet
        }
        super.onDestroy()
    }

    @SuppressLint("NewApi")
    private fun stopTorAsync(showNotification: Boolean) {
        debug("stopTor")

        if (showNotification) sendCallbackLogMessage(getString(R.string.status_shutting_down))

        val connectionPathway = Prefs.getConnectionPathway()
        // todo this needs to handle a lot of different cases that haven't been defined yet
        // todo particularly this is true for the smart connection case...
        if (connectionPathway.startsWith(Prefs.PATHWAY_SNOWFLAKE) || Prefs.getPrefSmartTrySnowflake()) {
            IPtProxy.stopSnowflake()
        } else if (connectionPathway == Prefs.PATHWAY_CUSTOM || Prefs.getPrefSmartTryObfs4() != null) {
            IPtProxy.stopLyrebird()
        }

        stopTor()

        //stop the foreground priority and make sure to remove the persistent notification
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (showNotification) sendCallbackLogMessage(getString(R.string.status_disabled))

        mPortDns = -1
        mPortSOCKS = -1
        mPortHTTP = -1
        mPortTrans = -1

        if (!showNotification) {
            clearNotifications()
            stopSelf()
        }
    }

    private fun stopTorOnError(message: String) {
        stopTorAsync(false)
        showToolbarNotification(
            getString(R.string.unable_to_start_tor) + ": " + message,
            ERROR_NOTIFY_ID,
            R.drawable.ic_stat_notifyerr
        )
    }

    private fun startSnowflakeClientDomainFronting() {
        //this is using the current, default Tor snowflake infrastructure
        val target = getCdnFront("snowflake-target")
        val front = getCdnFront("snowflake-front")
        val stunServer = getCdnFront("snowflake-stun")

        /*
        // @param ice Comma-separated list of ICE servers.
        // @param url URL of signaling broker.
        // @param fronts Comma-separated list of front domains.
        // @param ampCache OPTIONAL. URL of AMP cache to use as a proxy for signaling.
        //	Only needed when you want to do the rendezvous over AMP instead of a domain fronted server.
        // @param sqsQueueURL OPTIONAL. URL of SQS Queue to use as a proxy for signaling.
        // @param sqsCredsStr OPTIONAL. Credentials to access SQS Queue.
        // @param logFile Name of log file. OPTIONAL. Defaults to no log.
        // @param logToStateDir Resolve the log file relative to Tor's PT state dir.
        // @param keepLocalAddresses Keep local LAN address ICE candidates.
        // @param unsafeLogging Prevent logs from being scrubbed.
        // @param maxPeers Capacity for number of multiplexed WebRTC peers. Defaults to 1 if less than that.
        // @return Port number where Snowflake will listen on, if no error happens during start up.
         */
        IPtProxy.startSnowflake(
            stunServer,
            target,
            front,
            null,
            null,
            null,
            null,
            true,
            false,
            false,
            1
        )
    }

    private fun startSnowflakeClientAmpRendezvous() {
        val stunServers = getCdnFront("snowflake-stun")
        val target = getCdnFront("snowflake-target-direct")
        val front = getCdnFront("snowflake-amp-front")
        val ampCache = getCdnFront("snowflake-amp-cache")
        IPtProxy.startSnowflake(
            stunServers,
            target,
            front,
            ampCache,
            null,
            null,
            null,
            true,
            false,
            false,
            1
        )
    }

    private val mSecureRandGen = SecureRandom() //used to randomly select STUN servers for snowflake

    @Synchronized
    fun enableSnowflakeProxy() { // This is to host a snowflake entrance node / bridge
        if (!IPtProxy.isSnowflakeProxyRunning()) {
            if (Prefs.limitSnowflakeProxyingWifi() && (!mHasWifi)) return

            if (Prefs.limitSnowflakeProxyingCharging() && (!mHasPower)) return

            val capacity = 1
            val keepLocalAddresses = false
            val unsafeLogging = false
            val stunServers = getCdnFront("snowflake-stun")!!
                .split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            val randomIndex = mSecureRandGen.nextInt(stunServers.size)
            val stunUrl = stunServers[randomIndex]
            val relayUrl = getCdnFront("snowflake-relay-url")
            val natProbeUrl = getCdnFront("snowflake-nat-probe")
            val brokerUrl = getCdnFront("snowflake-target-direct")
            IPtProxy.startSnowflakeProxy(
                capacity.toLong(),
                brokerUrl,
                relayUrl,
                stunUrl,
                natProbeUrl,
                null,
                keepLocalAddresses,
                unsafeLogging
            ) {
                Prefs.addSnowflakeServed()
                if (!Prefs.showSnowflakeProxyMessage()) return@startSnowflakeProxy
                Handler(mainLooper).post {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.snowflake_proxy_client_connected_msg),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            logNotice(getString(R.string.log_notice_snowflake_proxy_enabled))

            if (Prefs.showSnowflakeProxyMessage()) {
                val message = getString(R.string.log_notice_snowflake_proxy_enabled)
                Handler(mainLooper).post {
                    Toast.makeText(
                        applicationContext,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun enableSnowflakeProxyNetworkListener() {
        if (Prefs.limitSnowflakeProxyingWifi()) {
            //check if on wifi
            val connMgr = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connMgr.registerDefaultNetworkCallback(object : NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        checkNetworkForSnowflakeProxy()
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        checkNetworkForSnowflakeProxy()
                    }
                })
            }
        }
    }

    fun setHasPower(hasPower: Boolean) {
        mHasPower = hasPower
        if (Prefs.beSnowflakeProxy()) {
            if (Prefs.limitSnowflakeProxyingCharging()) {
                if (mHasPower) enableSnowflakeProxy()
                else disableSnowflakeProxy()
            }
        }
    }

    private fun checkNetworkForSnowflakeProxy() {
        val connMgr = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val netCap = connMgr.getNetworkCapabilities(connMgr.activeNetwork)
            mHasWifi = netCap?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        } else {
            val netInfo = connMgr.activeNetworkInfo
            if (netInfo != null) mHasWifi = netInfo.type == ConnectivityManager.TYPE_WIFI
        }

        if (Prefs.beSnowflakeProxy()) {
            if (Prefs.limitSnowflakeProxyingWifi()) {
                if (mHasWifi) enableSnowflakeProxy()
                else disableSnowflakeProxy()
            }
        }
    }

    @Synchronized
    fun disableSnowflakeProxy() {
        if (IPtProxy.isSnowflakeProxyRunning()) {
            IPtProxy.stopSnowflakeProxy()
            logNotice(getString(R.string.log_notice_snowflake_proxy_disabled))

            if (Prefs.showSnowflakeProxyMessage()) {
                val message = getString(R.string.log_notice_snowflake_proxy_disabled)
                Handler(mainLooper).post {
                    Toast.makeText(
                        applicationContext,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // if someone stops during startup, we may have to wait for the conn port to be setup, so we can properly shutdown tor
    private fun stopTor() {
        if (shouldUnbindTorService) {
            unbindService(torServiceConnection!!) //unbinding from the tor service will stop tor
            shouldUnbindTorService = false
            conn = null
        } else {
            sendLocalStatusOffBroadcast()
        }
    }

    private fun requestTorRereadConfig() {
        try {
            if (conn != null) {
                conn!!.signal(TorControlCommands.SIGNAL_RELOAD)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun logNotice(msg: String?) {
        if (msg != null && msg.trim { it <= ' ' }.isNotEmpty()) {
            if (Prefs.useDebugLogging()) Log.d(OrbotConstants.TAG, msg)
            sendCallbackLogMessage(msg)
        }
    }

    override fun onCreate() {
        super.onCreate()
        configLanguage()
        try {
            //set proper content URIs for current build flavor
            V3_ONION_SERVICES_CONTENT_URI =
                Uri.parse("content://" + applicationContext.packageName + ".ui.v3onionservice/v3")
            V3_CLIENT_AUTH_URI =
                Uri.parse("content://" + applicationContext.packageName + ".ui.v3onionservice.clientauth/v3auth")

            try {
                mHandler = Handler(Looper.getMainLooper())

                appBinHome = filesDir
                if (!appBinHome?.exists()!!) appBinHome?.mkdirs()

                appCacheHome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    File(dataDir, OrbotConstants.DIRECTORY_TOR_DATA)
                } else {
                    getDir(OrbotConstants.DIRECTORY_TOR_DATA, MODE_PRIVATE)
                }

                if (!appCacheHome!!.exists()) appCacheHome!!.mkdirs()

                mV3OnionBasePath = File(filesDir.absolutePath, OrbotConstants.ONION_SERVICES_DIR)
                if (!mV3OnionBasePath!!.isDirectory) mV3OnionBasePath!!.mkdirs()

                mV3AuthBasePath = File(filesDir.absolutePath, OrbotConstants.V3_CLIENT_AUTH_DIR)
                if (!mV3AuthBasePath!!.isDirectory) mV3AuthBasePath!!.mkdirs()

                if (mNotificationManager == null) {
                    mNotificationManager =
                        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                }

                val filter = IntentFilter(TorControlCommands.SIGNAL_NEWNYM)
                filter.addAction(OrbotConstants.CMD_ACTIVE)
                filter.addAction(OrbotConstants.ACTION_STATUS)
                filter.addAction(TorService.ACTION_ERROR)
                filter.addAction(OrbotConstants.LOCAL_ACTION_NOTIFICATION_START)

                mActionBroadcastReceiver = ActionBroadcastReceiver()
                ContextCompat.registerReceiver(
                    this,
                    mActionBroadcastReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel()

                val hasGeoip = File(appBinHome, OrbotConstants.GEOIP_ASSET_KEY).exists()
                val hasGeoip6 = File(appBinHome, OrbotConstants.GEOIP6_ASSET_KEY).exists()

                // only write out geoip files if there's an app update or they don't exist
                if (!hasGeoip || !hasGeoip6 || Prefs.isGeoIpReinstallNeeded()) {
                    try {
                        Log.d(OrbotConstants.TAG, "Installing geoip files...")
                        CustomTorResourceInstaller(this, appBinHome).installGeoIP()
                        Prefs.setIsGeoIpReinstallNeeded(false)
                    } catch (io: IOException) { // user has < 10MB free space on disk...
                        Log.e(OrbotConstants.TAG, "Error installing geoip files", io)
                    }
                }


                pluggableTransportInstall()

                mVpnManager = OrbotVpnManager(this)

                loadCdnFronts(this)
            } catch (e: Exception) {
                Log.e(OrbotConstants.TAG, "Error setting up Orbot", e)
                logNotice(getString(R.string.couldn_t_start_tor_process_) + " " + e.javaClass.simpleName)
            }

            mPowerReceiver = PowerConnectionReceiver(this)

            val ifilter = IntentFilter()
            ifilter.addAction(Intent.ACTION_POWER_CONNECTED)
            ifilter.addAction(Intent.ACTION_POWER_DISCONNECTED)
            registerReceiver(mPowerReceiver, ifilter)

            enableSnowflakeProxyNetworkListener()

            if (Prefs.beSnowflakeProxy()
                && !(Prefs.limitSnowflakeProxyingCharging() || Prefs.limitSnowflakeProxyingWifi())
            ) enableSnowflakeProxy()
        } catch (re: RuntimeException) {
            //catch this to avoid malicious launches as document Cure53 Audit: ORB-01-009 WP1/2: Orbot DoS via exported activity (High)
        }
    }

    private fun configLanguage() {
        val config = baseContext.resources.configuration
        val locale = Locale(Prefs.getDefaultLocale())
        Locale.setDefault(locale)
        config.locale = locale
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
    }

    private fun pluggableTransportInstall() {
        val fileCacheDir = File(cacheDir, "pt")
        if (!fileCacheDir.exists())
            fileCacheDir.mkdir()

        try {
            IPtProxy.setStateLocation(fileCacheDir.absolutePath)
            debug("IPtProxy state: " + IPtProxy.getStateLocation())
        } catch (e: Error) {
            debug("IPtProxy state: not installed; " + e.localizedMessage)
        }
    }

    @Throws(IOException::class)
    private fun updateTorrcCustomFile(): File? {
        val prefs = Prefs.getSharedPrefs(applicationContext)
        var extraLines: StringBuffer? = StringBuffer()

        extraLines!!.append("\n")
        extraLines.append("RunAsDaemon 0").append('\n')
        extraLines.append("AvoidDiskWrites 1").append('\n')

        var socksPortPref =
            prefs.getString(OrbotConstants.PREF_SOCKS, OrbotConstants.SOCKS_PROXY_PORT_DEFAULT)
        if (socksPortPref!!.indexOf(':') != -1) socksPortPref =
            socksPortPref.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[1]

        socksPortPref = checkPortOrAuto(socksPortPref)

        var httpPortPref =
            prefs.getString(OrbotConstants.PREF_HTTP, OrbotConstants.HTTP_PROXY_PORT_DEFAULT)
        if (httpPortPref!!.indexOf(':') != -1) httpPortPref =
            httpPortPref.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[1]

        httpPortPref = checkPortOrAuto(httpPortPref)

        var isolate = ""
        if (prefs.getBoolean(OrbotConstants.PREF_ISOLATE_DEST, false)) {
            isolate += " IsolateDestAddr "
        }
        if (prefs.getBoolean(OrbotConstants.PREF_ISOLATE_PORT, false)) {
            isolate += " IsolateDestPort "
        }
        if (prefs.getBoolean(OrbotConstants.PREF_ISOLATE_PROTOCOL, false)) {
            isolate += " IsolateClientProtocol "
        }

        var ipv6Pref = ""
        if (prefs.getBoolean(OrbotConstants.PREF_PREFER_IPV6, true)) {
            ipv6Pref += " IPv6Traffic PreferIPv6 "
        }

        if (prefs.getBoolean(OrbotConstants.PREF_DISABLE_IPV4, false)) {
            ipv6Pref += " IPv6Traffic NoIPv4Traffic "
        }

        if (!Prefs.openProxyOnAllInterfaces()) {
            extraLines.append("SOCKSPort ").append(socksPortPref).append(isolate).append(ipv6Pref)
                .append('\n')
        } else {
            extraLines.append("SOCKSPort 0.0.0.0:").append(socksPortPref).append(ipv6Pref)
                .append(isolate).append("\n")
            extraLines.append("SocksPolicy accept *:*").append('\n')
        }

        extraLines.append("SafeSocks 0").append('\n')
        extraLines.append("TestSocks 0").append('\n')
        extraLines.append("HTTPTunnelPort ").append(httpPortPref).append(isolate).append('\n')


        if (prefs.getBoolean(OrbotConstants.PREF_CONNECTION_PADDING, false)) {
            extraLines.append("ConnectionPadding 1").append('\n')
        }

        if (prefs.getBoolean(OrbotConstants.PREF_REDUCED_CONNECTION_PADDING, true)) {
            extraLines.append("ReducedConnectionPadding 1").append('\n')
        }

        if (prefs.getBoolean(OrbotConstants.PREF_CIRCUIT_PADDING, true)) {
            extraLines.append("CircuitPadding 1").append('\n')
        } else {
            extraLines.append("CircuitPadding 0").append('\n')
        }

        if (prefs.getBoolean(OrbotConstants.PREF_REDUCED_CIRCUIT_PADDING, true)) {
            extraLines.append("ReducedCircuitPadding 1").append('\n')
        }

        val transPort = prefs.getString(
            "pref_transport",
            OrbotConstants.TOR_TRANSPROXY_PORT_DEFAULT.toString() + ""
        )
        val dnsPort =
            prefs.getString("pref_dnsport", OrbotConstants.TOR_DNS_PORT_DEFAULT.toString() + "")

        extraLines.append("TransPort ").append(checkPortOrAuto(transPort)).append(isolate)
            .append('\n')
        extraLines.append("DNSPort ").append(checkPortOrAuto(dnsPort)).append(isolate).append('\n')

        extraLines.append("VirtualAddrNetwork 10.192.0.0/10").append('\n')
        extraLines.append("AutomapHostsOnResolve 1").append('\n')

        extraLines.append("DormantClientTimeout 10 minutes").append('\n')
        // extraLines.append("DormantOnFirstStartup 0").append('\n');
        extraLines.append("DormantCanceledByStartup 1").append('\n')

        extraLines.append("DisableNetwork 0").append('\n')

        if (Prefs.useDebugLogging()) {
            extraLines.append("Log debug syslog").append('\n')
            extraLines.append("SafeLogging 0").append('\n')
        }

        extraLines = processSettingsImpl(extraLines)

        if (extraLines == null) return null

        extraLines.append('\n')
        extraLines.append(prefs.getString("pref_custom_torrc", "")).append('\n')

        logNotice(getString(R.string.log_notice_updating_torrc))

        debug("torrc.custom=$extraLines")

        val fileTorRcCustom = TorService.getTorrc(this)
        updateTorConfigCustom(fileTorRcCustom, extraLines.toString(), false)
        return fileTorRcCustom
    }

    private fun checkPortOrAuto(portString: String?): String? {
        if (!portString.equals("auto", ignoreCase = true)) {
            var isPortUsed = true
            var port = portString!!.toInt()

            while (isPortUsed) {
                isPortUsed = Utils.isPortOpen("127.0.0.1", port, 500)

                if (isPortUsed) //the specified port is not available, so let Tor find one instead
                    port++
            }
            return port.toString() + ""
        }

        return portString
    }

    @Throws(IOException::class)
    fun updateTorConfigCustom(fileTorRcCustom: File?, extraLines: String?, append: Boolean) {
        val ps = PrintWriter(FileWriter(fileTorRcCustom, append))
        ps.print(extraLines)
        ps.flush()
        ps.close()
    }

    /**
     * Send Orbot's status in reply to an
     * [.ACTION_START] [Intent], targeted only to
     * the app that sent the initial request. If the user has disabled auto-
     * starts, the reply `ACTION_START Intent` will include the extra
     * [.STATUS_STARTS_DISABLED]
     */
    private fun replyWithStatus(startRequest: Intent) {
        val packageName = startRequest.getStringExtra(OrbotConstants.EXTRA_PACKAGE_NAME)

        val reply = Intent(OrbotConstants.ACTION_STATUS)
        reply.putExtra(OrbotConstants.EXTRA_STATUS, currentStatus)
        reply.putExtra(OrbotConstants.EXTRA_SOCKS_PROXY, "socks://127.0.0.1:$mPortSOCKS")
        reply.putExtra(OrbotConstants.EXTRA_SOCKS_PROXY_HOST, "127.0.0.1")
        reply.putExtra(OrbotConstants.EXTRA_SOCKS_PROXY_PORT, mPortSOCKS)
        reply.putExtra(OrbotConstants.EXTRA_HTTP_PROXY, "http://127.0.0.1:$mPortHTTP")
        reply.putExtra(OrbotConstants.EXTRA_HTTP_PROXY_HOST, "127.0.0.1")
        reply.putExtra(OrbotConstants.EXTRA_HTTP_PROXY_PORT, mPortHTTP)
        reply.putExtra(OrbotConstants.EXTRA_DNS_PORT, mPortDns)

        if (packageName != null) {
            reply.setPackage(packageName)
            sendBroadcast(reply)
        }

        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(reply.setAction(OrbotConstants.LOCAL_ACTION_STATUS))

        if (mPortSOCKS != -1 && mPortHTTP != -1) sendCallbackPorts(
            mPortSOCKS,
            mPortHTTP,
            mPortDns,
            mPortTrans
        )
    }

    private var showTorServiceErrorMsg = false

    /**
     * The entire process for starting tor and related services is run from this method.
     */
    private fun startTor() {
        try {
            if (torServiceConnection != null && conn != null) {
                sendCallbackLogMessage(getString(R.string.log_notice_ignoring_start_request))
                showConnectedToTorNetworkNotification()
                return
            }

            mNotifyBuilder!!.setProgress(100, 0, false)
            showToolbarNotification("", NOTIFY_ID, R.drawable.ic_stat_tor)

            if (Prefs.getConnectionPathway() == Prefs.PATHWAY_SMART) {
                smartConnectionPathwayStartTor()
            }
            startTorService()
            showTorServiceErrorMsg = true

            if (Prefs.hostOnionServicesEnabled()) {
                try {
                    updateV3OnionNames()
                } catch (se: SecurityException) {
                    logNotice(getString(R.string.log_notice_unable_to_update_onions))
                }
            }
        } catch (e: Exception) {
            logException(getString(R.string.unable_to_start_tor) + " " + e.localizedMessage, e)
            stopTorOnError(e.localizedMessage)
        }
    }

    private fun smartConnectionPathwayStartTor() {
        Log.d(OrbotConstants.TAG, "timing out in " + TIMEOUT_MS + "ms")
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(OrbotConstants.TAG, "timed out mCurrentStatus=$currentStatus")
            if (currentStatus != OrbotConstants.STATUS_ON) {
                Log.d(OrbotConstants.TAG, "stopping tor...")
                if (Prefs.getPrefSmartTrySnowflake()) {
                    Log.d(OrbotConstants.TAG, "trying snowflake didn't work")
                    clearEphemeralSmartConnectionSettings()
                    sendSmartStatusToActivity(OrbotConstants.SMART_STATUS_CIRCUMVENTION_ATTEMPT_FAILED)
                } else if (Prefs.getPrefSmartTryObfs4() != null) {
                    Log.d(OrbotConstants.TAG, "trying obfs4 didn't work")
                    clearEphemeralSmartConnectionSettings()
                    sendSmartStatusToActivity(OrbotConstants.SMART_STATUS_CIRCUMVENTION_ATTEMPT_FAILED)
                } else {
                    sendSmartStatusToActivity(OrbotConstants.SMART_STATUS_NO_DIRECT)
                }
                stopTorAsync(true)
            } else {
                // tor was connected in the allotted time
                val obfs4 = Prefs.getPrefSmartTryObfs4()
                if (obfs4 != null) {
                    // set these obfs4 bridges
                    Prefs.setBridgesList(obfs4)
                    Prefs.putConnectionPathway(Prefs.PATHWAY_CUSTOM)
                } else if (Prefs.getPrefSmartTrySnowflake()) {
                    // set snowflake
                    Prefs.putConnectionPathway(Prefs.PATHWAY_SNOWFLAKE)
                }
                clearEphemeralSmartConnectionSettings()
            }
        }, (if (((TRIES_DELETE++) != 2)) TIMEOUT_MS else 10000).toLong())
    }

    private fun clearEphemeralSmartConnectionSettings() {
        Prefs.putPrefSmartTryObfs4(null)
        Prefs.putPrefSmartTrySnowflake(false)
    }

    private fun sendSmartStatusToActivity(status: String) {
        val intent = Intent(OrbotConstants.LOCAL_ACTION_SMART_CONNECT_EVENT).putExtra(
            OrbotConstants.LOCAL_EXTRA_SMART_STATUS,
            status
        )
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    @Throws(SecurityException::class)
    private fun updateV3OnionNames() {
        val contentResolver = applicationContext.contentResolver
        val onionServices =
            contentResolver.query(V3_ONION_SERVICES_CONTENT_URI!!, null, null, null, null)
        if (onionServices != null) {
            try {
                while (onionServices.moveToNext()) {
                    val domainIndex = onionServices.getColumnIndex(OnionService.DOMAIN)
                    val pathIndex = onionServices.getColumnIndex(OnionService.PATH)
                    val idIndex = onionServices.getColumnIndex(BaseColumns._ID)
                    if (domainIndex < 0 || pathIndex < 0 || idIndex < 0) continue
                    var domain = onionServices.getString(domainIndex)
                    if (domain == null || TextUtils.isEmpty(domain)) {
                        val path = onionServices.getString(pathIndex)
                        val v3OnionDirPath =
                            File(mV3OnionBasePath!!.absolutePath, path).canonicalPath
                        val hostname = File(v3OnionDirPath, "hostname")
                        if (hostname.exists()) {
                            val id = onionServices.getInt(idIndex)
                            domain = Utils.readInputStreamAsString(FileInputStream(hostname))
                                .trim { it <= ' ' }
                            val fields = ContentValues()
                            fields.put(OnionService.DOMAIN, domain)
                            contentResolver.update(
                                V3_ONION_SERVICES_CONTENT_URI!!,
                                fields,
                                BaseColumns._ID + "=" + id,
                                null
                            )
                        }
                    }
                }
                /*
                This old status hack is temporary and fixes the issue reported by syphyr at
                https://github.com/guardianproject/orbot/pull/556
                Down the line a better approach needs to happen for sending back the onion names updated
                status, perhaps just adding it as an extra to the normal Intent callback...
                 */
                val oldStatus = currentStatus
                val intent = Intent(OrbotConstants.LOCAL_ACTION_V3_NAMES_UPDATED)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

                currentStatus = oldStatus
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onionServices.close()
        }
    }

    @Synchronized
    @Throws(Exception::class)
    private fun startTorService() {
        updateTorConfigCustom(
            TorService.getDefaultsTorrc(this), """
                DNSPort 0
                TransPort 0
                DisableNetwork 1
                
                """.trimIndent(), false
        )

        val fileTorrcCustom = updateTorrcCustomFile()
        if ((!fileTorrcCustom!!.exists()) || (!fileTorrcCustom.canRead())) return

        sendCallbackLogMessage(getString(R.string.status_starting_up))

        torServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                //moved torService to a local variable, since we only need it once

                val torService = (iBinder as LocalBinder).service

                while ((torService.torControlConnection.also { conn = it }) == null) {
                    try {
                        Thread.sleep(500)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }

                //wait another second before we set our own event listener
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                mOrbotRawEventListener = OrbotRawEventListener(this@OrbotService)

                if (conn != null) {
                    try {
                        initControlConnection()
                        if (conn == null) return  // maybe there was an error setting up the control connection


                        //override the TorService event listener
                        conn!!.addRawEventListener(mOrbotRawEventListener)

                        logNotice(getString(R.string.log_notice_connected_to_tor_control_port))

                        //now set our own events
                        val events = mutableListOf(
                            TorControlCommands.EVENT_OR_CONN_STATUS,
                            TorControlCommands.EVENT_CIRCUIT_STATUS,
                            TorControlCommands.EVENT_NOTICE_MSG,
                            TorControlCommands.EVENT_WARN_MSG,
                            TorControlCommands.EVENT_ERR_MSG,
                            TorControlCommands.EVENT_BANDWIDTH_USED,
                            TorControlCommands.EVENT_NEW_DESC,
                            TorControlCommands.EVENT_ADDRMAP
                        )
                        if (Prefs.useDebugLogging()) {
                            events.add(TorControlCommands.EVENT_DEBUG_MSG)
                            events.add(TorControlCommands.EVENT_INFO_MSG)
                        }

                        if (Prefs.useDebugLogging()) events.add(TorControlCommands.EVENT_STREAM_STATUS)

                        conn!!.setEvents(events)
                        logNotice(getString(R.string.log_notice_added_event_handler))
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                if (Prefs.useDebugLogging()) Log.d(
                    OrbotConstants.TAG,
                    "TorService: onServiceDisconnected"
                )
                sendLocalStatusOffBroadcast()
            }

            override fun onNullBinding(componentName: ComponentName) {
                Log.w(OrbotConstants.TAG, "TorService: was unable to bind: onNullBinding")
            }

            override fun onBindingDied(componentName: ComponentName) {
                Log.w(OrbotConstants.TAG, "TorService: onBindingDied")
                sendLocalStatusOffBroadcast()
            }
        }

        val serviceIntent = Intent(this, TorService::class.java)
        shouldUnbindTorService = if (Build.VERSION.SDK_INT < 29) {
            bindService(serviceIntent, torServiceConnection as ServiceConnection, BIND_AUTO_CREATE)
        } else {
            bindService(serviceIntent, BIND_AUTO_CREATE, mExecutor,
                torServiceConnection as ServiceConnection
            )
        }
    }

    private fun sendLocalStatusOffBroadcast() {
        val localOffStatus = Intent(OrbotConstants.LOCAL_ACTION_STATUS).putExtra(
            OrbotConstants.EXTRA_STATUS,
            OrbotConstants.STATUS_OFF
        )
        LocalBroadcastManager.getInstance(this).sendBroadcast(localOffStatus)
    }

    fun exec(run: Runnable?) {
        mExecutor.execute(run)
    }

    private fun initControlConnection() {
        if (conn != null) {
            try {
                var confSocks = conn!!.getInfo("net/listeners/socks")
                var st = StringTokenizer(confSocks, " ")
                if (confSocks.trim { it <= ' ' }.isEmpty()) {
                    mPortSOCKS = 0
                } else {
                    confSocks = st.nextToken().split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1]
                    confSocks = confSocks.substring(0, confSocks.length - 1)
                    mPortSOCKS = confSocks.toInt()
                }
                var confHttp = conn!!.getInfo("net/listeners/httptunnel")
                if (confHttp.trim { it <= ' ' }.isEmpty()) {
                    mPortHTTP = 0
                } else {
                    st = StringTokenizer(confHttp, " ")
                    confHttp = st.nextToken().split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1]
                    confHttp = confHttp.substring(0, confHttp.length - 1)
                    mPortHTTP = confHttp.toInt()
                }
                var confDns = conn!!.getInfo("net/listeners/dns")
                st = StringTokenizer(confDns, " ")
                if (st.hasMoreTokens()) {
                    confDns = st.nextToken().split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1]
                    confDns = confDns.substring(0, confDns.length - 1)
                    mPortDns = confDns.toInt()
                    Prefs.getSharedPrefs(applicationContext).edit()
                        .putInt(OrbotConstants.PREFS_DNS_PORT, mPortDns).apply()
                }

                var confTrans = conn!!.getInfo("net/listeners/trans")
                st = StringTokenizer(confTrans, " ")
                if (st.hasMoreTokens()) {
                    confTrans = st.nextToken().split(":".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[1]
                    confTrans = confTrans.substring(0, confTrans.length - 1)
                    mPortTrans = confTrans.toInt()
                }

                sendCallbackPorts(mPortSOCKS, mPortHTTP, mPortDns, mPortTrans)
            } catch (e: IOException) {
                e.printStackTrace()
                stopTorOnError(e.localizedMessage!!)
                conn = null
            } catch (npe: NullPointerException) {
                Log.e(OrbotConstants.TAG, "NPE reached... how???")
                npe.printStackTrace()
                stopTorOnError("stopping from NPE")
                conn = null
            }
        }
    }

    fun sendSignalActive() {
        if (conn != null && currentStatus == OrbotConstants.STATUS_ON) {
            try {
                conn!!.signal("ACTIVE")
            } catch (e: IOException) {
                debug("error send active: " + e.localizedMessage)
            }
        }
    }

    fun newIdentity() {
        if (conn == null) return
        object : Thread() {
            override fun run() {
                try {
                    if (conn != null && currentStatus == OrbotConstants.STATUS_ON) {
                        mNotifyBuilder!!.setSubText(null) // clear previous exit node info if present
                        showToolbarNotification(
                            getString(R.string.newnym),
                            NOTIFY_ID,
                            R.drawable.ic_stat_tor
                        )
                        conn!!.signal(TorControlCommands.SIGNAL_NEWNYM)
                    }
                } catch (ioe: Exception) {
                    debug("error requesting newnym: " + ioe.localizedMessage)
                }
            }
        }.start()
    }

    fun sendCallbackBandwidth(
        lastWritten: Long,
        lastRead: Long,
        totalWritten: Long,
        totalRead: Long
    ) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(OrbotConstants.LOCAL_ACTION_BANDWIDTH).putExtra(
                OrbotConstants.LOCAL_EXTRA_TOTAL_WRITTEN,
                totalWritten
            ).putExtra(OrbotConstants.LOCAL_EXTRA_TOTAL_READ, totalRead)
                .putExtra(OrbotConstants.LOCAL_EXTRA_LAST_WRITTEN, lastWritten)
                .putExtra(OrbotConstants.LOCAL_EXTRA_LAST_READ, lastRead)
        )
    }

    private fun sendCallbackLogMessage(logMessage: String) {
        var notificationMessage = logMessage
        val localIntent = Intent(OrbotConstants.LOCAL_ACTION_LOG).putExtra(
            OrbotConstants.LOCAL_EXTRA_LOG,
            logMessage
        )
        if (logMessage.contains(OrbotConstants.LOG_NOTICE_HEADER)) {
            notificationMessage =
                notificationMessage.substring(OrbotConstants.LOG_NOTICE_HEADER.length)
            if (notificationMessage.contains(OrbotConstants.LOG_NOTICE_BOOTSTRAPPED)) {
                var percent =
                    notificationMessage.substring(OrbotConstants.LOG_NOTICE_BOOTSTRAPPED.length)
                percent = percent.substring(0, percent.indexOf('%')).trim { it <= ' ' }
                localIntent.putExtra(OrbotConstants.LOCAL_EXTRA_BOOTSTRAP_PERCENT, percent)
                mNotifyBuilder!!.setProgress(100, percent.toInt(), false)
                notificationMessage =
                    notificationMessage.substring(notificationMessage.indexOf(':') + 1)
                        .trim { it <= ' ' }
            }
        }
        showToolbarNotification(notificationMessage, NOTIFY_ID, R.drawable.ic_stat_tor)
        mHandler!!.post {
            LocalBroadcastManager.getInstance(this@OrbotService).sendBroadcast(localIntent)
        }
    }

    private fun sendCallbackPorts(socksPort: Int, httpPort: Int, dnsPort: Int, transPort: Int) {
        val intent = Intent(OrbotConstants.LOCAL_ACTION_PORTS).putExtra(
            OrbotConstants.EXTRA_SOCKS_PROXY_PORT,
            socksPort
        ).putExtra(OrbotConstants.EXTRA_HTTP_PROXY_PORT, httpPort)
            .putExtra(OrbotConstants.EXTRA_DNS_PORT, dnsPort)
            .putExtra(OrbotConstants.EXTRA_TRANS_PORT, transPort)

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        if (Prefs.useVpn() && mVpnManager != null) mVpnManager!!.handleIntent(Builder(), intent)
    }

    @Throws(IOException::class)
    private fun processSettingsImpl(extraLines: StringBuffer?): StringBuffer? {
        var extraLines = extraLines
        logNotice(getString(R.string.updating_settings_in_tor_service))
        val prefs = Prefs.getSharedPrefs(applicationContext)
        val becomeRelay = prefs.getBoolean(OrbotConstants.PREF_OR, false)
        val reachableAddresses = prefs.getBoolean(OrbotConstants.PREF_REACHABLE_ADDRESSES, false)
        val enableStrictNodes = prefs.getBoolean("pref_strict_nodes", false)
        val entranceNodes = prefs.getString("pref_entrance_nodes", "")
        val exitNodes = prefs.getString("pref_exit_nodes", "")
        val excludeNodes = prefs.getString("pref_exclude_nodes", "")

        val pathway = Prefs.getConnectionPathway()
        if (pathway == Prefs.PATHWAY_SMART) {
            // todo for now ...
        } else if (pathway == Prefs.PATHWAY_DIRECT) {
            extraLines = processSettingsImplDirectPathway(extraLines)
        } else {
            // snowflake or obfs4
            extraLines!!.append("UseBridges 1").append('\n')
            if (pathway.startsWith(Prefs.PATHWAY_SNOWFLAKE) || Prefs.getPrefSmartTrySnowflake()) {
                extraLines = processSettingsImplSnowflake(extraLines)
            } else if (pathway == Prefs.PATHWAY_CUSTOM || Prefs.getPrefSmartTryObfs4() != null) {
                extraLines = processSettingsImplObfs4(extraLines)
            }
        }
        val fileGeoIP = File(appBinHome, OrbotConstants.GEOIP_ASSET_KEY)
        val fileGeoIP6 = File(appBinHome, OrbotConstants.GEOIP6_ASSET_KEY)

        if (fileGeoIP.exists()) { // only apply geoip if it exists
            extraLines!!.append("GeoIPFile" + ' ').append(fileGeoIP.canonicalPath).append('\n')
            extraLines.append("GeoIPv6File" + ' ').append(fileGeoIP6.canonicalPath).append('\n')
        }

        if (!TextUtils.isEmpty(entranceNodes)) extraLines!!.append("EntryNodes ")
            .append(entranceNodes).append('\n')

        if (!TextUtils.isEmpty(exitNodes)) extraLines!!.append("ExitNodes ").append(exitNodes)
            .append('\n')

        if (!TextUtils.isEmpty(excludeNodes)) extraLines!!.append("ExcludeNodes ")
            .append(excludeNodes).append('\n')

        extraLines!!.append("StrictNodes ").append(if (enableStrictNodes) "1" else "0").append('\n')

        extraLines.append("\n")

        try {
            if (reachableAddresses) {
                val reachableAddressesPorts =
                    prefs.getString(OrbotConstants.PREF_REACHABLE_ADDRESSES_PORTS, "*:80,*:443")
                extraLines.append("ReachableAddresses" + ' ').append(reachableAddressesPorts)
                    .append('\n')
            }
        } catch (e: Exception) {
            showToolbarNotification(
                getString(R.string.your_reachableaddresses_settings_caused_an_exception_),
                ERROR_NOTIFY_ID,
                R.drawable.ic_stat_notifyerr
            )
            return null
        }

        try {
            if (becomeRelay && (!Prefs.bridgesEnabled()) && (!reachableAddresses)) {
                val orPort = prefs.getString(OrbotConstants.PREF_OR_PORT, "9001")?.toInt()
                val nickname = prefs.getString(OrbotConstants.PREF_OR_NICKNAME, "Orbot")
                val dnsFile = writeDNSFile()

                extraLines.append("ServerDNSResolvConfFile")
                    .append(' ')
                    .append(dnsFile)
                    .append('\n') // DNSResolv is not a typo
                    .append("ORPort")
                    .append(' ')
                    .append(orPort)
                    .append('\n')
                    .append("Nickname")
                    .append(' ')
                    .append(nickname)
                    .append('\n')
                    .append("ExitPolicy")
                    .append(' ')
                    .append("reject *:*")
                    .append('\n')
            }
        } catch (e: Exception) {
            showToolbarNotification(
                getString(R.string.your_relay_settings_caused_an_exception_),
                ERROR_NOTIFY_ID,
                R.drawable.ic_stat_notifyerr
            )
            return null
        }

        if (Prefs.hostOnionServicesEnabled()) {
            val contentResolver = applicationContext.contentResolver
            addV3OnionServicesToTorrc(extraLines, contentResolver)
            addV3ClientAuthToTorrc(extraLines, contentResolver)
        }

        return extraLines
    }

    private fun processSettingsImplSnowflake(extraLines: StringBuffer?): StringBuffer? {
        Log.d(OrbotConstants.TAG, "in snowflake torrc config")
        extraLines?.append("ClientTransportPlugin snowflake socks5 127.0.0.1:" + IPtProxy.snowflakePort())
            ?.append('\n')
            ?.append("Bridge ")
            ?.append(getCdnFront("snowflake-broker-1"))
            ?.append("\n")
            ?.append("Bridge ")
            ?.append(getCdnFront("snowflake-broker-2"))
            ?.append("\n")
        return extraLines
    }

    private fun processSettingsImplObfs4(extraLines: StringBuffer?): StringBuffer {
        Log.d(OrbotConstants.TAG, "in obfs4 torrc config")
        extraLines!!.append("ClientTransportPlugin obfs4 socks5 127.0.0.1:" + IPtProxy.obfs4Port())
            .append('\n')
        val bridgeList: String = if (Prefs.getConnectionPathway() == Prefs.PATHWAY_CUSTOM) {
            Prefs.getBridgesList()
        } else Prefs.getPrefSmartTryObfs4()
        val customBridges = parseBridgesFromSettings(bridgeList)
        for (b in customBridges) extraLines.append("Bridge ").append(b).append("\n")
        return extraLines
    }

    private fun processSettingsImplDirectPathway(extraLines: StringBuffer?): StringBuffer {
        val prefs = Prefs.getSharedPrefs(applicationContext)
        extraLines?.append("UseBridges 0")?.append('\n')
        if (!Prefs.useVpn()) { //set the proxy here if we aren't using a bridge
            val proxyType = prefs.getString("pref_proxy_type", null)
            if (!proxyType.isNullOrEmpty()) {
                val proxyHost = prefs.getString("pref_proxy_host", null)
                val proxyPort = prefs.getString("pref_proxy_port", null)
                val proxyUser = prefs.getString("pref_proxy_username", null)
                val proxyPass = prefs.getString("pref_proxy_password", null)

                if (!proxyHost.isNullOrEmpty() && !proxyPort.isNullOrEmpty()) {
                    extraLines?.append(proxyType)?.append("Proxy")?.append(' ')?.append(proxyHost)
                        ?.append(':')?.append(proxyPort)?.append('\n')

                    if (proxyUser != null && proxyPass != null) {
                        if (proxyType.equals("socks5", ignoreCase = true)) {
                            extraLines?.append("Socks5ProxyUsername")
                                ?.append(' ')
                                ?.append(proxyUser)
                                ?.append('\n')
                            extraLines?.append("Socks5ProxyPassword")
                                ?.append(' ')
                                ?.append(proxyPass)
                                ?.append('\n')
                        } else extraLines?.append(proxyType)
                            ?.append("ProxyAuthenticator")
                            ?.append(' ')
                            ?.append(proxyUser)
                            ?.append(':')
                            ?.append(proxyPort)
                            ?.append('\n')
                    } else if (proxyPass != null) {
                        extraLines?.append(proxyType)
                            ?.append("ProxyAuthenticator")
                            ?.append(' ')
                            ?.append(':')
                            ?.append(proxyPort)
                            ?.append('\n')
                    }
                }
            }
        }
        return extraLines!!
    }

    fun showBandwidthNotification(message: String, isActiveTransfer: Boolean) {
        if (currentStatus != OrbotConstants.STATUS_ON) return
        val icon = if (!isActiveTransfer) R.drawable.ic_stat_tor else R.drawable.ic_stat_tor_xfer
        showToolbarNotification(message, NOTIFY_ID, icon)
    }

    private fun addV3OnionServicesToTorrc(torrc: StringBuffer?, contentResolver: ContentResolver) {
        try {
            val onionServices = contentResolver.query(
                V3_ONION_SERVICES_CONTENT_URI!!,
                V3_ONION_SERVICE_PROJECTION,
                OnionService.ENABLED + "=1",
                null,
                null
            )
            if (onionServices != null) {
                while (onionServices.moveToNext()) {
                    val idIndex = onionServices.getColumnIndex(BaseColumns._ID)
                    val portIndex = onionServices.getColumnIndex(OnionService.PORT)
                    val onionPortIndex = onionServices.getColumnIndex(OnionService.ONION_PORT)
                    val pathIndex = onionServices.getColumnIndex(OnionService.PATH)
                    val domainIndex = onionServices.getColumnIndex(OnionService.DOMAIN)
                    // Ensure that are have all the indexes before trying to use them
                    if (idIndex < 0 || portIndex < 0 || onionPortIndex < 0 || pathIndex < 0 || domainIndex < 0) continue

                    val id = onionServices.getInt(idIndex)
                    val localPort = onionServices.getInt(portIndex)
                    val onionPort = onionServices.getInt(onionPortIndex)
                    var path = onionServices.getString(pathIndex)
                    val domain = onionServices.getString(domainIndex)
                    if (path == null) {
                        path = "v3"
                        if (domain == null) path += UUID.randomUUID().toString()
                        else path += localPort
                        val cv = ContentValues()
                        cv.put(OnionService.PATH, path)
                        contentResolver.update(
                            V3_ONION_SERVICES_CONTENT_URI!!,
                            cv,
                            BaseColumns._ID + "=" + id,
                            null
                        )
                    }
                    val v3DirPath = File(mV3OnionBasePath!!.absolutePath, path).canonicalPath
                    torrc!!.append("HiddenServiceDir ").append(v3DirPath).append("\n")
                    torrc.append("HiddenServiceVersion 3").append("\n")
                    torrc.append("HiddenServicePort ").append(onionPort).append(" 127.0.0.1:")
                        .append(localPort).append("\n")
                }
                onionServices.close()
            }
        } catch (e: Exception) {
            Log.e(OrbotConstants.TAG, e.message!!)
        }
    }

    private fun addV3ClientAuthToTorrc(torrc: StringBuffer?, contentResolver: ContentResolver) {
        val v3auths = contentResolver.query(
            V3_CLIENT_AUTH_URI!!,
            V3_CLIENT_AUTH_PROJECTION,
            V3ClientAuth.ENABLED + "=1",
            null,
            null
        )
        if (v3auths != null) {
            for (file in mV3AuthBasePath!!.listFiles()!!) {
                if (!file.isDirectory) file.delete() // todo the adapter should maybe just write these files and not do this in service...
            }
            torrc!!.append("ClientOnionAuthDir " + mV3AuthBasePath!!.absolutePath).append('\n')
            try {
                var i = 0
                while (v3auths.moveToNext()) {
                    val domainIndex = v3auths.getColumnIndex(V3ClientAuth.DOMAIN)
                    val hashIndex = v3auths.getColumnIndex(V3ClientAuth.HASH)
                    // Ensure that are have all the indexes before trying to use them
                    if (domainIndex < 0 || hashIndex < 0) continue
                    val domain = v3auths.getString(domainIndex)
                    val hash = v3auths.getString(hashIndex)
                    val authFile = File(mV3AuthBasePath, i++.toString() + ".auth_private")
                    authFile.createNewFile()
                    val fos = FileOutputStream(authFile)
                    fos.write(buildV3ClientAuthFile(domain, hash).toByteArray())
                    fos.close()
                }
            } catch (e: Exception) {
                Log.e(OrbotConstants.TAG, "error adding v3 client auth...")
            } finally {
                v3auths.close()
            }
        }
    }

    //using Google DNS for now as the public DNS server
    @Throws(IOException::class)
    private fun writeDNSFile(): String {
        val file = File(appBinHome, "resolv.conf")
        val bw = PrintWriter(FileWriter(file))
        bw.println("nameserver 8.8.8.8")
        bw.println("nameserver 8.8.4.4")
        bw.close()
        return file.canonicalPath
    }

    @SuppressLint("NewApi")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            TRIM_MEMORY_BACKGROUND -> debug("trim memory requested: app in the background")
            TRIM_MEMORY_COMPLETE -> debug("trim memory requested: cleanup all memory")
            TRIM_MEMORY_MODERATE -> debug("trim memory requested: clean up some memory")
            TRIM_MEMORY_RUNNING_CRITICAL -> debug("trim memory requested: memory on device is very low and critical")
            TRIM_MEMORY_RUNNING_LOW -> debug("trim memory requested: memory on device is running low")
            TRIM_MEMORY_RUNNING_MODERATE -> debug("trim memory requested: memory on device is moderate")
            TRIM_MEMORY_UI_HIDDEN -> debug("trim memory requested: app is not showing UI anymore")
        }
    }

    fun setNotificationSubtext(message: String?) {
        if (mNotifyBuilder != null) {
            // stop showing expanded notifications if the user changed the after starting Orbot
            // if (!Prefs.showExpandedNotifications()) message = null;
            mNotifyBuilder!!.setSubText(message)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d(OrbotConstants.TAG, "OrbotService: onBind")
        return super.onBind(intent) // invoking super class will call onRevoke() when appropriate
    }

    // system calls this method when VPN disconnects (either by the user or another VPN app)
    override fun onRevoke() {
        Prefs.putUseVpn(false)
        mVpnManager!!.handleIntent(Builder(), Intent(OrbotConstants.ACTION_STOP_VPN))
        // tell UI, if it's open, to update immediately (don't wait for onResume() in Activity...)
        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(Intent(OrbotConstants.ACTION_STOP_VPN))
    }

    private fun setExitNode(newExits: String?) {
        if (TextUtils.isEmpty(newExits)) {
            Prefs.setExitNodes("")

            if (conn != null) {
                try {
                    val resetBuffer = ArrayList<String>()
                    resetBuffer.add("ExitNodes")
                    resetBuffer.add("StrictNodes")
                    conn!!.resetConf(resetBuffer)
                    conn!!.setConf("DisableNetwork", "1")
                    conn!!.setConf("DisableNetwork", "0")
                } catch (ioe: Exception) {
                    Log.e(OrbotConstants.TAG, "Connection exception occurred resetting exits", ioe)
                }
            }
        } else {
            Prefs.setExitNodes("{$newExits}")

            if (conn != null) {
                try {
                    val fileGeoIP = File(appBinHome, OrbotConstants.GEOIP_ASSET_KEY)
                    val fileGeoIP6 = File(appBinHome, OrbotConstants.GEOIP6_ASSET_KEY)

                    conn!!.setConf("GeoIPFile", fileGeoIP.canonicalPath)
                    conn!!.setConf("GeoIPv6File", fileGeoIP6.canonicalPath)
                    conn!!.setConf("ExitNodes", newExits)
                    conn!!.setConf("StrictNodes", "1")
                    conn!!.setConf("DisableNetwork", "1")
                    conn!!.setConf("DisableNetwork", "0")
                } catch (ioe: Exception) {
                    Log.e(OrbotConstants.TAG, "Connection exception occurred resetting exits", ioe)
                }
            }
        }
    }

    object OnionService : BaseColumns {
        const val NAME: String = "name"
        const val PORT: String = "port"
        const val ONION_PORT: String = "onion_port"
        const val DOMAIN: String = "domain"
        const val ENABLED: String = "enabled"
        const val PATH: String = "filepath"
    }

    object V3ClientAuth : BaseColumns {
        const val DOMAIN: String = "domain"
        const val HASH: String = "hash"
        const val ENABLED: String = "enabled"
    }


    private inner class IncomingIntentRouter(val mIntent: Intent) : Runnable {
        override fun run() {
            val action = mIntent.action
            if (TextUtils.isEmpty(action)) return
            when (action) {
                OrbotConstants.ACTION_START -> {
                    val connectionPathway = Prefs.getConnectionPathway()
                    if (connectionPathway == Prefs.PATHWAY_SNOWFLAKE || Prefs.getPrefSmartTrySnowflake()) {
                        startSnowflakeClientDomainFronting()
                    } else if (connectionPathway == Prefs.PATHWAY_SNOWFLAKE_AMP) {
                        startSnowflakeClientAmpRendezvous()
                    } else if (connectionPathway == Prefs.PATHWAY_CUSTOM || Prefs.getPrefSmartTryObfs4() != null) {
                        IPtProxy.startLyrebird("DEBUG", false, false, null)
                    }
                    startTor()
                    replyWithStatus(mIntent)
                    if (Prefs.useVpn()) {
                        if (mVpnManager != null && (!mVpnManager!!.isStarted)) { // start VPN here
                            val vpnIntent = prepare(this@OrbotService)
                            if (vpnIntent == null) { //then we can run the VPN
                                mVpnManager!!.handleIntent(Builder(), mIntent)
                            }
                        }

                        if (mPortSOCKS != -1 && mPortHTTP != -1) sendCallbackPorts(
                            mPortSOCKS,
                            mPortHTTP,
                            mPortDns,
                            mPortTrans
                        )
                    }
                }

                OrbotConstants.ACTION_STOP -> {
                    val userIsQuittingOrbot =
                        mIntent.getBooleanExtra(OrbotConstants.ACTION_STOP_FOREGROUND_TASK, false)
                    stopTorAsync(!userIsQuittingOrbot)
                }

                OrbotConstants.ACTION_UPDATE_ONION_NAMES -> updateV3OnionNames()
                OrbotConstants.ACTION_STOP_FOREGROUND_TASK -> stopForeground(true)
                OrbotConstants.ACTION_START_VPN -> {
                    if (mVpnManager != null && (!mVpnManager!!.isStarted)) {
                        //start VPN here
                        val vpnIntent = prepare(this@OrbotService)
                        if (vpnIntent == null) { //then we can run the VPN
                            mVpnManager!!.handleIntent(Builder(), mIntent)
                        }
                    }
                    if (mPortSOCKS != -1 && mPortHTTP != -1) sendCallbackPorts(
                        mPortSOCKS,
                        mPortHTTP,
                        mPortDns,
                        mPortTrans
                    )
                }

                OrbotConstants.ACTION_STOP_VPN -> {
                    if (mVpnManager != null) mVpnManager!!.handleIntent(Builder(), mIntent)
                }

                OrbotConstants.ACTION_RESTART_VPN -> {
                    if (mVpnManager != null) mVpnManager!!.restartVPN(Builder())
                }

                OrbotConstants.ACTION_STATUS -> {
                    if (currentStatus == OrbotConstants.STATUS_OFF) showToolbarNotification(
                        getString(R.string.open_orbot_to_connect_to_tor),
                        NOTIFY_ID,
                        R.drawable.ic_stat_tor
                    )
                    replyWithStatus(mIntent)
                }

                TorControlCommands.SIGNAL_RELOAD -> requestTorRereadConfig()
                TorControlCommands.SIGNAL_NEWNYM -> newIdentity()
                OrbotConstants.CMD_ACTIVE -> {
                    sendSignalActive()
                    replyWithStatus(mIntent)
                }

                OrbotConstants.CMD_SET_EXIT -> setExitNode(mIntent.getStringExtra("exit"))
                OrbotConstants.ACTION_LOCAL_LOCALE_SET -> configLanguage()
                OrbotConstants.CMD_SNOWFLAKE_PROXY -> {
                    if (Prefs.beSnowflakeProxy()) {
                        enableSnowflakeProxy()
                    } else disableSnowflakeProxy()
                }

                else -> Log.w(OrbotConstants.TAG, "unhandled OrbotService Intent: $action")
            }
        }
    }

    inner class ActionBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                TorControlCommands.SIGNAL_NEWNYM -> newIdentity()
                OrbotConstants.CMD_ACTIVE -> sendSignalActive()
                OrbotConstants.LOCAL_ACTION_NOTIFICATION_START -> startTor()
                TorService.ACTION_ERROR -> {
                    if (showTorServiceErrorMsg) {
                        Toast.makeText(
                            context,
                            getString(R.string.orbot_config_invalid),
                            Toast.LENGTH_LONG
                        ).show()
                        showTorServiceErrorMsg = false
                    }
                    stopTor()
                }

                OrbotConstants.ACTION_STATUS -> {
                    // hack for https://github.com/guardianproject/tor-android/issues/73 remove when fixed
                    val newStatus = intent.getStringExtra(OrbotConstants.EXTRA_STATUS)
                    if (currentStatus == OrbotConstants.STATUS_OFF && newStatus == OrbotConstants.STATUS_STOPPING) return
                    currentStatus = newStatus
                    if (currentStatus == OrbotConstants.STATUS_OFF) {
                        showDeactivatedNotification()
                    }
                    sendStatusToOrbotActivity()
                }
            }
        }
    }

    private fun sendStatusToOrbotActivity() {
        val localStatus = Intent(OrbotConstants.LOCAL_ACTION_STATUS).putExtra(
            OrbotConstants.EXTRA_STATUS,
            currentStatus
        )
        LocalBroadcastManager.getInstance(this@OrbotService)
            .sendBroadcast(localStatus) // update the activity with what's new
    }

    companion object {
        const val BINARY_TOR_VERSION: String = TorService.VERSION_NAME

        const val NOTIFY_ID: Int = 1
        private const val ERROR_NOTIFY_ID = 3

        //these will be set dynamically due to build flavors
        private var V3_ONION_SERVICES_CONTENT_URI: Uri? =
            null //Uri.parse("content://org.torproject.android.ui.v3onionservice/v3");
        private var V3_CLIENT_AUTH_URI: Uri? =
            null //Uri.parse("content://org.torproject.android.ui.v3onionservice.clientauth/v3auth");
        private const val NOTIFICATION_CHANNEL_ID = "orbot_channel_1"
        private val V3_ONION_SERVICE_PROJECTION = arrayOf(
            BaseColumns._ID,
            OnionService.NAME,
            OnionService.DOMAIN,
            OnionService.PORT,
            OnionService.ONION_PORT,
            OnionService.ENABLED,
            OnionService.PATH
        )
        private val V3_CLIENT_AUTH_PROJECTION =
            arrayOf(BaseColumns._ID, V3ClientAuth.DOMAIN, V3ClientAuth.HASH, V3ClientAuth.ENABLED)

        var mPortSOCKS: Int = -1
        var mPortHTTP: Int = -1
        var mPortDns: Int = -1
        var mPortTrans: Int = -1
        var appBinHome: File? = null
        var appCacheHome: File? = null

        /**
         * @param bridgeList bridges that were manually entered into Orbot settings
         * @return Array with each bridge as an element, no whitespace entries see issue #289...
         */
        private fun parseBridgesFromSettings(bridgeList: String): Array<String> {
            // this regex replaces lines that only contain whitespace with an empty String
            return bridgeList
                .trim { it <= ' ' }
                .replace("(?m)^[ \t]*\r?\n".toRegex(), "")
                .split("\\n".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()
        }

        private var mFronts: HashMap<String, String>? = null

        fun loadCdnFronts(context: Context) {
            if (mFronts == null) {
                mFronts = HashMap()

                try {
                    val reader = BufferedReader(InputStreamReader(context.assets.open("fronts")))
                    var line: String
                    while ((reader.readLine().also { line = it }) != null) {
                        val spaceIdx = line.indexOf(' ')
                        val key = line.substring(0, spaceIdx)
                        val `val` = line.substring(spaceIdx + 1)
                        mFronts!![key] = `val`
                    }
                    reader.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        fun getCdnFront(service: String): String? {
            return mFronts!![service]
        }


        private const val TIMEOUT_MS = 15000

        var TRIES_DELETE: Int = 0

        @JvmStatic
        fun formatBandwidthCount(context: Context, bitsPerSecond: Long): String {
            val nf = NumberFormat.getInstance(Locale.getDefault())
            return if (bitsPerSecond < 1e6) nf.format(
                Math.round(
                    ((bitsPerSecond * 10 / 1024).toInt()
                        .toFloat() / 10)
                ).toLong()
            ) + context.getString(R.string.kibibyte_per_second)
            else nf.format(
                Math.round(
                    ((bitsPerSecond * 100 / 1024 / 1024).toInt()
                        .toFloat() / 100)
                ).toLong()
            ) + context.getString(R.string.mebibyte_per_second)
        }

        @JvmStatic
        fun buildV3ClientAuthFile(domain: String, keyHash: String): String {
            return "$domain:descriptor:x25519:$keyHash"
        }
    }
}
