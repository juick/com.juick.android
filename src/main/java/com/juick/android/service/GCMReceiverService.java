package com.juick.android.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.bluelinelabs.logansquare.LoganSquare;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.juick.App;
import com.juick.R;
import com.juick.api.RestClient;
import com.juick.api.model.Post;
import com.juick.android.MainActivity;
import com.google.android.gms.gcm.GcmListenerService;

/**
 * Created by vt on 03/12/15.
 */
public class GCMReceiverService extends GcmListenerService {

    public final static String GCM_EVENT_ACTION = GCMReceiverService.class.getName() + "_GCM_EVENT_ACTION";

    private static final String CHANNEL_ID = "default";

    private static NotificationManager notificationManager =
            (NotificationManager) App.getInstance().getSystemService(Context.NOTIFICATION_SERVICE);

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        NotificationChannel channel =  notificationManager.getNotificationChannel(CHANNEL_ID);
        if (channel == null) {
            channel = new NotificationChannel(CHANNEL_ID,
                    "Juick",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Juick notifications");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onMessageReceived(String from, Bundle data) {
        super.onMessageReceived(from, data);
        String msg = data.getString("message");
        Log.d("GCMReceiverService", "onMessageReceived " + data.toString());
        showNotification(msg);
    }

    public static void showNotification(final String msgStr) {
        try {
            final Post jmsg = LoganSquare.parse(msgStr, Post.class);
            String title = "@" + jmsg.user.uname;
            if (!jmsg.tags.isEmpty()) {
                title += ": " + jmsg.getTagsString();
            }
            String body;
            if (jmsg.body.length() > 64) {
                body = jmsg.body.substring(0, 60) + "...";
            } else {
                body = jmsg.body;
            }

            PendingIntent contentIntent = PendingIntent.getActivity(App.getInstance(), getId(jmsg), getIntent(msgStr, jmsg), PendingIntent.FLAG_UPDATE_CURRENT);
            final NotificationCompat.Builder notificationBuilder = Build.VERSION.SDK_INT < 26 ?
                    new NotificationCompat.Builder(App.getInstance()) : new NotificationCompat.Builder(App.getInstance(), CHANNEL_ID);
            notificationBuilder.setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true).setWhen(0)
                    .setContentIntent(contentIntent)
                    .setGroup("messages")
                    .setGroupSummary(true);
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_MESSAGE);
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(jmsg.body));

            final RequestOptions options = new RequestOptions().centerCrop();
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Glide.with(App.getInstance()).asBitmap()
                            .load(RestClient.getImagesUrl() + "a/" + jmsg.user.uid + ".png")
                            .apply(options)
                            .into(new SimpleTarget<Bitmap>() {
                                @Override
                                public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                                    notificationBuilder.setLargeIcon(resource);
                                    if (Build.VERSION.SDK_INT < 26) {
                                        notificationBuilder.setDefaults(~(Notification.DEFAULT_LIGHTS
                                                | Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND));
                                    }
                                    GCMReceiverService.notify(jmsg, notificationBuilder);
                                }
                            });

                    notificationBuilder.addAction(Build.VERSION.SDK_INT <= 19 ? R.drawable.ic_ab_reply2 : R.drawable.ic_ab_reply,
                            App.getInstance().getString(R.string.reply), PendingIntent.getActivity(App.getInstance(), getId(jmsg), getIntent(msgStr, jmsg), PendingIntent.FLAG_UPDATE_CURRENT));
                    GCMReceiverService.notify(jmsg, notificationBuilder);
                }
            });

        } catch (Exception e) {
            Log.e("GCMIntentService", "GCM message error", e);
        }
    }

    private static void notify(Post jmsg, NotificationCompat.Builder notificationBuilder) {
        notificationManager.notify(getId(jmsg), notificationBuilder.build());
    }

    private static Integer getId(Post jmsg) {
        return jmsg.mid != 0 ? jmsg.mid + jmsg.rid : jmsg.user.uid;
    }

    public static Intent getIntent(String msgStr, Post jmsg) {
        Intent intent = new Intent(App.getInstance(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.setAction(MainActivity.PUSH_ACTION);
        intent.putExtra(MainActivity.ARG_UNAME, jmsg.user.uname);
        intent.putExtra(MainActivity.ARG_UID, jmsg.user.uid);
        if (jmsg.mid == 0) {
            LocalBroadcastManager.getInstance(App.getInstance()).sendBroadcast(new Intent(GCM_EVENT_ACTION).putExtra("message", msgStr));
            intent.putExtra(MainActivity.PUSH_ACTION_SHOW_PM, true);
        } else {
            intent.putExtra(MainActivity.ARG_MID, jmsg.mid);
            intent.putExtra(MainActivity.PUSH_ACTION_SHOW_THREAD, true);
        }
        return intent;
    }
}
