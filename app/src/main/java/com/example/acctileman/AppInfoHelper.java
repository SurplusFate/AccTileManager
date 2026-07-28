package com.example.acctileman;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.view.accessibility.AccessibilityServiceInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 读取设备上已安装应用及其无障碍服务信息。
 */
public class AppInfoHelper {

    private static final String TAG = "AppInfoHelper";

    /**
     * 表示一个已安装应用。
     */
    public static class AppItem {
        public String packageName;
        public String appName;
        public Drawable icon;
    }

    /**
     * 表示一个无障碍服务。
     */
    public static class ServiceItem {
        public String packageName;
        public String className;
        /** 完整的组件名，如 com.example/.MyService */
        public String component;
        /** 服务描述（取自 accessibility service 的 description） */
        public String description;
        /** 是否已启用 */
        public boolean enabled;
    }

    /** 获取所有已安装的第三方应用（排除系统应用），按名称排序 */
    public static List<AppItem> getInstalledApps(Context ctx) {
        Logger.d(TAG, "getInstalledApps: 开始读取");
        PackageManager pm = ctx.getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(0);
        List<AppItem> result = new ArrayList<>();

        for (PackageInfo pi : packages) {
            // 只显示有启动 Intent 的非系统应用
            if (isSystemApp(pi.applicationInfo)) continue;
            if (pm.getLaunchIntentForPackage(pi.packageName) == null) continue;

            AppItem item = new AppItem();
            item.packageName = pi.packageName;
            item.appName = pi.applicationInfo.loadLabel(pm).toString();
            item.icon = pi.applicationInfo.loadIcon(pm);
            result.add(item);
        }

        // 按名称排序
        Collections.sort(result, (a, b) -> a.appName.compareToIgnoreCase(b.appName));
        Logger.d(TAG, "getInstalledApps: 共 " + result.size() + " 个应用");
        return result;
    }

    /** 获取指定包名的所有无障碍服务 */
    public static List<ServiceItem> getAccessibilityServices(Context ctx, String packageName) {
        Logger.d(TAG, "getAccessibilityServices: " + packageName);
        PackageManager pm = ctx.getPackageManager();
        List<ServiceItem> result = new ArrayList<>();

        // 方法1: 通过 AccessibilityServiceInfo（系统已知的无障碍服务）
        List<AccessibilityServiceInfo> installedServices =
                android.view.accessibility.AccessibilityManager.getInstance(ctx)
                        .getInstalledAccessibilityServiceList(
                                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        for (AccessibilityServiceInfo asi : installedServices) {
            ServiceInfo si = asi.getResolveInfo().serviceInfo;
            if (si != null && si.packageName.equals(packageName)) {
                ServiceItem item = new ServiceItem();
                item.packageName = packageName;
                item.className = si.name;
                item.component = flattenComponent(packageName, si.name);
                item.description = asi.getDescription();
                item.enabled = isServiceEnabled(item.component);
                result.add(item);
            }
        }

        // 方法2: 通过解析 AndroidManifest（补充系统未索引到的服务）
        try {
            PackageInfo pkgInfo = pm.getPackageInfo(packageName,
                    PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
            if (pkgInfo.services != null) {
                for (ServiceInfo si : pkgInfo.services) {
                    if (hasAccessibilityServiceIntentFilter(si)) {
                        // 避免重复
                        String component = flattenComponent(packageName, si.name);
                        boolean exists = false;
                        for (ServiceItem existing : result) {
                            if (existing.component.equals(component)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            ServiceItem item = new ServiceItem();
                            item.packageName = packageName;
                            item.className = si.name;
                            item.component = component;
                            item.description = si.loadDescription(pm) != null ?
                                    si.loadDescription(pm).toString() : "";
                            item.enabled = isServiceEnabled(component);
                            result.add(item);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Logger.e(TAG, "getAccessibilityServices: 解析 manifest 失败", t);
        }

        Logger.d(TAG, "getAccessibilityServices: " + packageName + " 找到 " + result.size() + " 个服务");
        return result;
    }

    /** 检查系统已安装的无障碍服务中是否包含该包名的服务 */
    public static boolean hasAnyAccessibilityService(Context ctx, String packageName) {
        List<AccessibilityServiceInfo> installedServices =
                android.view.accessibility.AccessibilityManager.getInstance(ctx)
                        .getInstalledAccessibilityServiceList(
                                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo asi : installedServices) {
            ServiceInfo si = asi.getResolveInfo().serviceInfo;
            if (si != null && si.packageName.equals(packageName)) {
                return true;
            }
        }
        // 再通过 manifest 检查
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pkgInfo = pm.getPackageInfo(packageName,
                    PackageManager.GET_SERVICES);
            if (pkgInfo.services != null) {
                for (ServiceInfo si : pkgInfo.services) {
                    if (hasAccessibilityServiceIntentFilter(si)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isSystemApp(ApplicationInfo ai) {
        return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private static String flattenComponent(String pkg, String cls) {
        // 简写格式: 包名/.短类名
        if (cls.startsWith(pkg + ".")) {
            return pkg + "/" + cls.substring(pkg.length());
        }
        return pkg + "/" + cls;
    }

    private static boolean hasAccessibilityServiceIntentFilter(ServiceInfo si) {
        if (si.intentFilters != null) {
            for (android.content.IntentFilter filter : si.intentFilters) {
                if (filter.hasAction("android.accessibilityservice.AccessibilityService")) {
                    return true;
                }
            }
        }
        // 有些无障碍服务通过 meta-data 而非 intent-filter 声明
        if (si.metaData != null && si.metaData.containsKey("android.accessibilityservice")) {
            return true;
        }
        return false;
    }

    private static boolean isServiceEnabled(String component) {
        String enabled = ShellHelper.getEnabledServices();
        return enabled != null && enabled.contains(component);
    }
}
