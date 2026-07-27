package com.example.acctileman;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

/**
 * Shell command executor.
 * Strategy: Find rish executable (Shizuku's remote shell), copy to app private dir with chmod +x,
 * then use rish -c for privileged commands. Falls back to Runtime.exec if rish unavailable.
 */
public class ShellHelper {

    private static final String TAG = "ShellHelper";
    private static String rishPath = null;
    private static boolean rishChecked = false;

    /**
     * Search for rish and copy to app private dir with execute permission.
     */
    private static void findRish() {
        if (rishChecked) return;
        rishChecked = true;
        Logger.d(TAG, "=== 搜索 rish 可执行文件 ===");

        // Step 1: 搜索源文件位置（Shizuku 导出的位置）
        String[] sourcePaths = {
                "/data/local/tmp/rish",
                "/sdcard/rish",
                "/storage/emulated/0/rish",
                "/storage/emulated/0/Download/rish",
                "/storage/emulated/0/Documents/rish",
        };
        String foundSource = null;
        for (String path : sourcePaths) {
            File f = new File(path);
            if (f.exists() && f.length() > 0) {
                foundSource = path;
                Logger.d(TAG, "rish 源文件找到: " + path + " (" + f.length() + " bytes)");
                break;
            }
        }

        // Step 2: 复制到 app 私有目录并赋予可执行权限
        if (foundSource != null) {
            try {
                File appDir = new File("/data/data/com.example.acctileman/files");
                if (!appDir.exists()) appDir.mkdirs();
                File rishFile = new File(appDir, "rish");

                // Copy
                FileInputStream in = new FileInputStream(foundSource);
                FileOutputStream out = new FileOutputStream(rishFile);
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                in.close();
                out.close();

                // chmod +x
                Runtime.getRuntime().exec(new String[]{"chmod", "755", rishFile.getAbsolutePath()}).waitFor();

                // Verify
                if (rishFile.canExecute() || rishFile.length() > 0) {
                    rishPath = rishFile.getAbsolutePath();
                    Logger.d(TAG, "rish 已复制到 app 私有目录: " + rishPath);
                    return;
                }
            } catch (Throwable t) {
                Logger.e(TAG, "复制 rish 失败", t);
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
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            reader.close();
            p.waitFor();
            if (line != null && !line.isEmpty()) {
                rishPath = line.trim();
                Logger.d(TAG, "rish 通过 which 找到: " + rishPath);
                return;
            }
        } catch (Throwable ignored) {}

        Logger.d(TAG, "rish 未找到，将使用 Runtime.exec（无特权）");
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
        run("monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1");
    }

    public static void forceStopApp(String packageName) {
        Logger.d(TAG, "forceStopApp: " + packageName);
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