package com.wesaphzt.privatelock.wifi;

import java.util.Objects;

/** One trusted WiFi access point: the network name plus the router's hardware address. */
public final class TrustedNetwork {

    public final String ssid;
    public final String bssid;

    public TrustedNetwork(String ssid, String bssid) {
        this.ssid = ssid;
        this.bssid = bssid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrustedNetwork)) return false;
        TrustedNetwork that = (TrustedNetwork) o;
        return ssid.equals(that.ssid) && bssid.equals(that.bssid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ssid, bssid);
    }

    @Override
    public String toString() {
        return ssid + " (" + bssid + ")";
    }
}
