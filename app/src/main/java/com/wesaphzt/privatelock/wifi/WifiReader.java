package com.wesaphzt.privatelock.wifi;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

/** Reads the current WiFi name and router address, and checks what Android allows. */
public final class WifiReader {

    /** Normalized SSID and BSSID. Either field is null if Android did not reveal it. */
    public static final class CurrentWifi {
        public final String ssid;
        public final String bssid;

        CurrentWifi(String ssid, String bssid) {
            this.ssid = ssid;
            this.bssid = bssid;
        }
    }

    private static final CurrentWifi NONE = new CurrentWifi(null, null);

    private WifiReader() {  }

    public static CurrentWifi fromWifiInfo(WifiInfo info) {
        if (info == null) return NONE;
        return new CurrentWifi(
                TrustedNetworks.normalizeSsid(info.getSSID()),
                TrustedNetworks.normalizeBssid(info.getBSSID()));
    }

    /**
     * Reads the connected WiFi through WifiManager. Deprecated on API 31+ but still
     * returns full details to a foreground app that holds location permission.
     */
    @SuppressWarnings("deprecation")
    public static CurrentWifi fromWifiManager(Context context) {
        WifiManager wifiManager =
                (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return NONE;
        try {
            return fromWifiInfo(wifiManager.getConnectionInfo());
        } catch (SecurityException e) {
            return NONE;
        }
    }

    public static boolean hasForegroundLocation(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Needed on Android 10+ to read the WiFi name while the app is in the background. */
    public static boolean hasBackgroundLocation(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Android hides the WiFi name while the system Location switch is off. */
    public static boolean isLocationEnabled(Context context) {
        LocationManager locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && LocationManagerCompat.isLocationEnabled(locationManager);
    }
}
