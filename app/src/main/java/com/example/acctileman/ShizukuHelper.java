package com.example.acctileman;

import android.content.pm.PackageManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Safe Shizuku wrapper using reflection.
 * Gracefully degrades if Shizuku is not installed or not running.
 */
public class ShizukuHelper {

    private static final String TAG = "ShizukuHelper";

    private static Class<?> shizukuClass;
    private static Method checkSelfPermissionMethod;
    private static Method requestPermissionMethod;
    private static Method addRequestPermissionResultListenerMethod;
    private static Method removeRequestPermissionResultListenerMethod;
    private static Class<?> listenerClass;

    static {
        init();
    }

    private static void init() {
        try {
            // Use initialize=false to avoid triggering static initializer
            // which requires IShizukuApplication$Stub (not in our DEX)
            shizukuClass = Class.forName("rikka.shizuku.Shizuku", false,
                    ShizukuHelper.class.getClassLoader());
            checkSelfPermissionMethod = shizukuClass.getMethod("checkSelfPermission");
            requestPermissionMethod = shizukuClass.getMethod("requestPermission", int.class);
            // Load listener class without initializing
            listenerClass = Class.forName("rikka.shizuku.Shizuku$OnRequestPermissionResultListener",
                    false, ShizukuHelper.class.getClassLoader());
            addRequestPermissionResultListenerMethod = shizukuClass.getMethod(
                    "addRequestPermissionResultListener", listenerClass);
            removeRequestPermissionResultListenerMethod = shizukuClass.getMethod(
                    "removeRequestPermissionResultListener", listenerClass);
        } catch (Throwable t) {
            // Shizuku library not available or missing dependencies
            shizukuClass = null;
        }
    }

    public static boolean isAvailable() {
        return shizukuClass != null;
    }

    public static boolean checkSelfPermission() {
        if (checkSelfPermissionMethod == null) return false;
        try {
            int result = (Integer) checkSelfPermissionMethod.invoke(null);
            return result == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        if (requestPermissionMethod == null) return;
        try {
            requestPermissionMethod.invoke(null, requestCode);
        } catch (Throwable t) {
            // Ignore
        }
    }

    public static Object createListener(final PermissionCallback callback) {
        if (listenerClass == null) return null;
        try {
            return Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("onRequestPermissionResult".equals(method.getName()) && args != null && args.length == 2) {
                                callback.onResult((Integer) args[0], (Integer) args[1]);
                            }
                            return null;
                        }
                    }
            );
        } catch (Throwable t) {
            return null;
        }
    }

    public static void addRequestPermissionResultListener(Object listener) {
        if (addRequestPermissionResultListenerMethod == null || listener == null) return;
        try {
            addRequestPermissionResultListenerMethod.invoke(null, listener);
        } catch (Throwable t) {
            // Ignore
        }
    }

    public static void removeRequestPermissionResultListener(Object listener) {
        if (removeRequestPermissionResultListenerMethod == null || listener == null) return;
        try {
            removeRequestPermissionResultListenerMethod.invoke(null, listener);
        } catch (Throwable t) {
            // Ignore
        }
    }

    public interface PermissionCallback {
        void onResult(int requestCode, int grantResult);
    }
}
