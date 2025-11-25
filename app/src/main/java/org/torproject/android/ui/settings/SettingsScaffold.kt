package org.torproject.android.ui.settings

val settingsCategories = listOf(
    // General
    SettingsCategory(
        title = "General",
        settings = listOf(
            Setting.ListSetting(
                k = "pref_default_locale",
                t = "Set Locale",
                options = listOf("System Default", "English", "Spanish"),
                optionValues = listOf("system", "en", "es")
            ),
            Setting.CheckBoxSetting(
                k = "pref_start_boot",
                t = "Start on Boot",
                s = "Enable the app to start automatically when the device boots",
                value = true
            ),
            Setting.CheckBoxSetting(
                k = "pref_allow_background_starts",
                t = "Allow Background Starts",
                s = "Allow the app to start background services",
                value = true
            ),
            Setting.CheckBoxSetting(
                k = "pref_open_proxy_on_all_interfaces",
                t = "Open Proxy on All Interfaces",
                s = "Enable the proxy on all network interfaces",
                value = false
            ),
            Setting.CheckBoxSetting(
                k = "pref_power_user",
                t = "Power User Mode",
                s = "Enable advanced settings",
                value = false
            ),
            Setting.CheckBoxSetting(
                k = "pref_flag_secure",
                t = "Secure Flag",
                s = "Enable secure flag for the app",
                value = true
            ),
            Setting.CheckBoxSetting(
                k = "pref_detect_root",
                t = "Detect Root",
                s = "Enable root detection for security",
                value = true
            ),
            Setting.TextSetting(
                k = "pref_key_camo_dialog",
                t = "Camo Mode",
                s = "Configure camouflage mode"
            ),
            Setting.CheckBoxSetting(
                k = "pref_require_password",
                t = "Require Password",
                s = "Require password to unlock certain features",
                value = false
            ),
            Setting.CheckBoxSetting(
                k = "pref_auth_no_biometrics",
                t = "Password Only (No Biometrics)",
                s = "Disable biometric authentication",
                value = false
            )
        )
    ),

    // Volunteer/Kindness
    SettingsCategory(
        title = "Volunteer Mode",
        settings = listOf(
            Setting.CheckBoxSetting(
                k = "pref_show_snowflake_proxy_msg",
                t = "Show Snowflake Proxy Message",
                s = "Show a message about Snowflake proxy usage",
                value = false
            )
        )
    ),

    // Node configuration
    SettingsCategory(
        title = "Node Configuration",
        settings = listOf(
            Setting.TextSetting(
                k = "pref_entrance_nodes",
                t = "Entrance Nodes",
                s = "Enter your preferred entrance nodes"
            ),
            Setting.TextSetting(
                k = "pref_exit_nodes",
                t = "Exit Nodes",
                s = "Enter exit nodes (fingerprints, nicks, countries)"
            ),
            Setting.TextSetting(
                k = "pref_exclude_nodes",
                t = "Exclude Nodes",
                s = "Exclude certain nodes"
            ),
            Setting.CheckBoxSetting(
                k = "pref_strict_nodes",
                t = "Strict Nodes",
                s = "Enforce strict node usage",
                value = false
            )
        )
    ),

    // Reachable addresses
    SettingsCategory(
        title = "Reachable Addresses",
        settings = listOf(
            Setting.CheckBoxSetting(
                k = "pref_reachable_addresses",
                t = "Reachable Addresses",
                s = "Run as a client behind a restrictive firewall",
                value = false
            ),
            Setting.TextSetting(
                k = "pref_reachable_addresses_ports",
                t = "Reachable Ports",
                s = "Specify ports reachable behind a restrictive firewall",
                value = "*:80,*:443"
            )
        )
    ),

    // Connectivity
    SettingsCategory(
        title = "Connectivity",
        settings = listOf(
            Setting.CheckBoxSetting(
                k = "pref_isolate_dest",
                t = "Isolate Destination",
                s = "Isolate traffic by destination",
                value = false
            ),
            Setting.CheckBoxSetting(
                k = "pref_isolate_port",
                t = "Isolate Port",
                s = "Isolate traffic by port",
                value = false
            ),
            Setting.CheckBoxSetting(
                k = "pref_isolate_protocol",
                t = "Isolate Protocol",
                s = "Isolate traffic by protocol",
                value = false
            ),
            Setting.CheckBoxSetting(
                k = "pref_isolate_keep_alive",
                t = "Isolate Keep-Alive",
                s = "Keep alive isolation",
                value = false
            ),
            Setting.CheckBoxSetting(
                k = "pref_prefer_ipv6",
                t = "Prefer IPv6",
                s = "Enable IPv6 preference",
                value = true
            ),
            Setting.CheckBoxSetting(
                k = "pref_disable_ipv4",
                t = "Disable IPv4",
                s = "Disable IPv4 traffic",
                value = false
            )
        )
    ),

    // Padding settings
    SettingsCategory(
        title = "Padding",
        settings = listOf(
            Setting.CheckBoxSetting(
                k = "pref_connection_padding",
                t = "Connection Padding",
                s = "Enable padding between connections",
                value = false
            ),
            Setting.CheckBoxSetting(
                k = "pref_reduced_connection_padding",
                t = "Reduced Connection Padding",
                s = "Enable reduced padding",
                value = true
            ),
            Setting.CheckBoxSetting(
                k = "pref_circuit_padding",
                t = "Circuit Padding",
                s = "Enable padding between circuits",
                value = true
            ),
            Setting.CheckBoxSetting(
                k = "pref_reduced_circuit_padding",
                t = "Reduced Circuit Padding",
                s = "Enable reduced padding for circuits",
                value = true
            )
        )
    ),

    // Proxy
    SettingsCategory(
        title = "Proxy",
        settings = listOf(
            Setting.TextSetting(
                k = "pref_proxy_note",
                t = "Note",
                s = "Snowflake does not support proxies"
            ),
            Setting.ListSetting(
                k = "pref_proxy_type",
                t = "Proxy Type",
                options = listOf("SOCKS5", "HTTP", "HTTPS"),
                optionValues = listOf("SOCKS5", "HTTP", "HTTPS"),
                selected = "SOCKS5"
            ),
            Setting.TextSetting(
                k = "pref_proxy_host",
                t = "Proxy Host"
            ),
            Setting.TextSetting(
                k = "pref_proxy_port",
                t = "Proxy Port",
                value = "0"
            ),
            Setting.TextSetting(
                k = "pref_proxy_username",
                t = "Proxy Username"
            ),
            Setting.TextSetting(
                k = "pref_proxy_password",
                t = "Proxy Password",
                value = ""
            )
        )
    ),

    // Debug
    SettingsCategory(
        title = "Debug",
        settings = listOf(
            Setting.TextSetting(
                k = "pref_socks",
                t = "SOCKS Port",
                value = "9050"
            ),
            Setting.TextSetting(
                k = "pref_http",
                t = "HTTP Port",
                value = "8118"
            ),
            Setting.TextSetting(
                k = "pref_transport",
                t = "Transport",
                value = "auto"
            ),
            Setting.TextSetting(
                k = "pref_dnsport",
                t = "DNS Port",
                value = "auto"
            ),
            Setting.TextSetting(
                k = "pref_custom_torrc",
                t = "Custom Torrc"
            ),
            Setting.CheckBoxSetting(
                k = "pref_enable_logging",
                t = "Debug Log",
                s = "Enable debug logging",
                value = false
            ),
            Setting.CheckBoxSetting(
                k = "pref_disable_network",
                t = "Disable Network",
                s = "Disable network connections",
                value = true
            )
        )
    )
)
