package com.example.acctileman;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Centralized logger that writes to both Logcat and a file on sdcard.
 * Log file: /sdcard/Download/acctileman_log.txt
 */
public class Logger {

    private static final String TAG = "AccTileMan";
    private static final String LOG_FILE_NAME = "acctileman_log.txt";

    private static File getLogFile() {
        return new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                LOG_FILE_NAME);
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
        try {
            FileWriter fw = new FileWriter(getLogFile(), true);
            fw.write(line);
            fw.close();
        } catch (Exception ignored) {}
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
        try {
            new FileWriter(getLogFile(), false).close();
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