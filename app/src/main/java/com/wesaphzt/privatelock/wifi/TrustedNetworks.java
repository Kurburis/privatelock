package com.wesaphzt.privatelock.wifi;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure matching and storage-format logic for trusted WiFi networks.
 *
 * Every unreadable value (redacted by Android, missing, malformed) becomes null,
 * and null never matches. So when in doubt, the lock stays armed.
 */
public final class TrustedNetworks {

    /** What Android returns for the SSID when the app may not read it. */
    static final String REDACTED_SSID = "<unknown ssid>";
    /** What Android returns for the BSSID when the app may not read it. */
    static final String REDACTED_BSSID = "02:00:00:00:00:00";

    private static final Pattern BSSID_PATTERN =
            Pattern.compile("^([0-9a-f]{2}:){5}[0-9a-f]{2}$");
    private static final char SEPARATOR = '|';

    private TrustedNetworks() {  }

    /** Removes the quotes Android adds around UTF-8 names. Returns null if unreadable. */
    public static String normalizeSsid(String raw) {
        if (raw == null) return null;
        String ssid = raw;
        if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        if (ssid.isEmpty() || REDACTED_SSID.equals(ssid)) return null;
        return ssid;
    }

    /** Lowercases a MAC address. Returns null if redacted or malformed. */
    public static String normalizeBssid(String raw) {
        if (raw == null) return null;
        String bssid = raw.trim().toLowerCase(Locale.ROOT);
        if (REDACTED_BSSID.equals(bssid) || !BSSID_PATTERN.matcher(bssid).matches()) {
            return null;
        }
        return bssid;
    }

    /** Storage format: "bssid|ssid". The BSSID cannot contain '|', so the SSID may. */
    public static String encode(TrustedNetwork network) {
        return network.bssid + SEPARATOR + network.ssid;
    }

    /** Returns null for any entry that is not a valid "bssid|ssid" pair. */
    public static TrustedNetwork decode(String entry) {
        if (entry == null) return null;
        int split = entry.indexOf(SEPARATOR);
        if (split < 0) return null;
        String bssid = normalizeBssid(entry.substring(0, split));
        String ssid = entry.substring(split + 1);
        if (bssid == null || ssid.isEmpty()) return null;
        return new TrustedNetwork(ssid, bssid);
    }

    public static Set<TrustedNetwork> decodeAll(Collection<String> entries) {
        Set<TrustedNetwork> result = new HashSet<>();
        if (entries == null) return result;
        for (String entry : entries) {
            TrustedNetwork network = decode(entry);
            if (network != null) result.add(network);
        }
        return result;
    }

    public static Set<String> encodeAll(Collection<TrustedNetwork> networks) {
        Set<String> result = new HashSet<>();
        for (TrustedNetwork network : networks) {
            result.add(encode(network));
        }
        return result;
    }

    /**
     * Cached WiFi details may outlive the right to read them (for example, Location
     * switched off after the network connected). Trust nothing unless all hold now.
     */
    public static boolean canReadWifiReliably(boolean featureEnabled, boolean foregroundLocation,
                                              boolean backgroundLocation, boolean locationOn) {
        return featureEnabled && foregroundLocation && backgroundLocation && locationOn;
    }

    /**
     * @param ssid      normalized current SSID, or null if unreadable
     * @param bssid     normalized current BSSID, or null if unreadable
     * @param nameOnly  true to ignore the BSSID (less secure, for mesh networks)
     */
    public static boolean matches(Collection<TrustedNetwork> trusted, String ssid,
                                  String bssid, boolean nameOnly) {
        if (ssid == null || trusted == null) return false;
        if (!nameOnly && bssid == null) return false;
        for (TrustedNetwork network : trusted) {
            if (!network.ssid.equals(ssid)) continue;
            if (nameOnly || network.bssid.equals(bssid)) return true;
        }
        return false;
    }
}
