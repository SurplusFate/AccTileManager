package com.example.acctileman;

import android.content.pm.PackageManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Safe Shizuku wrapper using reflection.
 * With classes2.dex providing moe.shizuku.server.* classes, Shizuku can fully initialize.
 */
public class ShizukuHelper {

    private static Class<?> shizukuClass = null;
    private static Method checkSelfPermissionMethod = null;
    private static Method requestPermissionMethod = null;
    private static Method addListenerMethod = null;
    private static Method removeListenerMethod = null;
    private static Class<?> listenerClass = null;
    private static boolean initAttempted = false;

    private static synchronized void ensureInit() {
        if (initAttempted) return;
        initAttempted = true;

        Logger.d("ShizukuHelper", "=== 开始初始化 ShizukuHelper ===");

        // Step 1: Load Shizuku class (allow full initialization - we have classes2.dex now)
        try {
            shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Logger.d("ShizukuHelper", "Shizuku 类加载成功");
        } catch (ClassNotFoundException e) {
            Logger.e("ShizukuHelper", "Shizuku 类未找到 (ClassNotFoundException)", e);
            shizukuClass = null;
            return;
        } catch (NoClassDefFoundError e) {
            Logger.e("ShizukuHelper", "Shizuku 类加载失败 (NoClassDefFoundError) - 缺少依赖类", e);
            shizukuClass = null;
            return;
        } catch (Throwable t) {
            Logger.e("ShizukuHelper", "Shizuku 类加载失败 (未知错误)", t);
            shizukuClass = null;
            return;
        }

        // Step 2: Get methods
        try {
            checkSelfPermissionMethod = shizukuClass.getMethod("checkSelfPermission");
            Logger.d("ShizukuHelper", "checkSelfPermission 方法获取成功");
        } catch (Throwable t) {
            Logger.e("ShizukuHelper", "checkSelfPermission 方法获取失败", t);
        }

        try {
            requestPermissionMethod = shizukuClass.getMethod("requestPermission", int.class);
            Logger.d("ShizukuHelper", "requestPermission 方法获取成功");
        } catch (Throwable t) {
            Logger.e("ShizukuHelper", "requestPermission 方法获取失败", t);
        }

        // Step 3: Load listener interface
        try {
            listenerClass = Class.forName("rikka.shizuku.Shizuku$OnRequestPermissionResultListener");
            addListenerMethod = shizukuClass.getMethod("addRequestPermissionResultListener", listenerClass);
            removeListenerMethod = shizukuClass.getMethod("removeRequestPermissionResultListener", listenerClass);
            Logger.d("ShizukuHelper", "Listener 接口和方法获取成功");
        } catch (Throwable t) {
            Logger.e("ShizukuHelper", "Listener 方法获取失败", t);
        }

        Logger.d("ShizukuHelper", "=== ShizukuHelper 初始化完成 ===");
        Logger.d("ShizukuHelper", "  shizukuClass=" + (shizukuClass != null));
        Logger.d("ShizukuHelper", "  checkSelfPermission=" + (checkSelfPermissionMethod != null));
        Logger.d("ShizukuHelper", "  requestPermission=" + (requestPermissionMethod != null));
        Logger.d("ShizukuHelper", "  listenerClass=" + (listenerClass != null));
    }

    public static boolean isAvailable() {
        ensureInit();
        return shizukuClass != null;
    }

    public static boolean checkSelfPermission() {
        ensureInit();
        if (checkSelfPermissionMethod == null) {
            Logger.w("ShizukuHelper", "checkSelfPermission: 方法不可用");
            return false;
        }
        try {
            Logger.d("ShizukuHelper", "checkSelfPermission: 调用中...");
            int result = (Integer) checkSelfPermissionMethod.invoke(null);
            Logger.d("ShizukuHelper", "checkSelfPermission: 结果=" + result + " (GRANTED=" + PackageManager.PERMISSION_GRANTED + ")");
            return result == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            Logger.e("ShizukuHelper", "checkSelfPermission: 调用失败", t);
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        ensureInit();
        Logger.d("ShizukuHelper", "requestPermission: 开始, requestCode=" + requestCode);
        if (requestPermissionMethod == null) {
            Logger.e("ShizukuHelper", "requestPermission: 方法不可用，无法请求权限");
            return;
        }
        try {
            Logger.d("ShizukuHelper", "requestPermission: 调用 Shizuku.requestPermission(" + requestCode + ")...");
            requestPermissionMethod.invoke(null, requestCode);
            Logger.d("ShizukuHelper", "requestPermission: 调用完成，等待用户响应");
        } catch (Throwable t) {
            Logger.e("ShizukuHelper", "requestPermission: 调用失败", t);
        }
    }

    public static Object createListener(final PermissionCallback callback) {
        ensureInit();
        if (listenerClass == null) {
            Logger.w("ShizukuHelper", "createListener: listenerClass 不可用");
            return null;
        }
        try {
            Object proxy = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            Logger.d("ShizukuHelper", "Listener 回调: method=" + method.getName());
                            if ("onRequestPermissionResult".equals(method.getName()) && args != null && args.length == 2) {
                                int rc = (Integer) args[0];
                                int gr = (Integer) args[1];
                                Logger.d("ShizukuHelper", "权限结果回调: requestCode=" + rc + " grantResult=" + gr);
                                callback.onResult(rc, gr);
                            }
                            return null;
                        }
                    }
            );
            Logger.d("ShizukuHelper", "createListener: 代理创建成功");
            return proxy;
        } catch (Throwable t) {
            Logger.e("ShizukuHelper", "createListener: 失败", t);
            return null;
        }
    }

    public static void addRequestPermissionResultListener(Object listener) {
        if (addListenerMethod == null || listener == null) {
            Logger.w("ShizukuHelper", "addListener: 方法或listener为空");
            return;
        }
        try {
            Logger.d("ShizukuHelper", "addListener: 注册监听器...");
            addListenerMethod.invoke(null, listener);
            Logger.d("ShizukuHelper", "addListener: 注册成功");
        } catch (Throwable t) {
            Logger.e("ShizukuHelper", "addListener: 失败", t);
        }
    }

    public static void removeRequestPermissionResultListener(Object listener) {
        if (removeListenerMethod == null || listener == null) return;
        try {
            Logger.d("ShizukuHelper", "removeListener: 移除监听器");
            removeListenerMethod.invoke(null, listener);
        } catch (Throwable t) {
            Logger.e("ShizukuHelper", "removeListener: 失败", t);
        }
    }

    public interface PermissionCallback {
        void onResult(int requestCode, int grantResult);
    }
}