package com.example.acctileman;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Shell command executor.
 * Strategy: Find rish executable (Shizuku's remote shell), copy to app private dir with chmod +x,
 * then use rish -c for privileged commands. Falls back to Runtime.exec if rish unavailable.
 */
public class ShellHelper {

    private static final String TAG = "ShellHelper";
    private static final java.util.regex.Pattern PKG_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.]*$");
    private static final java.util.regex.Pattern SVC_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.]+/[a-zA-Z][a-zA-Z0-9_.]+$");
    private static String rishPath = null;
    private static boolean rishChecked = false;

    private static boolean isValidPackageName(String s) {
        return s != null && !s.isEmpty() && PKG_PATTERN.matcher(s).matches();
    }

    private static boolean isValidServiceComponent(String s) {
        return s != null && !s.isEmpty() && SVC_PATTERN.matcher(s).matches();
    }

    private static void drainAndWait(Process p, String desc) {
        try {
            java.io.InputStream stdout = p.getInputStream();
            java.io.InputStream stderr = p.getErrorStream();
            byte[] buf = new byte[256];
            while (stdout.read(buf) > 0) {}
            while (stderr.read(buf) > 0) {}
        } catch (Throwable ignored) {}
        try {
            p.waitFor();
        } catch (Throwable ignored) {}
        try { p.destroy(); } catch (Throwable ignored) {}
    }

    /**
     * Search for rish and set rishPath.
     * On Android 10+ with scoped storage, /sdcard/Download/ may not be readable.
     * Prioritize /data/local/tmp/ paths where Shizuku typically installs the binary.
     */
    private static void findRish() {
        if (rishChecked) return;
        rishChecked = true;
        Logger.d(TAG, "=== 搜索 rish 可执行文件 ===");

        // Step 1: 搜索源文件位置（优先 /data/local/tmp/，避开分区存储限制）
        String[] sourcePaths = {
                "/data/local/tmp/rish",
                "/data/local/tmp/rish_shizuku",
                "/sdcard/rish",
                "/storage/emulated/0/rish",
                "/storage/emulated/0/Download/rish",
                "/storage/emulated/0/Documents/rish",
        };

        // 先尝试不复制，直接用 /data/local/tmp 路径（通常 rish_shizuku 在这里）
        for (String path : sourcePaths) {
            if (path.startsWith("/data/local/tmp/") || path.startsWith("/sdcard/")) {
                File f = new File(path);
                if (f.exists() && f.length() > 0 && f.canExecute()) {
                    rishPath = path;
                    Logger.d(TAG, "rish 直接使用: " + path);
                    return;
                }
            }
        }

        // Step 2: 复制到 app 私有目录并赋予可执行权限
        for (String sourcePath : sourcePaths) {
            File srcFile = new File(sourcePath);
            if (!srcFile.exists() || srcFile.length() <= 0) continue;
            Logger.d(TAG, "rish 源文件找到: " + sourcePath + " (" + srcFile.length() + " bytes)");

            try {
                File appDir = new File("/data/data/com.example.acctileman/files");
                if (!appDir.exists()) appDir.mkdirs();
                File rishFile = new File(appDir, "rish");

                // 优先用 shell cp（在某些设备上可绕过分区存储）
                try {
                    Process cp = Runtime.getRuntime().exec(new String[]{
                            "sh", "-c", "cp " + sourcePath + " " + rishFile.getAbsolutePath()
                                    + " && chmod 755 " + rishFile.getAbsolutePath()
                    });
                    drainAndWait(cp, "shell cp");
                    if (rishFile.exists() && rishFile.length() > 0) {
                        rishPath = rishFile.getAbsolutePath();
                        Logger.d(TAG, "rish (shell cp) 已复制到: " + rishPath);
                        return;
                    }
                } catch (Throwable ignored) {}

                // 回退到 Java IO 复制
                try {
                    try (java.io.FileInputStream in = new java.io.FileInputStream(sourcePath);
                         java.io.FileOutputStream out = new java.io.FileOutputStream(rishFile)) {
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    }

                    try {
                        Process chmod = Runtime.getRuntime().exec(
                                new String[]{"chmod", "755", rishFile.getAbsolutePath()});
                        drainAndWait(chmod, "chmod");
                    } catch (Throwable chmodEx) {
                        Logger.w(TAG, "chmod 失败: " + chmodEx.getMessage());
                    }

                    if (rishFile.length() > 0) {
                        rishPath = rishFile.getAbsolutePath();
                        Logger.d(TAG, "rish (java io) 已复制到: " + rishPath);
                        return;
                    }
                } catch (Throwable ioEx) {
                    Logger.w(TAG, "Java IO 复制失败(" + sourcePath + "): " + ioEx.getMessage());
                }
            } catch (Throwable t) {
                Logger.w(TAG, "复制 rish 失败(" + sourcePath + "): " + t.getMessage());
            }
        }

        // Step 3: 检查 app 私有目录是否已有 rish
        File appRish = new File("/data/data/com.example.acctileman/files/rish");
        if (appRish.exists() && appRish.length() > 0) {
            rishPath = appRish.getAbsolutePath();
            Logger.d(TAG, "rish 在 app 私有目录已存在: " + rishPath);
            return;
        }

        // Step 4: 尝试 which
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "which rish 2>/dev/null"});
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            reader.close();
            drainAndWait(p, "which");
            if (line != null && !line.isEmpty()) {
                rishPath = line.trim();
                Logger.d(TAG, "rish 通过 which 找到: " + rishPath);
                return;
            }
        } catch (Throwable ignored) {}

        Logger.d(TAG, "rish 未找到，将使用 Runtime.exec（无特权）");
        Logger.w(TAG, "请确保已通过 Shizuku 导出 rish 到 /data/local/tmp/ 目录");
    }

    public static boolean isAvailable() {
        findRish();
        return rishPath != null;
    }

    public static String run(String... cmd) {
        String cmdStr = String.join(" ", cmd);
        Logger.d(TAG, "run: " + cmdStr);

        findRish();
        try {
            String[] execCmd;
            if (rishPath != null) {
                execCmd = new String[]{rishPath, "-c", cmdStr};
                Logger.d(TAG, "exec(rish): " + String.join(" ", execCmd));
            } else {
                execCmd = cmd;
                Logger.d(TAG, "exec(runtime): " + String.join(" ", execCmd));
            }

            Process process = Runtime.getRuntime().exec(execCmd);
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            int exitCode = process.waitFor();
            Logger.d(TAG, "结果: exit=" + exitCode + " stdout=" + truncate(stdout, 300));
            if (exitCode != 0) {
                Logger.w(TAG, "非零退出码: " + exitCode + " stderr=" + truncate(stderr, 200));
            }
            return stdout.trim();
        } catch (Throwable t) {
            Logger.e(TAG, "run 异常: " + cmdStr, t);
            return "";
        }
    }

    public static void exec(String... cmd) {
        run(cmd);
    }

    public static String getEnabledServices() {
        return run("settings", "get", "secure", "enabled_accessibility_services");
    }

    public static boolean enableService(String serviceComponent) {
        Logger.d(TAG, "enableService: " + serviceComponent);
        if (!isValidServiceComponent(serviceComponent)) {
            Logger.w(TAG, "enableService: 无效的服务组件名: " + serviceComponent);
            return false;
        }
        String current = getEnabledServices();
        if (current.contains(serviceComponent)) {
            Logger.d(TAG, "enableService: 已启用，跳过");
            return true;
        }
        String newList = current.isEmpty() ? serviceComponent : current + ":" + serviceComponent;
        Logger.d(TAG, "enableService: 新列表=" + newList);
        exec("settings", "put", "secure", "enabled_accessibility_services", newList);
        exec("settings", "put", "secure", "accessibility_enabled", "1");
        return true;
    }

    public static boolean disableService(String serviceComponent) {
        Logger.d(TAG, "disableService: " + serviceComponent);
        if (!isValidServiceComponent(serviceComponent)) {
            Logger.w(TAG, "disableService: 无效的服务组件名: " + serviceComponent);
            return false;
        }
        String current = getEnabledServices();
        if (!current.contains(serviceComponent)) {
            Logger.d(TAG, "disableService: 未启用，跳过");
            return true;
        }
        String newList = current
                .replaceAll("(?<!\\S)" + java.util.regex.Pattern.quote(serviceComponent) + "(?!\\S)", "")
                .replaceAll("::+", ":")
                .replaceAll("^:+|:+$", "");
        Logger.d(TAG, "disableService: 新列表=" + newList);
        exec("settings", "put", "secure", "enabled_accessibility_services", newList);
        if (newList.isEmpty()) {
            exec("settings", "put", "secure", "accessibility_enabled", "0");
        }
        return true;
    }

    public static void launchApp(String packageName) {
        Logger.d(TAG, "launchApp: " + packageName);
        if (!isValidPackageName(packageName)) {
            Logger.w(TAG, "launchApp: 无效的包名: " + packageName);
            return;
        }
        run("monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1");
    }

    public static void forceStopApp(String packageName) {
        Logger.d(TAG, "forceStopApp: " + packageName);
        if (!isValidPackageName(packageName)) {
            Logger.w(TAG, "forceStopApp: 无效的包名: " + packageName);
            return;
        }
        exec("am", "force-stop", packageName);
    }

    private static String readStream(java.io.InputStream is) throws java.io.IOException {
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