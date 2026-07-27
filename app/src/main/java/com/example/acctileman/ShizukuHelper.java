package com.example.acctileman;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Shizuku detection and permission helper.
 * Does NOT use Shizuku Java API (which requires moe.shizuku.server classes).
 * Instead, uses 'rish' command-line tool provided by Shizuku to detect availability
 * and execute shell commands.
 */
public class ShizukuHelper {

    /**
     * Check if Shizuku is available by testing if 'rish' command works.
     * rish is Shizuku's built-in remote shell tool.
     */
    public static boolean isAvailable() {
        Logger.d("ShizukuHelper", "isAvailable: 检测 rish 命令...");
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "which rish 2>/dev/null || echo not_found"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            reader.close();
            p.waitFor();
            if (line != null && !line.contains("not_found") && !line.isEmpty()) {
                Logger.d("ShizukuHelper", "isAvailable: rish 找到: " + line);
                return true;
            }
        } catch (Throwable t) {
            Logger.e("ShizukuHelper", "isAvailable: 检测失败", t);
        }

        // 备选：检查 Shizuku 是否在运行（通过 content provider）
        Logger.d("ShizukuHelper", "isAvailable: rish 未找到，尝试 am 检测 Shizuku");
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "pm list packages 2>/dev/null | grep -i shizuku"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            p.waitFor();
            boolean found = sb.toString().contains("shizuku");
            Logger.d("ShizukuHelper", "isAvailable: Shizuku 包 " + (found ? "已安装" : "未安装"));
            return found;
        } catch (Throwable t) {
            Logger.e("ShizukuHelper", "isAvailable: pm 检测失败", t);
        }
        return false;
    }

    /**
     * Check if we can read/write secure settings via rish.
     * Uses 'settings get' instead of 'dumpsys package' because:
     * - 'dumpsys package' requires android.permission.DUMP (system-only)
     * - 'settings get secure' tests the actual permission we need
     */
    public static boolean checkSelfPermission() {
        Logger.d("ShizukuHelper", "checkSelfPermission: 通过 settings 命令检查...");
        String result = ShellHelper.run("settings", "get", "secure", "accessibility_enabled");
        boolean granted = !result.contains("Permission") && !result.contains("denied")
                && !result.contains("Error") && !result.contains("exception");
        Logger.d("ShizukuHelper", "checkSelfPermission: " + granted + " (result="
                + (result.length() > 100 ? result.substring(0, 100) + "..." : result) + ")");
        return granted;
    }

    /**
     * Request Shizuku permission - launches Shizuku app for user to grant permission.
     */
    public static void requestPermission(int requestCode) {
        Logger.d("ShizukuHelper", "requestPermission: 尝试启动 Shizuku app");
        try {
            // 尝试通过 am 启动 Shizuku
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "am start -n moe.shizuku.privileged.api/moe.shizuku.manager.ShizukuManagerActivity 2>/dev/null ||" +
                    "am start -n rikka.shizuku/moe.shizuku.manager.ShizukuManagerActivity 2>/dev/null ||" +
                    "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
                    "-p rikka.shizuku 2>/dev/null ||" +
                    "monkey -p rikka.shizuku -c android.intent.category.LAUNCHER 1 2>/dev/null"});
            p.waitFor();
            Logger.d("ShizukuHelper", "requestPermission: Shizuku 启动命令已执行");
        } catch (Throwable t) {
            Logger.e("ShizukuHelper", "requestPermission: 启动 Shizuku 失败", t);
        }
    }

    // ---- 以下方法保留接口兼容性，内部不再使用 Java API ----

    public static Object createListener(PermissionCallback callback) {
        Logger.d("ShizukuHelper", "createListener: 使用 rish 模式，不需要 listener");
        return null;
    }

    public static void addRequestPermissionResultListener(Object listener) {
        // 不需要
    }

    public static void removeRequestPermissionResultListener(Object listener) {
        // 不需要
    }

    public interface PermissionCallback {
        void onResult(int requestCode, int grantResult);
    }
}