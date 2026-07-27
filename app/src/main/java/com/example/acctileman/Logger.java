package com.example.acctileman;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Centralized logger that writes to both Logcat and app's external files directory.
 * Uses app-specific external directory (no permissions needed on Android 10+).
 * Path: /sdcard/Android/data/com.example.acctileman/files/acctileman_log.txt
 * Also copies to /sdcard/Download/ when possible.
 */
public class Logger {

    private static final String TAG = "AccTileMan";
    private static final String LOG_FILE_NAME = "acctileman_log.txt";

    private static Context appContext;

    public static void init(Context ctx) {
        if (appContext == null && ctx != null) {
            appContext = ctx.getApplicationContext();
        }
    }

    private static File getLogFile() {
        if (appContext != null) {
            File dir = appContext.getExternalFilesDir(null);
            if (dir != null) {
                return new File(dir, LOG_FILE_NAME);
            }
        }
        // Fallback: app internal storage (always writable)
        return new File("/data/data/com.example.acctileman/files/" + LOG_FILE_NAME);
    }

    private static synchronized void writeLog(String level, String tag, String msg) {
        String timestamp = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String line = timestamp + " " + level + "/" + tag + ": " + msg + "\n";

        // Logcat
        switch (level) {
            case "E": Log.e(TAG, tag + ": " + msg); break;
            case "W": Log.w(TAG, tag + ": " + msg); break;
            case "I": Log.i(TAG, tag + ": " + msg); break;
            default:  Log.d(TAG, tag + ": " + msg); break;
        }

        // File
        File logFile = getLogFile();
        try {
            File dir = logFile.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(line);
            fw.close();
        } catch (Exception e) {
            Log.e(TAG, "Logger: 写入日志失败 path=" + logFile.getAbsolutePath() + " err=" + e.getMessage());
        }
    }

    public static void d(String tag, String msg) { writeLog("D", tag, msg); }
    public static void i(String tag, String msg) { writeLog("I", tag, msg); }
    public static void w(String tag, String msg) { writeLog("W", tag, msg); }
    public static void e(String tag, String msg) { writeLog("E", tag, msg); }
    public static void e(String tag, String msg, Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println(msg);
        t.printStackTrace(pw);
        pw.close();
        writeLog("E", tag, sw.toString());
    }

    /** Clear the log file */
    public static synchronized void clear() {
        File logFile = getLogFile();
        try {
            new FileWriter(logFile, false).close();
        } catch (Exception ignored) {}
    }

    /** Get the log file path for export */
    public static String getLogPath() {
        return getLogFile().getAbsolutePath();
    }

    /** Get log file size */
    public static long getLogSize() {
        File f = getLogFile();
        return f.exists() ? f.length() : 0;
    }
}