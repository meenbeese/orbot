package org.torproject.android

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.torproject.android.OrbotActivity.Companion.REQUEST_CODE_SETTINGS
import org.torproject.android.OrbotActivity.Companion.REQUEST_VPN_APP_SELECT
import org.torproject.android.core.ui.SettingsActivity
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.ui.*
import org.torproject.android.ui.v3onionservice.OnionServiceActivity
import org.torproject.android.ui.v3onionservice.clientauth.ClientAuthActivity

class MoreFragment : Fragment() {
    private lateinit var lvMore : ListView;

    private var httpPort = -1
    private var socksPort = -1

    private lateinit var tvStatus : TextView

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        (activity as OrbotActivity).fragMore = this
    }


    fun setPorts (newHttpPort : Int, newSocksPort: Int) {
        httpPort = newHttpPort
        socksPort = newSocksPort

        updateStatus()
    }

    private fun updateStatus () {
        val sb = java.lang.StringBuilder()

        sb.append(getString(R.string.proxy_ports)).append(" ")

        if (httpPort != -1 && socksPort != -1) {
            sb.append("HTTP: ").append(httpPort).append(" - ").append(" SOCKS: ").append(socksPort)
        }
        else
        {
            sb.append("none")
        }

        sb.append("\n\n")

        val manager = requireActivity().packageManager
        val info = manager.getPackageInfo(requireActivity().packageName, PackageManager.GET_ACTIVITIES)
        sb.append(getString(R.string.app_name)).append(" ").append(info.versionName).append("\n")
        sb.append("Tor v").append(getTorVersion())

        tvStatus.text = sb.toString()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_more, container, false)
        tvStatus = view.findViewById(R.id.tvVersion)

        updateStatus()
        lvMore = view.findViewById(R.id.lvMoreActions)

        val listItems = arrayListOf(
            OrbotMenuAction(R.string.v3_hosted_services, R.drawable.ic_menu_onion) { startActivity(Intent(requireActivity(), OnionServiceActivity::class.java))},
            OrbotMenuAction(R.string.v3_client_auth_activity_title, R.drawable.ic_shield) { startActivity(Intent(requireActivity(), ClientAuthActivity::class.java))},
            OrbotMenuAction(R.string.btn_choose_apps, R.drawable.ic_choose_apps) {
                activity?.startActivityForResult(Intent(requireActivity(), AppManagerActivity::class.java), REQUEST_VPN_APP_SELECT)
            },
            OrbotMenuAction(R.string.menu_settings, R.drawable.ic_settings_gear) {

              //  activity?.startActivityForResult(SettingsPreferencesFragment.createIntent(requireActivity(), R.xml.preferences), REQUEST_CODE_SETTINGS)
                activity?.startActivityForResult(Intent(context, SettingsActivity::class.java), REQUEST_CODE_SETTINGS)
                                                                                 },
            OrbotMenuAction(R.string.menu_log, R.drawable.ic_log) { showLog()},
            OrbotMenuAction(R.string.menu_about, R.drawable.ic_about) { AboutDialogFragment()
                .show(requireActivity().supportFragmentManager, AboutDialogFragment.TAG)},
            OrbotMenuAction(R.string.menu_exit, R.drawable.ic_exit) { doExit()}
        )
        lvMore.adapter = MoreActionAdapter(requireActivity(), listItems)

        return view;
    }

    private fun getTorVersion(): String? {
        return OrbotService.BINARY_TOR_VERSION.split("-").toTypedArray()[0]
    }


    private fun doExit() {
        val killIntent = Intent(requireActivity(), OrbotService::class.java)
            .setAction(OrbotConstants.ACTION_STOP)
            .putExtra(OrbotConstants.ACTION_STOP_FOREGROUND_TASK, true)
        sendIntentToService(OrbotConstants.ACTION_STOP_VPN)
        requireActivity().startService(killIntent)
        requireActivity().finish()
    }

    private fun sendIntentToService(intent: Intent) = ContextCompat.startForegroundService(requireActivity(), intent)
    private fun sendIntentToService(action: String) = sendIntentToService(
        Intent(requireActivity(), OrbotService::class.java).apply {
            this.action = action
        })

    private fun showLog() {
        (activity as OrbotActivity).showLog()
    }

    /**
    private fun configureNavigationMenu() {
    navigationView.getHeaderView(0).let {
    tvPorts = it.findViewById(R.id.tvPorts)
    torStatsGroup = it.findViewById(R.id.torStatsGroup)
    }
    // apply theme to colorize menu headers
    navigationView.menu.forEach { menu -> menu.subMenu?.let { // if it has a submenu, we want to color it
    menu.title = SpannableString(menu.title).apply {
    setSpan(TextAppearanceSpan(this@OrbotActivity, R.style.NavigationGroupMenuHeaders), 0, this.length, 0)
    }
    } }
    // set click listeners for menu items
    navigationView.setNavigationItemSelectedListener {
    when (it.itemId) {
    R.id.menu_tor_connection -> {
    openConfigureTorConnection()
    //closeDrawer()
    }
    R.id.menu_help_others -> openKindnessMode()
    R.id.menu_choose_apps -> {
    startActivityForResult(Intent(this, AppManagerActivity::class.java), REQUEST_VPN_APP_SELECT)
    }
    R.id.menu_exit -> doExit()
    R.id.menu_log -> showLog()
    R.id.menu_v3_onion_services -> startActivity(Intent(this, OnionServiceActivity::class.java))
    R.id.menu_v3_onion_client_auth -> startActivity(Intent(this, ClientAuthActivity::class.java))
    R.id.menu_settings -> startActivityForResult(SettingsPreferencesActivity.createIntent(this, R.xml.preferences), REQUEST_CODE_SETTINGS)
    R.id.menu_faq -> Toast.makeText(this, "TODO FAQ not implemented...", Toast.LENGTH_LONG).show()
    R.id.menu_about -> {
    AboutDialogFragment()
    .show(supportFragmentManager, AboutDialogFragment.TAG)
    //closeDrawer()
    }
    else -> {}
    }
    true
    }

    }**/
}