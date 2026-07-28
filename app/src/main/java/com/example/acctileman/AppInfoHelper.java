package com.example.acctileman;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 读取设备上已安装应用及其无障碍服务信息。
 * 全部使用 PackageManager 标准 API，避免依赖 AccessibilityServiceInfo 等可能在精简 SDK 中不可用的类。
 */
public class AppInfoHelper {

    private static final String TAG = "AppInfoHelper";
    private static final String ACTION_ACCESSIBILITY_SERVICE = "android.accessibilityservice.AccessibilityService";
    private static final String META_DATA_ACCESSIBILITY_SERVICE = "android.accessibilityservice";

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
        /** 服务描述 */
        public String description;
        /** 是否已启用 */
        public boolean enabled;
    }

    /**
     * 表示一个 Deep Link（URI/Intent）。
     */
    public static class DeepLinkItem {
        /** 完整 URI，如 taobao://item.taobao.com/item.html?id=123 */
        public String uri;
        /** scheme，如 taobao */
        public String scheme;
        /** host，如 item.taobao.com */
        public String host;
        /** 来源 Activity 名称 */
        public String activityName;
        /** 显示用的简短描述 */
        public String label;
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

        try {
            // 方法: 通过 queryIntentServices 查询声明了无障碍服务 action 的服务
            Intent queryIntent = new Intent(ACTION_ACCESSIBILITY_SERVICE);
            queryIntent.setPackage(packageName);
            List<ResolveInfo> resolveInfos = pm.queryIntentServices(
                    queryIntent, PackageManager.GET_META_DATA);

            for (ResolveInfo ri : resolveInfos) {
                ServiceInfo si = ri.serviceInfo;
                if (si == null) continue;

                String component = flattenComponent(packageName, si.name);
                // 去重
                boolean exists = false;
                for (ServiceItem existing : result) {
                    if (existing.component.equals(component)) {
                        exists = true;
                        break;
                    }
                }
                if (exists) continue;

                ServiceItem item = new ServiceItem();
                item.packageName = packageName;
                item.className = si.name;
                item.component = component;
                // 尝试从 meta-data 或 label 获取描述
                item.description = getServiceDescription(pm, si, ri);
                item.enabled = isServiceEnabled(component);
                result.add(item);
            }
        } catch (Throwable t) {
            Logger.e(TAG, "getAccessibilityServices: queryIntentServices 失败", t);
        }

        // 补充: 通过解析 PackageInfo 中的 services 列表（有些服务可能未正确注册 intent-filter）
        try {
            PackageInfo pkgInfo = pm.getPackageInfo(packageName,
                    PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
            if (pkgInfo.services != null) {
                for (ServiceInfo si : pkgInfo.services) {
                    if (hasAccessibilityMetaData(si)) {
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
                            item.description = getServiceDescription(pm, si, null);
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

    /** 检查指定包名是否包含任何无障碍服务 */
    public static boolean hasAnyAccessibilityService(Context ctx, String packageName) {
        PackageManager pm = ctx.getPackageManager();
        try {
            Intent queryIntent = new Intent(ACTION_ACCESSIBILITY_SERVICE);
            queryIntent.setPackage(packageName);
            List<ResolveInfo> resolveInfos = pm.queryIntentServices(queryIntent, 0);
            if (resolveInfos != null && !resolveInfos.isEmpty()) {
                return true;
            }
        } catch (Throwable ignored) {}

        // 再通过 manifest 检查 meta-data
        try {
            PackageInfo pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
            if (pkgInfo.services != null) {
                for (ServiceInfo si : pkgInfo.services) {
                    if (hasAccessibilityMetaData(si)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * 获取指定 APP 声明的所有 Deep Link（URI scheme）。
     * 通过 PackageManager 标准 API 查询，不需要特权。
     */
    public static List<DeepLinkItem> getDeepLinks(Context ctx, String packageName) {
        Logger.d(TAG, "getDeepLinks: " + packageName);
        List<DeepLinkItem> result = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();

        // 方法1: queryIntentActivities 查询 VIEW intent（不带 category 限制）
        try {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setPackage(packageName);
            List<ResolveInfo> activities = pm.queryIntentActivities(
                    viewIntent, PackageManager.GET_RESOLVED_FILTER);

            for (ResolveInfo ri : activities) {
                if (ri.filter == null) continue;
                // 遍历所有 scheme
                for (int i = 0; i < ri.filter.countDataSchemes(); i++) {
                    String scheme = ri.filter.getDataScheme(i);
                    if (scheme == null) continue;
                    // 排除常见的非 deep link scheme
                    if (scheme.equals("http") || scheme.equals("https")
                            || scheme.equals("content") || scheme.equals("file")
                            || scheme.equals("package")) {
                        continue;
                    }

                    DeepLinkItem item = new DeepLinkItem();
                    item.scheme = scheme;
                    item.uri = scheme + "://";
                    item.activityName = ri.activityInfo != null ? ri.activityInfo.name : "";
                    item.label = scheme + "://";
                    addDeepLinkIfNotExists(result, item);
                }
                // 遍历所有 authority (host)，构建更完整的 URI
                for (int i = 0; i < ri.filter.countDataAuthorities(); i++) {
                    String host = ri.filter.getDataAuthority(i).getHost();
                    if (host == null) continue;
                    for (int j = 0; j < ri.filter.countDataSchemes(); j++) {
                        String scheme = ri.filter.getDataScheme(j);
                        if (scheme == null) continue;
                        if (scheme.equals("http") || scheme.equals("https")
                                || scheme.equals("content") || scheme.equals("file")
                                || scheme.equals("package")) {
                            continue;
                        }

                        DeepLinkItem item = new DeepLinkItem();
                        item.scheme = scheme;
                        item.host = host;
                        item.uri = scheme + "://" + host;
                        item.activityName = ri.activityInfo != null ? ri.activityInfo.name : "";
                        item.label = scheme + "://" + host;
                        addDeepLinkIfNotExists(result, item);
                    }
                }
            }
        } catch (Throwable t) {
            Logger.e(TAG, "getDeepLinks: queryIntentActivities 失败", t);
        }

        // 方法2: 日志记录已通过方法1获取的结果
        Logger.d(TAG, "getDeepLinks: 方法1(queryIntentActivities) 获取到 " + result.size() + " 个");

        // 方法3: 查询带 CATEGORY_BROWSABLE 的（补充 http/https 类的链接）
        try {
            Intent browsableIntent = new Intent(Intent.ACTION_VIEW);
            browsableIntent.setPackage(packageName);
            browsableIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            List<ResolveInfo> browsableActivities = pm.queryIntentActivities(
                    browsableIntent, PackageManager.GET_RESOLVED_FILTER);

            for (ResolveInfo ri : browsableActivities) {
                if (ri.filter == null) continue;
                for (int i = 0; i < ri.filter.countDataSchemes(); i++) {
                    String scheme = ri.filter.getDataScheme(i);
                    if (scheme == null) continue;
                    // 这里只补充 http/https
                    if (!scheme.equals("http") && !scheme.equals("https")) {
                        continue;
                    }
                    // 获取 host
                    String host = "";
                    for (int j = 0; j < ri.filter.countDataAuthorities(); j++) {
                        String h = ri.filter.getDataAuthority(j).getHost();
                        if (h != null && !h.isEmpty()) {
                            host = h;
                            break;
                        }
                    }

                    DeepLinkItem item = new DeepLinkItem();
                    item.scheme = scheme;
                    item.host = host;
                    item.uri = host.isEmpty() ? scheme + "://" : scheme + "://" + host;
                    item.activityName = ri.activityInfo != null ? ri.activityInfo.name : "";
                    item.label = item.uri;
                    addDeepLinkIfNotExists(result, item);
                }
            }
        } catch (Throwable t) {
            Logger.e(TAG, "getDeepLinks: 查询 BROWSABLE 失败", t);
        }

        Logger.d(TAG, "getDeepLinks: " + packageName + " 找到 " + result.size() + " 个 Deep Link");
        return result;
    }

    private static void addDeepLinkIfNotExists(List<DeepLinkItem> list, DeepLinkItem item) {
        for (DeepLinkItem existing : list) {
            if (existing.uri.equals(item.uri)) {
                return;
            }
        }
        list.add(item);
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

    /** 检查 ServiceInfo 是否包含无障碍服务相关的 meta-data */
    private static boolean hasAccessibilityMetaData(ServiceInfo si) {
        if (si.metaData != null) {
            if (si.metaData.containsKey(META_DATA_ACCESSIBILITY_SERVICE)) {
                return true;
            }
        }
        return false;
    }

    /** 尝试获取服务的描述文本 */
    private static String getServiceDescription(PackageManager pm, ServiceInfo si, ResolveInfo ri) {
        try {
            // 先从 ResolveInfo 的 loadLabel 尝试
            if (ri != null && ri.loadLabel(pm) != null) {
                String label = ri.loadLabel(pm).toString();
                if (!label.isEmpty() && !label.equals(si.name)) {
                    return label;
                }
            }
            // 再从 ServiceInfo 的 loadLabel 尝试
            CharSequence label = si.loadLabel(pm);
            if (label != null && label.length() > 0 && !label.toString().equals(si.name)) {
                return label.toString();
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static boolean isServiceEnabled(String component) {
        String enabled = ShellHelper.getEnabledServices();
        return enabled != null && enabled.contains(component);
    }
}
