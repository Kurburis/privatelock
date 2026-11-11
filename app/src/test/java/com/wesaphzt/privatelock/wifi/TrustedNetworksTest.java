package com.wesaphzt.privatelock.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TrustedNetworksTest {

    private static final String HOME_SSID = "HomeNet";
    private static final String HOME_BSSID = "aa:bb:cc:dd:ee:ff";

    private static Set<TrustedNetwork> homeOnly() {
        return Collections.singleton(new TrustedNetwork(HOME_SSID, HOME_BSSID));
    }

    // --- normalizeSsid ---

    @Test
    public void normalizeSsid_stripsSurroundingQuotes() {
        assertEquals("HomeNet", TrustedNetworks.normalizeSsid("\"HomeNet\""));
    }

    @Test
    public void normalizeSsid_keepsUnquotedHexSsid() {
        assertEquals("48656c6c6f", TrustedNetworks.normalizeSsid("48656c6c6f"));
    }

    @Test
    public void normalizeSsid_returnsNullForRedactedSsid() {
        assertNull(TrustedNetworks.normalizeSsid("<unknown ssid>"));
        assertNull(TrustedNetworks.normalizeSsid("\"<unknown ssid>\""));
    }

    @Test
    public void normalizeSsid_returnsNullForNullOrEmpty() {
        assertNull(TrustedNetworks.normalizeSsid(null));
        assertNull(TrustedNetworks.normalizeSsid(""));
        assertNull(TrustedNetworks.normalizeSsid("\"\""));
    }

    // --- normalizeBssid ---

    @Test
    public void normalizeBssid_lowercasesValidAddress() {
        assertEquals(HOME_BSSID, TrustedNetworks.normalizeBssid("AA:BB:CC:DD:EE:FF"));
    }

    @Test
    public void normalizeBssid_returnsNullForRedactedAddress() {
        assertNull(TrustedNetworks.normalizeBssid("02:00:00:00:00:00"));
    }

    @Test
    public void normalizeBssid_returnsNullForMalformedOrMissing() {
        assertNull(TrustedNetworks.normalizeBssid(null));
        assertNull(TrustedNetworks.normalizeBssid(""));
        assertNull(TrustedNetworks.normalizeBssid("aa:bb:cc"));
        assertNull(TrustedNetworks.normalizeBssid("zz:bb:cc:dd:ee:ff"));
    }

    // --- encode / decode ---

    @Test
    public void encodeDecode_roundTripsSsidContainingSeparator() {
        TrustedNetwork original = new TrustedNetwork("Cafe|Guest", HOME_BSSID);

        TrustedNetwork decoded = TrustedNetworks.decode(TrustedNetworks.encode(original));

        assertEquals(original, decoded);
    }

    @Test
    public void decode_returnsNullForMalformedEntries() {
        assertNull(TrustedNetworks.decode(null));
        assertNull(TrustedNetworks.decode("no-separator"));
        assertNull(TrustedNetworks.decode("not-a-mac|HomeNet"));
        assertNull(TrustedNetworks.decode(HOME_BSSID + "|"));
    }

    @Test
    public void decodeAll_skipsMalformedEntries() {
        Set<String> raw = new HashSet<>();
        raw.add(TrustedNetworks.encode(new TrustedNetwork(HOME_SSID, HOME_BSSID)));
        raw.add("garbage");

        Set<TrustedNetwork> decoded = TrustedNetworks.decodeAll(raw);

        assertEquals(homeOnly(), decoded);
    }

    @Test
    public void decodeAll_returnsEmptySetForNull() {
        assertTrue(TrustedNetworks.decodeAll(null).isEmpty());
    }

    // --- matches: strict (name + BSSID) ---

    @Test
    public void strict_matchesWhenNameAndBssidAgree() {
        assertTrue(TrustedNetworks.matches(homeOnly(), HOME_SSID, HOME_BSSID, false));
    }

    @Test
    public void strict_rejectsSameNameFromDifferentRouter() {
        assertFalse(TrustedNetworks.matches(homeOnly(), HOME_SSID, "11:22:33:44:55:66", false));
    }

    @Test
    public void strict_rejectsWhenBssidUnreadable() {
        assertFalse(TrustedNetworks.matches(homeOnly(), HOME_SSID, null, false));
    }

    @Test
    public void strict_rejectsDifferentNameOnSameRouter() {
        assertFalse(TrustedNetworks.matches(homeOnly(), "Other", HOME_BSSID, false));
    }

    // --- matches: name only ---

    @Test
    public void nameOnly_matchesSameNameFromAnyRouter() {
        assertTrue(TrustedNetworks.matches(homeOnly(), HOME_SSID, "11:22:33:44:55:66", true));
        assertTrue(TrustedNetworks.matches(homeOnly(), HOME_SSID, null, true));
    }

    @Test
    public void nameOnly_rejectsDifferentName() {
        assertFalse(TrustedNetworks.matches(homeOnly(), "Other", HOME_BSSID, true));
    }

    // --- matches: fail safe ---

    @Test
    public void anyMode_rejectsWhenSsidUnreadable() {
        assertFalse(TrustedNetworks.matches(homeOnly(), null, HOME_BSSID, false));
        assertFalse(TrustedNetworks.matches(homeOnly(), null, HOME_BSSID, true));
    }

    // --- canReadWifiReliably: gate before any cached WiFi info is trusted ---

    @Test
    public void canRead_onlyWhenEnabledWithAllPermissionsAndLocationOn() {
        assertTrue(TrustedNetworks.canReadWifiReliably(true, true, true, true));
    }

    @Test
    public void canRead_falseWhenAnyConditionFails() {
        assertFalse(TrustedNetworks.canReadWifiReliably(false, true, true, true));
        assertFalse(TrustedNetworks.canReadWifiReliably(true, false, true, true));
        assertFalse(TrustedNetworks.canReadWifiReliably(true, true, false, true));
        assertFalse(TrustedNetworks.canReadWifiReliably(true, true, true, false));
    }

    @Test
    public void anyMode_rejectsWhenNothingTrusted() {
        Set<TrustedNetwork> none = Collections.emptySet();
        assertFalse(TrustedNetworks.matches(none, HOME_SSID, HOME_BSSID, false));
        assertFalse(TrustedNetworks.matches(none, HOME_SSID, HOME_BSSID, true));
    }
}
