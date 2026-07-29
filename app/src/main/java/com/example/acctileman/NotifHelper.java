package com.example.acctileman;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/**
 * 通知提示辅助类。
 * Android 12+ 后台 Toast 被限制，改用通知显示操作反馈。
 */
public class NotifHelper {

    private static final String CHANNEL_ID = "tile_feedback";
    private static final String CHANNEL_NAME = "磁贴操作提示";
    private static final int NOTIF_ID = 9001;

    private static boolean channelCreated = false;

    /** 显示一条操作反馈通知，3 秒后自动取消 */
    public static void showFeedback(Context ctx, String title, String message) {
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                Logger.e("NotifHelper", "NotificationManager 为 null");
                return;
            }

            // 创建通知渠道（Android 8.0+ 需要）
            if (!channelCreated) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(
                            CHANNEL_ID, CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_LOW); // 低优先级，不发声
                    channel.setDescription("磁贴操作反馈");
                    channel.setShowBadge(false);
                    nm.createNotificationChannel(channel);
                }
                channelCreated = true;
            }

            // 构建通知
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(ctx, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(ctx);
            }
            builder.setSmallIcon(android.R.drawable.ic_dialog_info);
            builder.setContentTitle(title);
            builder.setContentText(message);
            builder.setStyle(new Notification.BigTextStyle().bigText(message));
            builder.setAutoCancel(true);
            builder.setPriority(Notification.PRIORITY_LOW);
            builder.setOngoing(false);

            Notification notif = builder.build();

            // 显示通知
            nm.notify(NOTIF_ID, notif);
            Logger.d("NotifHelper", "已显示通知: " + title + " | " + message.replace("\n", " "));

            // 3 秒后自动取消
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        nm.cancel(NOTIF_ID);
                        Logger.d("NotifHelper", "通知已自动取消");
                    } catch (Throwable ignored) {}
                }
            }, 3000);

        } catch (Throwable t) {
            Logger.e("NotifHelper", "显示通知失败", t);
        }
    }

    /** 取消通知 */
    public static void cancel(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(NOTIF_ID);
            }
        } catch (Throwable ignored) {}
    }
}
