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
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import rikka.shizuku.Shizuku;

/**
 * Settings activity for configuring tile slots.
 */
public class SettingsActivity extends Activity {

    private static final String PREFS = "acc_tile_prefs";
    private static final int MAX_SLOTS = 3;
    private static final int REQUEST_CODE_SHIZUKU = 100;

    // Permission listener (avoid lambda for d8 desugaring compatibility)
    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onResult(int requestCode, int grantResult) {
                    onShizukuPermissionResult(requestCode, grantResult);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_settings);

            // Shizuku permission listener
            try {
                Shizuku.addRequestPermissionResultListener(permissionListener);
            } catch (Exception e) {
                // Shizuku not running yet, that's ok
                logError("addPermissionListener", e);
            }

            // Initialize slot editors
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            for (int i = 0; i < MAX_SLOTS; i++) {
                initSlotEditor(i, prefs);
            }

            // Save buttons
            Button saveBtn0 = findViewById(R.id.btn_save_0);
            Button saveBtn1 = findViewById(R.id.btn_save_1);
            Button saveBtn2 = findViewById(R.id.btn_save_2);
            final SettingsActivity self = this;
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
            try {
                updateShizukuStatus();
            } catch (Exception e) {
                logError("updateStatus", e);
            }

            // Current services button
            Button btnShowServices = findViewById(R.id.btn_show_services);
            if (btnShowServices != null) {
                btnShowServices.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View v) {
                        showCurrentServices();
                    }
                });
            }

            // Request Shizuku permission button
            Button btnRequestShizuku = findViewById(R.id.btn_request_shizuku);
            if (btnRequestShizuku != null) {
                btnRequestShizuku.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View v) {
                        requestShizukuPermission();
                    }
                });
            }
        } catch (Throwable t) {
            logError("onCreate", t);
            throw t;
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
        try {
            String result = com.example.acctileman.ShellHelper.getEnabledServices();
            if (result.isEmpty()) {
                result = "(无已启用的无障碍服务)";
            }
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "需要先授予 Shizuku 权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestShizukuPermission() {
        try {
            if (Shizuku.checkSelfPermission()
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku 权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU);
            }
        } catch (Exception e) {
            Toast.makeText(this, "请确保 Shizuku 已安装并运行", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateShizukuStatus() {
        TextView tvStatus = findViewById(R.id.tv_shizuku_status);
        if (tvStatus != null) {
            try {
                boolean granted = Shizuku.checkSelfPermission()
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
                tvStatus.setText(granted ? "Shizuku 权限: 已授予" : "Shizuku 权限: 未授予");
                tvStatus.setTextColor(granted ? 0xFF4CAF50 : 0xFFF44336);
            } catch (Exception e) {
                tvStatus.setText("Shizuku 权限: 未连接");
                tvStatus.setTextColor(0xFFFF9800);
            }
        }
    }

    private void onShizukuPermissionResult(int requestCode, int grantResult) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener);
        } catch (Exception ignored) {}
    }

    /**
     * Write crash/error info to /sdcard/Download/acctileman_error.log
     */
    private void logError(String tag, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("[" + tag + "] " + android.text.format.DateFormat.format(
                    "yyyy-MM-dd HH:mm:ss", new java.util.Date()));
            t.printStackTrace(pw);
            pw.close();

            String dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            File logFile = new File(dir, "acctileman_error.log");
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(sw.toString() + "\n");
            fw.close();
        } catch (Exception ignored) {}
    }
}
