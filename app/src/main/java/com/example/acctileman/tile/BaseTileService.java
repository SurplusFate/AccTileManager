package com.example.acctileman.tile;

import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.example.acctileman.Logger;
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

    private static final String PREFS = "acc_tile_prefs";
    private static final String KEY_ENABLED = "_enabled";

    private Handler uiHandler = new Handler(Looper.getMainLooper());

    public abstract int getSlotIndex();

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Logger.d("Tile", "onTileAdded: slot=" + getSlotIndex());
        updateTileUI();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Logger.d("Tile", "onStartListening: slot=" + getSlotIndex());
        updateTileUI();
    }

    @Override
    public void onClick() {
        super.onClick();
        Logger.d("Tile", "onClick: slot=" + getSlotIndex());
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
            Logger.w("Tile", "Slot " + getSlotIndex() + " 未配置，打开设置");
            startActivityAndCollapse(getPackageManager().getLaunchIntentForPackage(getPackageName()));
            return;
        }

        Logger.d("Tile", "ENABLE slot=" + getSlotIndex() + " service=" + config.accessibilityService);
        ShellHelper.enableService(config.accessibilityService);

        if (config.launchApp && config.appPackage != null && !config.appPackage.isEmpty()) {
            ShellHelper.launchApp(config.appPackage);
        }

        setSlotState(true);
    }

    private void disable() {
        SlotConfig config = getSlotConfig();
        if (config == null || config.isEmpty()) return;

        Logger.d("Tile", "DISABLE slot=" + getSlotIndex() + " service=" + config.accessibilityService);
        ShellHelper.disableService(config.accessibilityService);

        if (config.stopApp && config.appPackage != null && !config.appPackage.isEmpty()) {
            ShellHelper.forceStopApp(config.appPackage);
        }

        setSlotState(false);
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

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private boolean getSlotState() {
        return getPrefs().getBoolean(getSlotIndex() + KEY_ENABLED, false);
    }

    private void setSlotState(boolean enabled) {
        getPrefs().edit().putBoolean(getSlotIndex() + KEY_ENABLED, enabled).apply();
    }

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

    private void ensureShizukuPermission() {
        if (ShizukuHelper.checkSelfPermission()) {
            Logger.d("Tile", "Shizuku 权限已授予");
            return;
        }
        Logger.d("Tile", "Shizuku 权限未授予，请求中...");
        ShizukuHelper.requestPermission(0);
    }

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