/* Copyright (c) 2009, Nathan Freitas, Orbot/The Guardian Project - http://openideals.com/guardian */
/* See LICENSE for licensing information */

package org.torproject.android.service;

import android.content.Intent;

import java.util.Arrays;
import java.util.List;

public final class OrbotConstants {

    public static final String TAG = "Orbot";

    public static final String PREF_OR = "pref_or";
    public static final String PREF_OR_PORT = "pref_or_port";
    public static final String PREF_OR_NICKNAME = "pref_or_nickname";
    public static final String PREF_REACHABLE_ADDRESSES = "pref_reachable_addresses";
    public static final String PREF_REACHABLE_ADDRESSES_PORTS = "pref_reachable_addresses_ports";

    public static final String PREF_TOR_SHARED_PREFS = "org.torproject.android_preferences";

    public static final String PREF_SOCKS = "pref_socks";

    public static final String PREF_HTTP = "pref_http";

    public static final String PREF_ISOLATE_DEST = "pref_isolate_dest";
    public static final String PREF_ISOLATE_PORT = "pref_isolate_port";
    public static final String PREF_ISOLATE_PROTOCOL = "pref_isolate_protocol";

    public static final String PREF_CONNECTION_PADDING = "pref_connection_padding";
    public static final String PREF_REDUCED_CONNECTION_PADDING = "pref_reduced_connection_padding";
    public static final String PREF_CIRCUIT_PADDING = "pref_circuit_padding";
    public static final String PREF_REDUCED_CIRCUIT_PADDING = "pref_reduced_circuit_padding";

    public static final String PREF_PREFER_IPV6 = "pref_prefer_ipv6";
    public static final String PREF_DISABLE_IPV4 = "pref_disable_ipv4";


    public static final String APP_TOR_KEY = "_app_tor";
    public static final String APP_DATA_KEY = "_app_data";
    public static final String APP_WIFI_KEY = "_app_wifi";


    public static final String DIRECTORY_TOR_DATA = "tordata";

    //geoip data file asset key
    public static final String GEOIP_ASSET_KEY = "geoip";
    public static final String GEOIP6_ASSET_KEY = "geoip6";

    public static final int TOR_TRANSPROXY_PORT_DEFAULT = 9040;

    public static final int TOR_DNS_PORT_DEFAULT = 5400;

    public static final String HTTP_PROXY_PORT_DEFAULT = "8118"; // like Privoxy!
    public static final String SOCKS_PROXY_PORT_DEFAULT = "9050";

    //control port
    public static final String LOG_NOTICE_HEADER = "NOTICE: ";
    public static final String LOG_NOTICE_BOOTSTRAPPED = "Bootstrapped";

    /**
     * A request to Orbot to transparently start Tor services
     */
    public static final String ACTION_START = "org.torproject.android.intent.action.START";
    public static final String ACTION_STOP = "org.torproject.android.intent.action.STOP";

    // needed when Orbot exits and tor is not running, but the notification is still active
    public static final String ACTION_STOP_FOREGROUND_TASK = "org.torproject.android.intent.action.STOP_FOREGROUND_TASK";

    public static final String ACTION_START_VPN = "org.torproject.android.intent.action.START_VPN";
    public static final String ACTION_STOP_VPN = "org.torproject.android.intent.action.STOP_VPN";
    public static final String ACTION_RESTART_VPN = "org.torproject.android.intent.action.RESTART_VPN";

    public static final String ACTION_LOCAL_LOCALE_SET = "org.torproject.android.intent.LOCAL_LOCALE_SET";
    public static final String EXTRA_LOCALE = "org.torproject.android.intent.extra.LOCALE";


    public static final String ACTION_UPDATE_ONION_NAMES = "org.torproject.android.intent.action.UPDATE_ONION_NAMES";

    /**
     * {@link Intent} send by Orbot with {@code ON/OFF/STARTING/STOPPING} status
     */
    public static final String ACTION_STATUS = "org.torproject.android.intent.action.STATUS";
    /**
     * {@code String} that contains a status constant: {@link #STATUS_ON},
     * {@link #STATUS_OFF}, {@link #STATUS_STARTING}, or
     * {@link #STATUS_STOPPING}
     */
    public static final String EXTRA_STATUS = "org.torproject.android.intent.extra.STATUS";
    /**
     * A {@link String} {@code packageName} for Orbot to direct its status reply
     * to, used in {@link #ACTION_START} {@link Intent}s sent to Orbot
     */
    public static final String EXTRA_PACKAGE_NAME = "org.torproject.android.intent.extra.PACKAGE_NAME";
    /**
     * The SOCKS proxy settings in URL form.
     */
    public static final String EXTRA_SOCKS_PROXY = "org.torproject.android.intent.extra.SOCKS_PROXY";
    public static final String EXTRA_SOCKS_PROXY_HOST = "org.torproject.android.intent.extra.SOCKS_PROXY_HOST";
    public static final String EXTRA_SOCKS_PROXY_PORT = "org.torproject.android.intent.extra.SOCKS_PROXY_PORT";
    /**
     * The HTTP proxy settings in URL form.
     */
    public static final String EXTRA_HTTP_PROXY = "org.torproject.android.intent.extra.HTTP_PROXY";
    public static final String EXTRA_HTTP_PROXY_HOST = "org.torproject.android.intent.extra.HTTP_PROXY_HOST";
    public static final String EXTRA_HTTP_PROXY_PORT = "org.torproject.android.intent.extra.HTTP_PROXY_PORT";

    public static final String EXTRA_DNS_PORT = "org.torproject.android.intent.extra.DNS_PORT";
    public static final String EXTRA_TRANS_PORT = "org.torproject.android.intent.extra.TRANS_PORT";

    public static final String LOCAL_ACTION_LOG = "log";
    public static final String LOCAL_ACTION_STATUS = "status";
    public static final String LOCAL_ACTION_BANDWIDTH = "bandwidth";
    public static final String LOCAL_EXTRA_TOTAL_READ = "totalRead";
    public static final String LOCAL_EXTRA_TOTAL_WRITTEN = "totalWritten";
    public static final String LOCAL_EXTRA_LAST_WRITTEN = "lastWritten";
    public static final String LOCAL_EXTRA_LAST_READ = "lastRead";
    public static final String LOCAL_EXTRA_LOG = "log";
    public static final String LOCAL_EXTRA_BOOTSTRAP_PERCENT = "percent";
    public static final String LOCAL_ACTION_PORTS = "ports";
    public static final String LOCAL_ACTION_V3_NAMES_UPDATED = "V3_NAMES_UPDATED";
    public static final String LOCAL_ACTION_NOTIFICATION_START = "notification_start";
    public static final String LOCAL_ACTION_SMART_CONNECT_EVENT = "smart";
    public static final String LOCAL_EXTRA_SMART_STATUS = "status";
    public static final String SMART_STATUS_NO_DIRECT = "no_direct";
    public static final String SMART_STATUS_CIRCUMVENTION_ATTEMPT_FAILED = "bad_attempt_suggestion";


    /**
     * All tor-related services and daemons are stopped
     */
    public static final String STATUS_OFF = "OFF";

    /**
     * All tor-related services and daemons have completed starting
     */
    public static final String STATUS_ON = "ON";
    public static final String STATUS_STARTING = "STARTING";
    public static final String STATUS_STOPPING = "STOPPING";

    /**
     * The user has disabled the ability for background starts triggered by
     * apps. Fallback to the old {@link Intent} action that brings up Orbot:
     * {@link #ACTION_START}
     */
    public static final String STATUS_STARTS_DISABLED = "STARTS_DISABLED";

    // actions for internal command Intents
    public static final String CMD_SET_EXIT = "setexit";
    public static final String CMD_ACTIVE = "ACTIVE";
    public static final String CMD_SNOWFLAKE_PROXY = "sf_proxy";

    public static final String ONION_SERVICES_DIR = "v3_onion_services";
    public static final String V3_CLIENT_AUTH_DIR = "v3_client_auth";

    public static final String PREFS_DNS_PORT = "PREFS_DNS_PORT";

    public static final String PREFS_KEY_TORIFIED = "PrefTord";

    /**
     * Include packages here to make the VPNService ignore these apps (On Lollipop+). This is to
     * prevent tor over tor scenarios...
     */
    public static final List<String> BYPASS_VPN_PACKAGES = Arrays.asList(
            "org.torproject.torbrowser_alpha",
            "org.torproject.torbrowser",
            "org.onionshare.android", // issue #618
            "org.briarproject.briar.android" // issue #474
    );

    /**
     * Include packages here to explicitly suggest these apps to the user (on Lollipop+).
     * This is done for social media and communications apps that can benefit from Tor.
     */
    public static final List<String> VPN_SUGGESTED_APPS = Arrays.asList(
            "org.thoughtcrime.securesms", // Signal
            "com.whatsapp",
            "com.instagram.android",
            "im.vector.app",
            "org.telegram.messenger",
            "com.twitter.android",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.brave.browser",
            "org.mozilla.focus"
    );

    public static final String ONION_EMOJI = "\uD83E\uDDC5";
}
