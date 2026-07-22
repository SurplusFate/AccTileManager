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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toast.makeText(this, "App started", Toast.LENGTH_SHORT).show();

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
        TextView tvStatus = findViewById(R.id.tv_shizuku_status);
        if (tvStatus != null) {
            tvStatus.setText("Shizuku: not checked");
        }

        // Current services button
        Button btnShowServices = findViewById(R.id.btn_show_services);
        if (btnShowServices != null) {
            btnShowServices.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    Toast.makeText(SettingsActivity.this, "Shizuku required", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Request Shizuku permission button
        Button btnRequestShizuku = findViewById(R.id.btn_request_shizuku);
        if (btnRequestShizuku != null) {
            btnRequestShizuku.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    Toast.makeText(SettingsActivity.this, "Please install Shizuku", Toast.LENGTH_SHORT).show();
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
}
