package com.example.acctileman;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/**
 * Helper class to execute shell commands via Shizuku.
 * Uses reflection to call Shizuku.newProcess() which is private in the API.
 * All operations run with shell-level (adb) permissions.
 */
public class ShellHelper {

    private static final String TAG = "AccTileMan";

    private static Method newProcessMethod = null;

    static {
        try {
            newProcessMethod = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            newProcessMethod.setAccessible(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get Shizuku.newProcess method", e);
        }
    }

    /**
     * Execute a command via Shizuku and return the Process object.
     */
    private static Process shizukuExec(String[] cmd) {
        if (newProcessMethod == null) {
            Log.e(TAG, "Shizuku.newProcess not available");
            return null;
        }
        try {
            return (Process) newProcessMethod.invoke(null, cmd, null, null);
        } catch (Exception e) {
            Log.e(TAG, "shizukuExec failed: " + String.join(" ", cmd), e);
            return null;
        }
    }

    /**
     * Run a shell command via Shizuku and return stdout.
     */
    public static String run(String... cmd) {
        try {
            Process process = shizukuExec(cmd);
            if (process == null) return "";
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Log.w(TAG, "cmd exit " + exitCode + ": " + String.join(" ", cmd) + " | err: " + stderr);
            }
            return stdout.trim();
        } catch (Exception e) {
            Log.e(TAG, "run failed: " + String.join(" ", cmd), e);
            return "";
        }
    }

    /**
     * Run a shell command via Shizuku (no return value needed).
     */
    public static void exec(String... cmd) {
        try {
            Process process = shizukuExec(cmd);
            if (process != null) process.waitFor();
        } catch (Exception e) {
            Log.e(TAG, "exec failed: " + String.join(" ", cmd), e);
        }
    }

    /**
     * Get the current enabled accessibility services list.
     */
    public static String getEnabledServices() {
        return run("settings", "get", "secure", "enabled_accessibility_services");
    }

    /**
     * Add a service to the enabled accessibility services list.
     */
    public static boolean enableService(String serviceComponent) {
        String current = getEnabledServices();
        if (current.contains(serviceComponent)) {
            Log.d(TAG, "service already enabled: " + serviceComponent);
            return true;
        }
        String newList = current.isEmpty() ? serviceComponent : current + ":" + serviceComponent;
        exec("settings", "put", "secure", "enabled_accessibility_services", newList);
        exec("settings", "put", "secure", "accessibility_enabled", "1");
        Log.d(TAG, "enabled: " + serviceComponent);
        return true;
    }

    /**
     * Remove a service from the enabled accessibility services list.
     * Preserves other enabled services.
     */
    public static boolean disableService(String serviceComponent) {
        String current = getEnabledServices();
        if (!current.contains(serviceComponent)) {
            Log.d(TAG, "service not enabled: " + serviceComponent);
            return true;
        }
        String newList = current
                .replaceAll("(?<!\\S)" + java.util.regex.Pattern.quote(serviceComponent) + "(?!\\S)", "")
                .replaceAll("::+", ":")
                .replaceAll("^:+|:+$", "");
        exec("settings", "put", "secure", "enabled_accessibility_services", newList);
        if (newList.isEmpty()) {
            exec("settings", "put", "secure", "accessibility_enabled", "0");
        }
        Log.d(TAG, "disabled: " + serviceComponent);
        return true;
    }

    /**
     * Launch an app by package name.
     */
    public static void launchApp(String packageName) {
        run("monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1");
    }

    /**
     * Force stop an app by package name.
     */
    public static void forceStopApp(String packageName) {
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
}
