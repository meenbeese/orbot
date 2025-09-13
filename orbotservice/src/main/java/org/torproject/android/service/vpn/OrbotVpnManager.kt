/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.torproject.android.service.vpn

import IPtProxy.IPtProxy
import IPtProxy.PacketFlow

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import android.widget.Toast

import org.pcap4j.packet.IllegalRawDataException
import org.pcap4j.packet.IpPacket
import org.pcap4j.packet.IpSelector
import org.pcap4j.packet.UdpPacket
import org.pcap4j.packet.namednumber.IpNumber
import org.pcap4j.packet.namednumber.UdpPort
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.service.ui.Notifications.getVpnSessionName
import org.torproject.android.service.util.Prefs.getSharedPrefs

import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OrbotVpnManager(service: OrbotService) : Handler.Callback {
    @JvmField
    var isStarted: Boolean = false
    private var mInterface: ParcelFileDescriptor? = null
    private var mTorSocks = -1
    private var mTorDns = -1
    private val mService = service
    private val prefs: SharedPreferences? = getSharedPrefs(mService.applicationContext)
    private var mDnsResolver: DNSResolver? = null

    private val mExec: ExecutorService = Executors.newFixedThreadPool(10)
    private var mThreadPacket: Thread? = null
    private var keepRunningPacket = false

    private var fis: FileInputStream? = null
    private var fos: DataOutputStream? = null

    fun handleIntent(builder: VpnService.Builder, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == null) return
        when (action) {
            OrbotConstants.ACTION_START -> {
                Log.d(TAG, "starting VPN")
                isStarted = true
            }

            OrbotConstants.ACTION_STOP -> {
                isStarted = false
                Log.d(TAG, "stopping VPN")
                stopVPN()

                //reset ports
                mTorSocks = -1
                mTorDns = -1
            }

            OrbotConstants.LOCAL_ACTION_PORTS -> {
                Log.d(TAG, "setting VPN ports")
                val torSocks = intent.getIntExtra(OrbotConstants.EXTRA_SOCKS_PROXY_PORT, -1)
                val torDns = intent.getIntExtra(OrbotConstants.EXTRA_DNS_PORT, -1)

                //if running, we need to restart
                if ((torSocks != -1 && torSocks != mTorSocks && torDns != -1 && torDns != mTorDns)) {
                    mTorSocks = torSocks
                    mTorDns = torDns
                    setupTun2Socks(builder)
                }
            }
        }
    }

    fun restartVPN(builder: VpnService.Builder) {
        stopVPN()
        setupTun2Socks(builder)
    }

    private fun stopVPN() {
        keepRunningPacket = false

        if (mInterface != null) {
            try {
                Log.d(TAG, "closing interface, destroying VPN interface")
                IPtProxy.stopSocks()
                if (fis != null) {
                    fis?.close()
                    fis = null
                }

                if (fos != null) {
                    fos?.close()
                    fos = null
                }

                mInterface?.close()
                mInterface = null
            } catch (e: Exception) {
                Log.d(TAG, "error stopping tun2socks", e)
            } catch (e: Error) {
                Log.d(TAG, "error stopping tun2socks", e)
            }
        }

        if (mThreadPacket != null && mThreadPacket?.isAlive == true) {
            mThreadPacket?.interrupt()
        }
    }

    override fun handleMessage(message: Message): Boolean {
        Toast.makeText(mService, message.what, Toast.LENGTH_SHORT).show()
        return true
    }

    @Synchronized
    private fun setupTun2Socks(builder: VpnService.Builder) {
        try {
            val defaultRoute = "0.0.0.0"
            val virtualGateway = "192.168.50.1"

            builder.addAddress(virtualGateway, 24)
                .addRoute(defaultRoute, 0)
                .addRoute(FAKE_DNS, 32)
                .addDnsServer(FAKE_DNS) // just setting a value here so DNS is captured by TUN interface
                .setSession(getVpnSessionName(mService))

            // handle ipv6
            builder.addAddress("fdfe:dcba:9876::1", 126)
            builder.addRoute("::", 0)

            /*
             * Can't use this since our HTTP proxy is only CONNECT and not a full proxy
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy("localhost",mTorHttp));
            }**/
            doAppBasedRouting(builder)

            // https://developer.android.com/reference/android/net/VpnService.Builder#setMetered(boolean)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)

                // Explicitly allow both families, so we do not block
                // traffic for ones without DNS servers (issue 129).
                builder.allowFamily(OsConstants.AF_INET)
                builder.allowFamily(OsConstants.AF_INET6)
            }

            builder.setBlocking(true)

            mInterface = builder.establish()
            mDnsResolver = DNSResolver(mTorDns)

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                try {
                    startListeningToFD()
                } catch (e: IOException) {
                    Log.d(TAG, "VPN tun listening has stopped", e)
                }
            }, DELAY_FD_LISTEN_MS.toLong())
        } catch (e: Exception) {
            Log.d(TAG, "VPN tun setup has stopped", e)
        }
    }

    @Throws(IOException::class)
    private fun startListeningToFD() {
        if (mInterface == null) return // Prepare hasn't been called yet

        fis = FileInputStream(mInterface?.fileDescriptor)
        fos = DataOutputStream(FileOutputStream(mInterface?.fileDescriptor))

        // write packets back out to TUN
        val pFlow = PacketFlow { packet: ByteArray? ->
            try {
                fos?.write(packet)
            } catch (e: IOException) {
                Log.e(TAG, "error writing to VPN fd", e)
            }
        }

        IPtProxy.startSocks(pFlow, "127.0.0.1", mTorSocks.toLong())

        // read packets from TUN and send to go-tun2socks
        mThreadPacket = Thread {
            val buffer = ByteArray(32767 * 2) // 64k
            keepRunningPacket = true

            while (keepRunningPacket) {
                try {
                    val pLen = fis?.read(buffer) ?: -1 // will block on API 21+

                    if (pLen > 0) {
                        val pdata = buffer.copyOf(pLen)
                        try {
                            val packet = IpSelector.newPacket(pdata, 0, pdata.size)

                            if (packet is IpPacket) {
                                when {
                                    isPacketDNS(packet) -> mExec.execute(
                                        RequestPacketHandler(packet, pFlow, mDnsResolver ?: return@Thread)
                                    )
                                    isPacketICMP(packet) -> {
                                        // drop silently
                                    }
                                    else -> IPtProxy.inputPacket(pdata)
                                }
                            }
                        } catch (e: IllegalRawDataException) {
                            Log.e(TAG, e.localizedMessage ?: "")
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "error reading from VPN fd: " + e.localizedMessage)
                }
            }
        }
        mThreadPacket?.start()
    }

    @Throws(PackageManager.NameNotFoundException::class)
    private fun doAppBasedRouting(builder: VpnService.Builder) {
        val apps: ArrayList<TorifiedApp> = TorifiedApp.Companion.getApps(mService, prefs!!)
        var individualAppsWereSelected = false
        val isLockdownMode = isVpnLockdown(mService)

        apps.forEach { app ->
            if (app.isTorified && app.packageName != mService.packageName) {
                if (prefs.getBoolean(app.packageName + OrbotConstants.APP_TOR_KEY, true)) {
                    builder.addAllowedApplication(app.packageName)
                }
                individualAppsWereSelected = true
            }
        }

        Log.i(
            TAG,
            "App based routing is enabled?=$individualAppsWereSelected, isLockdownMode=$isLockdownMode"
        )

        if (isLockdownMode) {
            /* TODO https://github.com/guardianproject/orbot/issues/774
                Need to allow briar, onionshare, etc to enter orbot's vpn gateway, but not enter the tor
                network, that way these apps can use their own tor connection
                 // TODO  "add" these packages here...
                 */
        }

        if (!individualAppsWereSelected && !isLockdownMode) {
            // disallow orbot itself...
            builder.addDisallowedApplication(mService.packageName)

            // disallow tor apps to avoid tor over tor, Orbot doesn't need to concern itself with them
            for (packageName in OrbotConstants.BYPASS_VPN_PACKAGES) builder.addDisallowedApplication(packageName)
        }
    }

    private fun isVpnLockdown(vpn: VpnService): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vpn.isLockdownEnabled
        } else {
            false
        }
    }

    companion object {
        private const val TAG = "OrbotVpnManager"
        private const val DELAY_FD_LISTEN_MS = 5000

        const val FAKE_DNS: String = "10.0.0.1"

        private fun isPacketDNS(p: IpPacket): Boolean {
            if (p.header.protocol == IpNumber.UDP) {
                val up = p.payload as UdpPacket
                return up.header.dstPort == UdpPort.DOMAIN
            }
            return false
        }

        private fun isPacketICMP(p: IpPacket): Boolean {
            return p.header.protocol == IpNumber.ICMPV4 ||
                   p.header.protocol == IpNumber.ICMPV6
        }
    }
}
