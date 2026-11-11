package com.wesaphzt.privatelock.wifi;

import android.content.SharedPreferences;

import java.util.Collection;
import java.util.Set;

/** Reads and writes the trusted-WiFi preferences. */
public final class TrustedWifiSettings {

    public static final String KEY_ENABLED = "TRUSTED_WIFI_ENABLED";
    public static final String KEY_NAME_ONLY = "TRUSTED_WIFI_NAME_ONLY";
    public static final String KEY_NETWORKS = "TRUSTED_WIFI_NETWORKS";

    private TrustedWifiSettings() {  }

    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public static boolean isNameOnly(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_NAME_ONLY, false);
    }

    public static Set<TrustedNetwork> load(SharedPreferences prefs) {
        return TrustedNetworks.decodeAll(prefs.getStringSet(KEY_NETWORKS, null));
    }

    public static void save(SharedPreferences prefs, Collection<TrustedNetwork> networks) {
        prefs.edit().putStringSet(KEY_NETWORKS, TrustedNetworks.encodeAll(networks)).apply();
    }

    public static boolean isKey(String key) {
        return KEY_ENABLED.equals(key) || KEY_NAME_ONLY.equals(key) || KEY_NETWORKS.equals(key);
    }
}
