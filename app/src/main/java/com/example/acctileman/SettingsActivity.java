package com.example.acctileman;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * Settings activity for configuring tile slots.
 */
public class SettingsActivity extends Activity {

    private static final String PREFS = "acc_tile_prefs";
    private static final int MAX_SLOTS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.d("Settings", "=== SettingsActivity onCreate ===");
        setContentView(R.layout.activity_settings);

        // Initialize slot editors
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        for (int i = 0; i < MAX_SLOTS; i++) {
            initSlotEditor(i, prefs);
        }

        // Save buttons
        final SettingsActivity self = this;
        for (int i = 0; i < MAX_SLOTS; i++) {
            final int slot = i;
            Button btn = findViewById(getResId("btn_save_" + slot));
            if (btn != null) {
                btn.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override public void onClick(android.view.View v) { self.saveSlot(slot); }
                });
            }
        }

        // Status
        updatePermissionStatus();

        // Current services button
        Button btnShowServices = findViewById(R.id.btn_show_services);
        if (btnShowServices != null) {
            btnShowServices.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    showCurrentServices();
                }
            });
        }

        // Request permission button
        Button btnRequestShizuku = findViewById(R.id.btn_request_shizuku);
        if (btnRequestShizuku != null) {
            btnRequestShizuku.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    requestPermission();
                }
            });
        }

        // Export log button
        Button btnExportLog = findViewById(R.id.btn_export_log);
        if (btnExportLog != null) {
            btnExportLog.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    exportLog();
                }
            });
        }

        // Clear log button
        Button btnClearLog = findViewById(R.id.btn_clear_log);
        if (btnClearLog != null) {
            btnClearLog.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    Logger.clear();
                    Toast.makeText(SettingsActivity.this, "日志已清空", Toast.LENGTH_SHORT).show();
                }
            });
        }

        Logger.d("Settings", "=== SettingsActivity onCreate 完成 ===");
    }

    private void initSlotEditor(int slot, SharedPreferences prefs) {
        String prefix = "slot_" + slot + "_";

        EditText etApp = findViewById(getResId("et_app_" + slot));
        EditText etService = findViewById(getResId("et_service_" + slot));
        EditText etLabel = findViewById(getResId("et_label_" + slot));
        CheckBox cbLaunch = findViewById(getResId("cb_launch_" + slot));
        CheckBox cbStop = findViewById(getResId("cb_stop_" + slot));

        if (etApp != null) etApp.setText(prefs.getString(prefix + "app", ""));
        if (etService != null) etService.setText(prefs.getString(prefix + "service", ""));
        if (etLabel != null) etLabel.setText(prefs.getString(prefix + "label", ""));
        if (cbLaunch != null) cbLaunch.setChecked(prefs.getBoolean(prefix + "launch", true));
        if (cbStop != null) cbStop.setChecked(prefs.getBoolean(prefix + "stop", true));
    }

    private int getResId(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    private void saveSlot(int slot) {
        Logger.d("Settings", "saveSlot: " + slot);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String prefix = "slot_" + slot + "_";

        EditText etApp = findViewById(getResId("et_app_" + slot));
        EditText etService = findViewById(getResId("et_service_" + slot));
        EditText etLabel = findViewById(getResId("et_label_" + slot));
        CheckBox cbLaunch = findViewById(getResId("cb_launch_" + slot));
        CheckBox cbStop = findViewById(getResId("cb_stop_" + slot));

        String app = etApp != null ? etApp.getText().toString().trim() : "";
        String service = etService != null ? etService.getText().toString().trim() : "";
        String label = etLabel != null ? etLabel.getText().toString().trim() : "";
        boolean launch = cbLaunch != null && cbLaunch.isChecked();
        boolean stop = cbStop != null && cbStop.isChecked();

        editor.putString(prefix + "app", app);
        editor.putString(prefix + "service", service);
        editor.putString(prefix + "label", label.isEmpty() ? "无障碍 " + (slot + 1) : label);
        editor.putBoolean(prefix + "launch", launch);
        editor.putBoolean(prefix + "stop", stop);
        editor.apply();

        Logger.d("Settings", "saveSlot " + slot + ": app=" + app + " service=" + service + " label=" + label);
        Toast.makeText(this, "磁贴 " + (slot + 1) + " 已保存", Toast.LENGTH_SHORT).show();
    }

    private void showCurrentServices() {
        Logger.d("Settings", "showCurrentServices: 点击");
        if (!ShellHelper.isAvailable()) {
            Logger.w("Settings", "showCurrentServices: 无权限");
            Toast.makeText(this, "无 WRITE_SECURE_SETTINGS 权限，请先授权", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String result = ShellHelper.getEnabledServices();
            if (result.isEmpty()) {
                result = "(无已启用的无障碍服务)";
            }
            Logger.d("Settings", "showCurrentServices: 结果=" + result);
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Logger.e("Settings", "showCurrentServices: 失败", t);
            Toast.makeText(this, "读取失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void requestPermission() {
        Logger.d("Settings", "=== requestPermission: 用户点击了授权按钮 ===");
        if (ShizukuHelper.checkSelfPermission()) {
            Logger.d("Settings", "requestPermission: 权限已授予");
            Toast.makeText(this, "权限已授予，无需重复操作", Toast.LENGTH_SHORT).show();
            updatePermissionStatus();
            return;
        }
        Logger.d("Settings", "requestPermission: 权限未授予，引导用户...");
        ShizukuHelper.requestPermission(this);
        updatePermissionStatus();
    }

    private void updatePermissionStatus() {
        TextView tvStatus = findViewById(R.id.tv_shizuku_status);
        if (tvStatus != null) {
            boolean hasPermission = ShizukuHelper.checkSelfPermission();
            if (hasPermission) {
                tvStatus.setText("权限状态: 已授权 (可正常使用)");
                tvStatus.setTextColor(0xFF4CAF50);
                Logger.d("Settings", "updatePermissionStatus: 已授权");
            } else {
                tvStatus.setText("权限状态: 未授权 (点击下方按钮授权)");
                tvStatus.setTextColor(0xFFF44336);
                Logger.d("Settings", "updatePermissionStatus: 未授权");
            }
        }
    }

    private void exportLog() {
        Logger.d("Settings", "=== 导出日志 ===");
        File logFile = new File(Logger.getLogPath());
        if (!logFile.exists() || logFile.length() == 0) {
            Logger.w("Settings", "日志文件不存在或为空, path=" + Logger.getLogPath());
            Toast.makeText(this, "日志为空，请先操作一下再导出\n路径: " + Logger.getLogPath(), Toast.LENGTH_LONG).show();
            return;
        }
        Logger.d("Settings", "日志文件: " + logFile.getAbsolutePath() + " 大小: " + logFile.length() + " bytes");

        // 通过 share intent 导出
        try {
            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            android.net.Uri shareUri = LogFileProvider.getUriForFile(logFile);
            shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, shareUri);
            shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(shareIntent, "导出日志"));
            Logger.d("Settings", "日志分享 intent 已启动, uri=" + shareUri);
        } catch (Throwable t) {
            Logger.e("Settings", "分享失败，降级显示路径", t);
            Toast.makeText(this, "日志路径:\n" + logFile.getAbsolutePath()
                    + "\n(可通过文件管理器访问)", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Logger.d("Settings", "=== SettingsActivity onResume ===");
        // 用户可能从 Shizuku 返回，刷新权限状态
        updatePermissionStatus();
    }
}
