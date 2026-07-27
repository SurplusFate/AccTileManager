package com.example.acctileman;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

/**
 * 权限辅助类。
 * 检查 WRITE_SECURE_SETTINGS 权限是否已授予，并引导用户授权。
 *
 * 该权限无法在 app 内直接请求，需要外部授予:
 *   方式1 (adb):     adb shell pm grant com.example.acctileman android.permission.WRITE_SECURE_SETTINGS
 *   方式2 (Shizuku): 在 Shizuku 的终端中执行上述命令
 *   方式3 (Root):    su -c "pm grant com.example.acctileman android.permission.WRITE_SECURE_SETTINGS"
 */
public class ShizukuHelper {

    private static final String TAG = "ShizukuHelper";
    private static final String PACKAGE_NAME = "com.example.acctileman";
    private static final String GRANT_CMD =
            "pm grant com.example.acctileman android.permission.WRITE_SECURE_SETTINGS";

    /**
     * 检查 WRITE_SECURE_SETTINGS 权限是否已授予。
     * 使用 PackageManager 检查，不依赖 shell 命令。
     */
    public static boolean checkSelfPermission() {
        try {
            int state = App.getContext().getPackageManager()
                    .checkPermission("android.permission.WRITE_SECURE_SETTINGS", PACKAGE_NAME);
            boolean granted = (state == PackageManager.PERMISSION_GRANTED);
            Logger.d(TAG, "checkSelfPermission: " + granted + " (state=" + state + ")");
            return granted;
        } catch (Throwable t) {
            Logger.e(TAG, "checkSelfPermission: 检查失败", t);
            return false;
        }
    }

    /**
     * 检查权限是否可用（兼容旧接口）。
     * 委托给 ShellHelper.isAvailable() 进行实际读写测试。
     */
    public static boolean isAvailable() {
        boolean permGranted = checkSelfPermission();
        boolean canRead = ShellHelper.isAvailable();
        Logger.d(TAG, "isAvailable: 权限=" + permGranted + " 可读=" + canRead);
        return permGranted && canRead;
    }

    /**
     * 请求权限 —— 无法在 app 内直接请求，引导用户操作。
     * 1. 检查 Shizuku 是否安装，如安装则打开 Shizuku
     * 2. 同时将授权命令复制到剪贴板
     */
    public static void requestPermission(Context ctx) {
        Logger.d(TAG, "requestPermission: 引导用户授权");

        // 复制授权命令到剪贴板
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("授权命令", GRANT_CMD));
                Logger.d(TAG, "授权命令已复制到剪贴板");
            }
        } catch (Throwable t) {
            Logger.e(TAG, "复制到剪贴板失败", t);
        }

        // 尝试打开 Shizuku app
        boolean shizukuOpened = false;
        try {
            Intent intent = ctx.getPackageManager()
                    .getLaunchIntentForPackage("rikka.shizuku");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
                shizukuOpened = true;
                Logger.d(TAG, "已打开 Shizuku app");
            }
        } catch (Throwable t) {
            Logger.e(TAG, "打开 Shizuku 失败", t);
        }

        // 显示引导信息
        String msg;
        if (shizukuOpened) {
            msg = "已打开 Shizuku，请在 Shizuku 的终端中粘贴执行:\n" + GRANT_CMD
                    + "\n（命令已复制到剪贴板）";
        } else {
            msg = "请通过以下方式授权:\n"
                    + "adb: " + GRANT_CMD + "\n"
                    + "（命令已复制到剪贴板）\n"
                    + "或安装 Shizuku 后在其中执行";
        }
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
    }

    // ---- 兼容旧接口，内部不再使用 ----

    public static Object createListener(PermissionCallback callback) {
        return null;
    }

    public static void addRequestPermissionResultListener(Object listener) {
    }

    public static void removeRequestPermissionResultListener(Object listener) {
    }

    public interface PermissionCallback {
        void onResult(int requestCode, int grantResult);
    }
}
