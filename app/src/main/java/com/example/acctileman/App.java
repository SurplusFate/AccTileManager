package com.example.acctileman;

import android.app.Application;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Custom Application class.
 * - Initializes Logger with app context
 * - Captures all uncaught exceptions and writes to Logger + crash file
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.init(this);
        Logger.d("App", "=== App onCreate ===");
        Logger.d("App", "日志路径: " + Logger.getLogPath());

        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                try {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    pw.println("===== CRASH " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + " =====");
                    pw.println("Thread: " + thread.getName());
                    pw.println("Exception: " + throwable.getClass().getName());
                    pw.println("Message: " + throwable.getMessage());
                    pw.println("Stacktrace:");
                    throwable.printStackTrace(pw);

                    Throwable cause = throwable.getCause();
                    while (cause != null) {
                        pw.println("\nCaused by: " + cause.getClass().getName() + ": " + cause.getMessage());
                        cause.printStackTrace(pw);
                        cause = cause.getCause();
                    }
                    pw.println("===== END =====\n");
                    pw.close();

                    // 写入 Logger（会写到 app 外部目录）
                    Logger.e("CRASH", sw.toString());

                    // 同时尝试写到 /sdcard/Download/ 崩溃日志（可能失败没关系）
                    try {
                        File downloadsDir = getExternalFilesDir(null);
                        if (downloadsDir != null) {
                            FileWriter fw = new FileWriter(new File(downloadsDir, "acctileman_crash.log"), true);
                            fw.write(sw.toString());
                            fw.close();
                        }
                    } catch (Exception ignored) {}
                } catch (Exception ignored) {}

                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            }
        });
        Logger.d("App", "=== App onCreate 完成 ===");
    }
}