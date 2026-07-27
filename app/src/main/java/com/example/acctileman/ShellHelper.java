package com.example.acctileman;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * 无障碍服务管理器。
 * 直接使用 Settings.Secure API 读写无障碍服务列表。
 * 需要 WRITE_SECURE_SETTINGS 权限（通过 adb 或 Shizuku 授予）。
 *
 * 授权方式（任选其一）:
 *   adb:    adb shell pm grant com.example.acctileman android.permission.WRITE_SECURE_SETTINGS
 *   Shizuku: 在 Shizuku app 中对该 app 授权
 */
public class ShellHelper {

    private static final String TAG = "ShellHelper";
    private static Context appContext;

    /** 初始化，由 App.onCreate 调用 */
    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
    }

    /**
     * 检查是否有 WRITE_SECURE_SETTINGS 权限。
     * 方法：尝试读取 enabled_accessibility_services，如果返回 null 说明无权限。
     */
    public static boolean isAvailable() {
        if (appContext == null) {
            Logger.w(TAG, "isAvailable: appContext 为 null，未初始化");
            return false;
        }
        try {
            // 能读到值（包括空字符串）说明有权限；返回 null 说明无权限
            String val = Settings.Secure.getString(
                    appContext.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean hasPermission = (val != null);
            Logger.d(TAG, "isAvailable: " + hasPermission + " (val=" + truncate(val, 100) + ")");
            return hasPermission;
        } catch (Throwable t) {
            Logger.e(TAG, "isAvailable: 检查失败", t);
            return false;
        }
    }

    /** 获取当前已启用的无障碍服务列表 */
    public static String getEnabledServices() {
        if (appContext == null) return "";
        try {
            String raw = Settings.Secure.getString(
                    appContext.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return raw != null ? raw : "";
        } catch (Throwable t) {
            Logger.e(TAG, "getEnabledServices 失败", t);
            return "";
        }
    }

    /**
     * 启用指定的无障碍服务。
     * @param serviceComponent 格式: 包名/服务类全名，如 com.example.app/.MyAccessibilityService
     * @return true 表示操作成功
     */
    public static boolean enableService(String serviceComponent) {
        Logger.d(TAG, "enableService: " + serviceComponent);
        if (!isAvailable()) {
            Logger.w(TAG, "enableService: 无 WRITE_SECURE_SETTINGS 权限");
            return false;
        }
        if (!isValidServiceComponent(serviceComponent)) {
            Logger.e(TAG, "enableService: 无效的服务组件名（必须包含 '/'）：" + serviceComponent);
            return false;
        }

        List<String> services = parseServiceList(getEnabledServices());
        if (services.contains(serviceComponent)) {
            Logger.d(TAG, "enableService: 已在列表中，跳过");
            return true;
        }
        services.add(serviceComponent);
        String newList = joinServiceList(services);

        boolean ok = Settings.Secure.putString(
                appContext.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                newList);
        Settings.Secure.putInt(
                appContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1);
        Logger.d(TAG, "enableService: putString=" + ok + " newList=" + truncate(newList, 200));
        return ok;
    }

    /**
     * 禁用指定的无障碍服务。
     * @param serviceComponent 格式: 包名/服务类全名
     * @return true 表示操作成功
     */
    public static boolean disableService(String serviceComponent) {
        Logger.d(TAG, "disableService: " + serviceComponent);
        if (!isAvailable()) {
            Logger.w(TAG, "disableService: 无 WRITE_SECURE_SETTINGS 权限");
            return false;
        }
        if (!isValidServiceComponent(serviceComponent)) {
            Logger.e(TAG, "disableService: 无效的服务组件名（必须包含 '/'）：" + serviceComponent);
            return false;
        }

        List<String> services = parseServiceList(getEnabledServices());
        boolean removed = services.remove(serviceComponent);
        if (!removed) {
            Logger.d(TAG, "disableService: 不在列表中，跳过");
            return true;
        }
        String newList = joinServiceList(services);

        boolean ok = Settings.Secure.putString(
                appContext.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                newList);
        if (services.isEmpty()) {
            Settings.Secure.putInt(
                    appContext.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0);
        }
        Logger.d(TAG, "disableService: putString=" + ok + " newList=" + truncate(newList, 200));
        return ok;
    }

    /** 启动指定 app（使用 PackageManager，不需要特权） */
    public static void launchApp(String packageName) {
        Logger.d(TAG, "launchApp: " + packageName);
        if (appContext == null) return;
        try {
            Intent intent = appContext.getPackageManager()
                    .getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(intent);
                Logger.d(TAG, "launchApp: 已启动 " + packageName);
            } else {
                Logger.w(TAG, "launchApp: 无可启动的 Activity for " + packageName);
            }
        } catch (Throwable t) {
            Logger.e(TAG, "launchApp 失败", t);
        }
    }

    /**
     * 强制停止 app（需要特权，通过 Shizuku 执行）。
     * 如果无特权则静默跳过。
     */
    public static void forceStopApp(String packageName) {
        Logger.d(TAG, "forceStopApp: " + packageName);
        // 尝试通过 Shizuku 的 am force-stop
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "am", "force-stop", packageName
            });
            int exitCode = p.waitFor();
            Logger.d(TAG, "forceStopApp: exit=" + exitCode);
        } catch (Throwable t) {
            Logger.e(TAG, "forceStopApp 失败（无特权，已跳过）", t);
        }
    }

    // ---- 工具方法 ----

    private static List<String> parseServiceList(String raw) {
        List<String> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return list;
        // 服务列表以 : 分隔，格式: pkg1/svc1:pkg2/svc2
        String[] parts = raw.split(":");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private static String joinServiceList(List<String> services) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < services.size(); i++) {
            if (i > 0) sb.append(":");
            sb.append(services.get(i));
        }
        return sb.toString();
    }

    /** 验证服务组件名格式：必须包含 '/' */
    private static boolean isValidServiceComponent(String s) {
        return s != null && s.contains("/");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
