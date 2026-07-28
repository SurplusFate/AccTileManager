package com.example.acctileman.tile;

import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.example.acctileman.App;
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

        if (!ShizukuHelper.checkSelfPermission()) {
            Logger.w("Tile", "WRITE_SECURE_SETTINGS 权限未授予，忽略操作");
            // 通过 statusBar 提示用户去设置页授权
            try {
                android.widget.Toast.makeText(
                        App.getContext(),
                        "无权限！请先打开 App 点击「授予权限」",
                        android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
            return;
        }

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
            try {
                android.widget.Toast.makeText(
                        App.getContext(),
                        "磁贴未配置，打开 App 进行设置",
                        android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
            startActivityAndCollapse(getPackageManager().getLaunchIntentForPackage(getPackageName()));
            return;
        }

        Logger.d("Tile", "ENABLE slot=" + getSlotIndex() + " service=" + config.accessibilityService);
        boolean ok = ShellHelper.enableService(config.accessibilityService);
        Logger.d("Tile", "enableService 结果: " + ok);

        if (!ok) {
            Logger.e("Tile", "启用无障碍服务失败，不继续启动App");
            try {
                android.widget.Toast.makeText(
                        App.getContext(),
                        "❌ 启用失败\n请检查权限和服务名",
                        android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("✅ 已启用: ").append(config.label);
        msg.append("\n服务: ").append(shortServiceName(config.accessibilityService));

        if (config.launchApp && config.appPackage != null && !config.appPackage.isEmpty()) {
            ShellHelper.launchApp(config.appPackage);
            msg.append("\n已启动应用: ").append(config.appPackage);
        }

        setSlotState(true);

        try {
            android.widget.Toast.makeText(
                    App.getContext(), msg.toString(),
                    android.widget.Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {}
    }

    private void disable() {
        SlotConfig config = getSlotConfig();
        if (config == null || config.isEmpty()) return;

        Logger.d("Tile", "DISABLE slot=" + getSlotIndex() + " service=" + config.accessibilityService);
        boolean ok = ShellHelper.disableService(config.accessibilityService);
        Logger.d("Tile", "disableService 结果: " + ok);

        if (!ok) {
            Logger.e("Tile", "禁用无障碍服务失败");
            try {
                android.widget.Toast.makeText(
                        App.getContext(),
                        "❌ 禁用失败\n请检查权限",
                        android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {}
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("⛔ 已禁用: ").append(config.label);
        msg.append("\n服务: ").append(shortServiceName(config.accessibilityService));

        if (config.stopApp && config.appPackage != null && !config.appPackage.isEmpty()) {
            ShellHelper.forceStopApp(config.appPackage);
            ShellHelper.clearNotifications(config.appPackage);
            msg.append("\n已停止应用并清除通知: ").append(config.appPackage);
        }

        setSlotState(false);

        try {
            android.widget.Toast.makeText(
                    App.getContext(), msg.toString(),
                    android.widget.Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {}
    }

    private void updateTileUI() {
        Tile tile = getQsTile();
        if (tile == null) return;

        SlotConfig config = getSlotConfig();
        if (config == null || config.isEmpty()) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("未配置");
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile_empty));
            tile.updateTile();
            return;
        }

        // 检查服务是否实际在已启用列表中（而非仅依赖本地 preference）
        boolean active = isServiceActuallyEnabled(config.accessibilityService);
        Logger.d("Tile", "updateTileUI: slot=" + getSlotIndex()
                + " active=" + active + " service=" + config.accessibilityService);

        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(config.label);
        tile.setIcon(Icon.createWithResource(this,
                active ? R.drawable.ic_tile_on : R.drawable.ic_tile_off));
        tile.updateTile();
    }

    /** 将完整服务组件名缩短显示，如 com.a.b/.X.Y -> .X.Y */
    private String shortServiceName(String component) {
        if (component == null) return "";
        int slash = component.indexOf('/');
        if (slash >= 0 && slash + 1 < component.length()) {
            return component.substring(slash + 1);
        }
        return component;
    }

    /** 检查服务是否实际在系统的已启用无障碍服务列表中 */
    private boolean isServiceActuallyEnabled(String serviceComponent) {
        try {
            String enabled = ShellHelper.getEnabledServices();
            return enabled != null && enabled.contains(serviceComponent);
        } catch (Throwable t) {
            Logger.e("Tile", "isServiceActuallyEnabled 失败", t);
            return getSlotState();
        }
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