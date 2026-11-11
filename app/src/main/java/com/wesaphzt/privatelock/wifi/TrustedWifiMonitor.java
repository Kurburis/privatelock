package com.wesaphzt.privatelock.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Watches WiFi connections and reports whether the phone is on a trusted network.
 *
 * All state is touched only on the main thread: network callbacks and broadcasts
 * are posted there. That is also the thread the accelerometer listener runs on.
 */
public class TrustedWifiMonitor {

    public interface Listener {
        /** @param ssid name of the trusted network, or null when not trusted */
        void onTrustedWifiChanged(boolean trusted, String ssid);
    }

    private static final String TAG = "TrustedWifiMonitor";

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final ConnectivityManager connectivityManager;

    /** Connected WiFi networks and their latest capabilities. Main thread only. */
    private final Map<Network, NetworkCapabilities> wifiNetworks = new HashMap<>();

    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean started = false;
    private boolean trusted = false;
    private String trustedSsid = null;

    //kept as a field: SharedPreferences holds listeners only weakly
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (sharedPrefs, key) -> {
                if (key == null || TrustedWifiSettings.isKey(key)) evaluate();
            };

    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            evaluate();
        }
    };

    public TrustedWifiMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
        this.connectivityManager =
                (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /** Call on the main thread. */
    public void start() {
        if (started) return;
        started = true;

        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        ContextCompat.registerReceiver(context, locationReceiver,
                new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        if (connectivityManager != null) {
            networkCallback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? new WifiCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
                    : new WifiCallback();
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    //a home network without internet access still counts
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            try {
                connectivityManager.registerNetworkCallback(request, networkCallback);
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not watch WiFi; trusted networks will be ignored", e);
                networkCallback = null;
            }
        }
        evaluate();
    }

    /** Call on the main thread. Safe to call more than once. */
    public void stop() {
        if (!started) return;
        started = false;

        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        try {
            context.unregisterReceiver(locationReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Location receiver was not registered", e);
        }
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Network callback was not registered", e);
            }
        }
        networkCallback = null;
        mainHandler.removeCallbacksAndMessages(null);
        wifiNetworks.clear();
        setTrusted(false, null);
    }

    /** Re-checks the current network against the trusted list. Main thread only. */
    public void evaluate() {
        if (!started) return;

        boolean canRead = TrustedNetworks.canReadWifiReliably(
                TrustedWifiSettings.isEnabled(prefs),
                WifiReader.hasForegroundLocation(context),
                WifiReader.hasBackgroundLocation(context),
                WifiReader.isLocationEnabled(context));
        if (!canRead || wifiNetworks.isEmpty()) {
            setTrusted(false, null);
            return;
        }

        Set<TrustedNetwork> trustedNetworks = TrustedWifiSettings.load(prefs);
        boolean nameOnly = TrustedWifiSettings.isNameOnly(prefs);

        for (NetworkCapabilities caps : wifiNetworks.values()) {
            WifiReader.CurrentWifi current = readWifi(caps);
            if (TrustedNetworks.matches(trustedNetworks, current.ssid, current.bssid, nameOnly)) {
                setTrusted(true, current.ssid);
                return;
            }
        }
        setTrusted(false, null);
    }

    private WifiReader.CurrentWifi readWifi(NetworkCapabilities caps) {
        if (caps != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WifiReader.CurrentWifi fromCaps = readFromCapabilities(caps);
            if (fromCaps.ssid != null) return fromCaps;
        }
        //older Android, or capabilities that arrived before permission was granted
        return WifiReader.fromWifiManager(context);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private static WifiReader.CurrentWifi readFromCapabilities(NetworkCapabilities caps) {
        TransportInfo info = caps.getTransportInfo();
        return WifiReader.fromWifiInfo(info instanceof WifiInfo ? (WifiInfo) info : null);
    }

    private void setTrusted(boolean nowTrusted, String ssid) {
        boolean changed = nowTrusted != trusted
                || (ssid == null ? trustedSsid != null : !ssid.equals(trustedSsid));
        trusted = nowTrusted;
        trustedSsid = ssid;
        if (changed) listener.onTrustedWifiChanged(nowTrusted, ssid);
    }

    private class WifiCallback extends ConnectivityManager.NetworkCallback {

        WifiCallback() {
            super();
        }

        @RequiresApi(Build.VERSION_CODES.S)
        WifiCallback(int flags) {
            super(flags);
        }

        //Android 6-7 may not follow up with onCapabilitiesChanged
        @Override
        public void onAvailable(@NonNull Network network) {
            mainHandler.post(() -> {
                if (!started || wifiNetworks.containsKey(network)) return;
                wifiNetworks.put(network, connectivityManager.getNetworkCapabilities(network));
                evaluate();
            });
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
            mainHandler.post(() -> {
                if (!started) return;
                wifiNetworks.put(network, caps);
                evaluate();
            });
        }

        @Override
        public void onLost(@NonNull Network network) {
            mainHandler.post(() -> {
                if (!started) return;
                wifiNetworks.remove(network);
                evaluate();
            });
        }
    }
}
