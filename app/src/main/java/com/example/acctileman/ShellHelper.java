package com.example.acctileman;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * Helper class to execute shell commands via Shizuku.
 * Uses reflection to call Shizuku.newProcess() which is private in the API.
 * All operations run with shell-level (adb) permissions.
 */
public class ShellHelper {

    private static Method newProcessMethod = null;
    private static boolean initialized = false;

    private static void init() {
        if (initialized) return;
        initialized = true;
        Logger.d("ShellHelper", "=== 开始初始化 ShellHelper ===");
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            newProcessMethod = shizukuClass.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            newProcessMethod.setAccessible(true);
            Logger.d("ShellHelper", "Shizuku.newProcess 方法获取成功");
        } catch (Throwable t) {
            Logger.e("ShellHelper", "Shizuku.newProcess 不可用", t);
            newProcessMethod = null;
        }
        Logger.d("ShellHelper", "=== ShellHelper 初始化完成: newProcess=" + (newProcessMethod != null) + " ===");
    }

    private static Process shizukuExec(String[] cmd) {
        init();
        if (newProcessMethod == null) {
            Logger.e("ShellHelper", "shizukuExec: newProcess 不可用, cmd=" + String.join(" ", cmd));
            return null;
        }
        try {
            Logger.d("ShellHelper", "shizukuExec: " + String.join(" ", cmd));
            return (Process) newProcessMethod.invoke(null, cmd, null, null);
        } catch (Throwable t) {
            Logger.e("ShellHelper", "shizukuExec 失败: " + String.join(" ", cmd), t);
            return null;
        }
    }

    public static boolean isAvailable() {
        init();
        return newProcessMethod != null;
    }

    public static String run(String... cmd) {
        Logger.d("ShellHelper", "run: " + String.join(" ", cmd));
        try {
            Process process = shizukuExec(cmd);
            if (process == null) {
                Logger.e("ShellHelper", "run: process 为 null");
                return "";
            }
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            int exitCode = process.waitFor();
            Logger.d("ShellHelper", "run 结果: exit=" + exitCode + " stdout=" + truncate(stdout, 200));
            if (exitCode != 0) {
                Logger.w("ShellHelper", "run 非零退出码: " + exitCode + " stderr=" + truncate(stderr, 200));
            }
            return stdout.trim();
        } catch (Throwable t) {
            Logger.e("ShellHelper", "run 异常: " + String.join(" ", cmd), t);
            return "";
        }
    }

    public static void exec(String... cmd) {
        Logger.d("ShellHelper", "exec: " + String.join(" ", cmd));
        try {
            Process process = shizukuExec(cmd);
            if (process != null) {
                int exitCode = process.waitFor();
                Logger.d("ShellHelper", "exec 完成: exit=" + exitCode);
            }
        } catch (Throwable t) {
            Logger.e("ShellHelper", "exec 异常: " + String.join(" ", cmd), t);
        }
    }

    public static String getEnabledServices() {
        Logger.d("ShellHelper", "getEnabledServices: 查询中...");
        return run("settings", "get", "secure", "enabled_accessibility_services");
    }

    public static boolean enableService(String serviceComponent) {
        Logger.d("ShellHelper", "enableService: " + serviceComponent);
        String current = getEnabledServices();
        if (current.contains(serviceComponent)) {
            Logger.d("ShellHelper", "enableService: 已启用，跳过");
            return true;
        }
        String newList = current.isEmpty() ? serviceComponent : current + ":" + serviceComponent;
        Logger.d("ShellHelper", "enableService: 新列表=" + newList);
        exec("settings", "put", "secure", "enabled_accessibility_services", newList);
        exec("settings", "put", "secure", "accessibility_enabled", "1");
        return true;
    }

    public static boolean disableService(String serviceComponent) {
        Logger.d("ShellHelper", "disableService: " + serviceComponent);
        String current = getEnabledServices();
        if (!current.contains(serviceComponent)) {
            Logger.d("ShellHelper", "disableService: 未启用，跳过");
            return true;
        }
        String newList = current
                .replaceAll("(?<!\\S)" + java.util.regex.Pattern.quote(serviceComponent) + "(?!\\S)", "")
                .replaceAll("::+", ":")
                .replaceAll("^:+|:+$", "");
        Logger.d("ShellHelper", "disableService: 新列表=" + newList);
        exec("settings", "put", "secure", "enabled_accessibility_services", newList);
        if (newList.isEmpty()) {
            exec("settings", "put", "secure", "accessibility_enabled", "0");
        }
        return true;
    }

    public static void launchApp(String packageName) {
        Logger.d("ShellHelper", "launchApp: " + packageName);
        run("monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1");
    }

    public static void forceStopApp(String packageName) {
        Logger.d("ShellHelper", "forceStopApp: " + packageName);
        exec("am", "force-stop", packageName);
    }

    private static String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(line);
        }
        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}