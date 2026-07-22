package com.example.acctileman.tile;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import com.example.acctileman.R;
import com.example.acctileman.ShellHelper;
import com.example.acctileman.ShizukuHelper;

/**
 * Base TileService for accessibility toggle slots.
 * Each slot is configured with a target app package and accessibility service component.
 * Toggle ON  -> enable accessibility service + launch app
 * Toggle OFF -> disable accessibility service + force stop app
 */
public abstract class BaseTileService extends TileService {

    private static final String TAG = "AccTile";
    private static final String PREFS = "acc_tile_prefs";
    private static final String KEY_ENABLED = "_enabled";

    private Handler uiHandler = new Handler(Looper.getMainLooper());

    /**
     * Subclasses must return their slot index (0, 1, 2, ...).
     */
    public abstract int getSlotIndex();

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Log.d(TAG, "Tile added for slot " + getSlotIndex());
        updateTileUI();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileUI();
    }

    @Override
    public void onClick() {
        super.onClick();
        ensureShizukuPermission();
        boolean currentlyEnabled = getSlotState();
        if (currentlyEnabled) {
            disable();
        } else {
            enable();
        }
        updateTileUI();
    }

    private void enable() {
        SlotConfig config = getSlotConfig();
        if (config == null || config.isEmpty()) {
            Log.w(TAG, "Slot " + getSlotIndex() + " not configured, launching settings");
            // Open settings if not configured
            startActivityAndCollapse(getPackageManager().getLaunchIntentForPackage(getPackageName()));
            return;
        }

        // Enable accessibility service
        ShellHelper.enableService(config.accessibilityService);

        // Launch target app
        if (config.launchApp && config.appPackage != null && !config.appPackage.isEmpty()) {
            ShellHelper.launchApp(config.appPackage);
        }

        setSlotState(true);
        Log.d(TAG, "Slot " + getSlotIndex() + " ENABLED: " + config.accessibilityService);
    }

    private void disable() {
        SlotConfig config = getSlotConfig();
        if (config == null || config.isEmpty()) return;

        // Disable accessibility service
        ShellHelper.disableService(config.accessibilityService);

        // Force stop target app
        if (config.stopApp && config.appPackage != null && !config.appPackage.isEmpty()) {
            ShellHelper.forceStopApp(config.appPackage);
        }

        setSlotState(false);
        Log.d(TAG, "Slot " + getSlotIndex() + " DISABLED");
    }

    private void updateTileUI() {
        Tile tile = getQsTile();
        if (tile == null) return;

        SlotConfig config = getSlotConfig();
        boolean active = getSlotState();

        if (config == null || config.isEmpty()) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("未配置");
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile_empty));
        } else {
            tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(config.label);
            tile.setIcon(Icon.createWithResource(this,
                    active ? R.drawable.ic_tile_on : R.drawable.ic_tile_off));
        }
        tile.updateTile();
    }

    // ---- SharedPreferences helpers ----

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private boolean getSlotState() {
        return getPrefs().getBoolean(getSlotIndex() + KEY_ENABLED, false);
    }

    private void setSlotState(boolean enabled) {
        getPrefs().edit().putBoolean(getSlotIndex() + KEY_ENABLED, enabled).apply();
    }

    /**
     * Read configuration for this slot.
     * Format in prefs: "slot_{index}_app", "slot_{index}_service", "slot_{index}_label",
     * "slot_{index}_launch", "slot_{index}_stop"
     */
    SlotConfig getSlotConfig() {
        SharedPreferences prefs = getSharedPreferences("acc_tile_prefs", MODE_PRIVATE);
        String prefix = "slot_" + getSlotIndex() + "_";
        String app = prefs.getString(prefix + "app", "");
        String service = prefs.getString(prefix + "service", "");
        String label = prefs.getString(prefix + "label", "");
        boolean launch = prefs.getBoolean(prefix + "launch", true);
        boolean stop = prefs.getBoolean(prefix + "stop", true);

        if (service.isEmpty()) return null;
        return new SlotConfig(app, service, label, launch, stop);
    }

    // ---- Shizuku permission ----

    private void ensureShizukuPermission() {
        if (ShizukuHelper.checkSelfPermission()) return;
        ShizukuHelper.requestPermission(0);
    }

    // ---- Slot config data class ----

    static class SlotConfig {
        String appPackage;
        String accessibilityService;
        String label;
        boolean launchApp;
        boolean stopApp;

        SlotConfig(String app, String service, String label, boolean launch, boolean stop) {
            this.appPackage = app;
            this.accessibilityService = service;
            this.label = label;
            this.launchApp = launch;
            this.stopApp = stop;
        }

        boolean isEmpty() {
            return accessibilityService == null || accessibilityService.isEmpty();
        }
    }
}
