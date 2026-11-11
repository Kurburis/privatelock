package com.wesaphzt.privatelock.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.wesaphzt.privatelock.R;
import com.wesaphzt.privatelock.wifi.TrustedNetwork;
import com.wesaphzt.privatelock.wifi.TrustedNetworks;
import com.wesaphzt.privatelock.wifi.TrustedWifiSettings;
import com.wesaphzt.privatelock.wifi.WifiReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FragmentSettings extends PreferenceFragmentCompat {

    private Context context;

    private CheckBoxPreference cbRunConstant;
    private Preference prefTrustedWifiAdd;
    private Preference prefTrustedWifiManage;

    //ask for "allow all the time" at most once per visit, so a refusal cannot loop
    private boolean backgroundLocationAsked = false;

    private final ActivityResultLauncher<String[]> foregroundLocationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (WifiReader.hasForegroundLocation(requireContext())) {
                    addCurrentWifi();
                } else {
                    toast(getString(R.string.trusted_wifi_location_denied));
                }
            });

    private final ActivityResultLauncher<String> backgroundLocationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    toast(getString(R.string.trusted_wifi_background_denied));
                }
                addCurrentWifi();
            });

    private SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences);

        context = getContext();

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        //this static call will reset default values only on the first read
        PreferenceManager.setDefaultValues(context, R.xml.preferences, false);

        cbRunConstant = (CheckBoxPreference) findPreference("RUN_CONSTANT");

        prefTrustedWifiAdd = findPreference("TRUSTED_WIFI_ADD");
        prefTrustedWifiManage = findPreference("TRUSTED_WIFI_MANAGE");
        prefTrustedWifiAdd.setOnPreferenceClickListener(preference -> {
            addCurrentWifi();
            return true;
        });
        prefTrustedWifiManage.setOnPreferenceClickListener(preference -> {
            showRemoveTrustedWifiDialog();
            return true;
        });
    }

    //permission steps in order; each step returns here once it is done
    private void addCurrentWifi() {
        Context ctx = requireContext();
        if (!WifiReader.hasForegroundLocation(ctx)) {
            foregroundLocationLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION });
            return;
        }
        if (!WifiReader.hasBackgroundLocation(ctx) && !backgroundLocationAsked) {
            showBackgroundLocationDialog();
            return;
        }
        if (!WifiReader.isLocationEnabled(ctx)) {
            toast(getString(R.string.trusted_wifi_location_off));
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }
        saveCurrentWifi(ctx);
    }

    private void showBackgroundLocationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.trusted_wifi_background_title)
                .setMessage(R.string.trusted_wifi_background_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    backgroundLocationAsked = true;
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    backgroundLocationAsked = true;
                    toast(getString(R.string.trusted_wifi_background_denied));
                    addCurrentWifi();
                })
                .show();
    }

    private void saveCurrentWifi(Context ctx) {
        WifiReader.CurrentWifi current = WifiReader.fromWifiManager(ctx);
        if (current.ssid == null) {
            toast(getString(R.string.trusted_wifi_not_connected));
            return;
        }
        if (current.bssid == null) {
            toast(getString(R.string.trusted_wifi_no_bssid));
            return;
        }

        Set<TrustedNetwork> networks = new HashSet<>(TrustedWifiSettings.load(sharedPreferences));
        if (!networks.add(new TrustedNetwork(current.ssid, current.bssid))) {
            toast(getString(R.string.trusted_wifi_already_added));
            return;
        }
        TrustedWifiSettings.save(sharedPreferences, networks);
        toast(getString(R.string.trusted_wifi_added, current.ssid));
        updateTrustedWifiSummaries();
    }

    private void showRemoveTrustedWifiDialog() {
        final List<TrustedNetwork> networks = new ArrayList<>(TrustedWifiSettings.load(sharedPreferences));
        if (networks.isEmpty()) {
            toast(getString(R.string.trusted_wifi_none));
            return;
        }
        //Comparator.comparing needs API 24; minSdk is 23
        Collections.sort(networks, (a, b) -> a.toString().compareTo(b.toString()));

        CharSequence[] labels = new CharSequence[networks.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = networks.get(i).toString();
        }
        final boolean[] checked = new boolean[labels.length];

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.trusted_wifi_manage_title)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.trusted_wifi_remove, (dialog, which) -> {
                    List<TrustedNetwork> kept = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (!checked[i]) kept.add(networks.get(i));
                    }
                    TrustedWifiSettings.save(sharedPreferences, kept);
                    toast(getString(R.string.trusted_wifi_removed));
                    updateTrustedWifiSummaries();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateTrustedWifiSummaries() {
        Set<TrustedNetwork> networks = TrustedWifiSettings.load(sharedPreferences);
        prefTrustedWifiManage.setSummary(networks.isEmpty()
                ? getString(R.string.trusted_wifi_none)
                : getString(R.string.trusted_wifi_count, networks.size()));

        WifiReader.CurrentWifi current = WifiReader.fromWifiManager(requireContext());
        if (current.ssid == null) {
            prefTrustedWifiAdd.setSummary(R.string.trusted_wifi_status_unknown);
            return;
        }
        boolean trusted = TrustedNetworks.matches(networks, current.ssid, current.bssid,
                TrustedWifiSettings.isNameOnly(sharedPreferences));
        prefTrustedWifiAdd.setSummary(getString(trusted
                ? R.string.trusted_wifi_status_connected_trusted
                : R.string.trusted_wifi_status_connected, current.ssid));
    }

    private void toast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        updateTrustedWifiSummaries();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //set title
        getActivity().setTitle("Settings");

        //bg color
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white));

        //hide action bar menu while this fragment is shown
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {  }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                return false;
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                menu.setGroupVisible(R.id.menu_main, false);
            }
        }, getViewLifecycleOwner());

        sharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if(TrustedWifiSettings.KEY_NAME_ONLY.equals(key)) {
                    updateTrustedWifiSummaries();
                }
                if("RUN_CONSTANT".equals(key) && cbRunConstant.isChecked()) {
                    Toast.makeText(context, getString(R.string.settings_restart_service_toast), Toast.LENGTH_LONG).show();
                }
            }
        };
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    }
}