package com.example.acctileman;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Settings activity for configuring tile slots.
 */
public class SettingsActivity extends Activity {

    private static final String PREFS = "acc_tile_prefs";
    private static final int MAX_SLOTS = 3;
    private static final int REQUEST_CODE_SHIZUKU = 100;

    private Object permissionListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Shizuku permission listener (via reflection proxy)
        if (ShizukuHelper.isAvailable()) {
            permissionListener = ShizukuHelper.createListener(new ShizukuHelper.PermissionCallback() {
                @Override
                public void onResult(int requestCode, int grantResult) {
                    if (requestCode == REQUEST_CODE_SHIZUKU) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateShizukuStatus();
                                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    Toast.makeText(SettingsActivity.this, "Shizuku 权限已授予", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(SettingsActivity.this, "Shizuku 权限被拒绝", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                }
            });
            ShizukuHelper.addRequestPermissionResultListener(permissionListener);
        }

        // Initialize slot editors
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        for (int i = 0; i < MAX_SLOTS; i++) {
            initSlotEditor(i, prefs);
        }

        // Save buttons
        final SettingsActivity self = this;
        Button saveBtn0 = findViewById(R.id.btn_save_0);
        Button saveBtn1 = findViewById(R.id.btn_save_1);
        Button saveBtn2 = findViewById(R.id.btn_save_2);
        if (saveBtn0 != null) {
            saveBtn0.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) { self.saveSlot(0); }
            });
        }
        if (saveBtn1 != null) {
            saveBtn1.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) { self.saveSlot(1); }
            });
        }
        if (saveBtn2 != null) {
            saveBtn2.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) { self.saveSlot(2); }
            });
        }

        // Status
        updateShizukuStatus();

        // Current services button
        Button btnShowServices = findViewById(R.id.btn_show_services);
        if (btnShowServices != null) {
            btnShowServices.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    showCurrentServices();
                }
            });
        }

        // Request Shizuku permission button
        Button btnRequestShizuku = findViewById(R.id.btn_request_shizuku);
        if (btnRequestShizuku != null) {
            btnRequestShizuku.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    requestShizukuPermission();
                }
            });
        }
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

        Toast.makeText(this, "磁贴 " + (slot + 1) + " 已保存", Toast.LENGTH_SHORT).show();
    }

    private void showCurrentServices() {
        if (!ShellHelper.isAvailable()) {
            Toast.makeText(this, "Shizuku 不可用，请确保已安装并运行", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String result = ShellHelper.getEnabledServices();
            if (result.isEmpty()) {
                result = "(无已启用的无障碍服务)";
            }
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Shizuku 调用失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void requestShizukuPermission() {
        if (!ShizukuHelper.isAvailable()) {
            Toast.makeText(this, "请确保 Shizuku 已安装并运行", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ShizukuHelper.checkSelfPermission()) {
            Toast.makeText(this, "Shizuku 权限已授予", Toast.LENGTH_SHORT).show();
        } else {
            ShizukuHelper.requestPermission(REQUEST_CODE_SHIZUKU);
        }
    }

    private void updateShizukuStatus() {
        TextView tvStatus = findViewById(R.id.tv_shizuku_status);
        if (tvStatus != null) {
            if (!ShizukuHelper.isAvailable()) {
                tvStatus.setText("Shizuku: 未安装/未运行");
                tvStatus.setTextColor(0xFFFF9800);
            } else if (ShizukuHelper.checkSelfPermission()) {
                tvStatus.setText("Shizuku 权限: 已授予");
                tvStatus.setTextColor(0xFF4CAF50);
            } else {
                tvStatus.setText("Shizuku 权限: 未授予");
                tvStatus.setTextColor(0xFFF44336);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (permissionListener != null) {
            ShizukuHelper.removeRequestPermissionResultListener(permissionListener);
        }
    }
}
