package com.example.acctileman;

import android.app.Application;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Custom Application class that captures all uncaught exceptions
 * and writes them to /sdcard/Download/acctileman_crash.log
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

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

                    // Print all causes
                    Throwable cause = throwable.getCause();
                    while (cause != null) {
                        pw.println("\nCaused by: " + cause.getClass().getName() + ": " + cause.getMessage());
                        cause.printStackTrace(pw);
                        cause = cause.getCause();
                    }
                    pw.println("===== END =====\n");
                    pw.close();

                    String dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
                    File logFile = new File(dir, "acctileman_crash.log");
                    FileWriter fw = new FileWriter(logFile, true);
                    fw.write(sw.toString());
                    fw.close();
                } catch (Exception ignored) {}

                // Let the default handler do its thing (show the crash dialog)
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            }
        });
    }
}
