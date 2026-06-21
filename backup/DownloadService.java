package com.oldmarket.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;

import com.oldmarket.R;
import com.oldmarket.ui.MainActivity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;

public class DownloadService extends Service {
    public static final String ACTION_START = "com.oldmarket.DOWNLOAD_START";
    public static final String ACTION_CANCEL = "com.oldmarket.DOWNLOAD_CANCEL";
    public static final String ACTION_PROGRESS = "com.oldmarket.DOWNLOAD_PROGRESS";

    private volatile boolean cancel = false;
    private int currentAppId = -1;
    private int lastPercent = 0;
    private long lastNotifyTime = 0L;
    private long lastUiSpeed = 0L;

    public IBinder onBind(Intent intent) { return null; }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();

        if (ACTION_CANCEL.equals(action)) {
            int id = intent.getIntExtra("app_id", -1);
            if (id == currentAppId) cancel = true;
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            final int appId = intent.getIntExtra("app_id", -1);
            final String url = intent.getStringExtra("url");
            final String fileName = intent.getStringExtra("file_name");

            if (appId < 0 || url == null || url.length() == 0) return START_NOT_STICKY;

            currentAppId = appId;
            cancel = false;

            new Thread(new Runnable() {
                public void run() {
                    runDownload(appId, url, fileName);
                }
            }).start();
        }

        return START_NOT_STICKY;
    }

    private File outFile(String fileName) {
        File dir;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            dir = new File(Environment.getExternalStorageDirectory(), "OldMarket");
        } else {
            dir = getCacheDir();
        }
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, fileName);
    }

    // —оздание уведомлени€ без Notification.Builder (чтобы не было Lint "requires API 11")
    @SuppressWarnings("deprecation")
    private Notification makeNotification(Context c, String title, String text, boolean ongoing) {
        long when = System.currentTimeMillis();

        int icon = ongoing ? android.R.drawable.stat_sys_download
                           : android.R.drawable.stat_sys_download_done;

        Notification n = new Notification(icon, text, when);
        n.flags = (ongoing ? Notification.FLAG_ONGOING_EVENT : 0) | Notification.FLAG_ONLY_ALERT_ONCE;

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pi = PendingIntent.getActivity(c, 0, open, 0);

        // setLatestEventInfo вырезан из SDK stubs на новых API, поэтому вызываем через reflection
        try {
            Method m = n.getClass().getMethod(
                    "setLatestEventInfo",
                    Context.class,
                    CharSequence.class,
                    CharSequence.class,
                    PendingIntent.class
            );
            m.invoke(n, c, title, text, pi);
        } catch (Exception e) {
            // ≈сли вдруг не получилось Ч просто не заполним контент, но не упадЄм
        }

        return n;
    }

    @SuppressWarnings("deprecation")
    private void notifyProgress(NotificationManager nm, int notifId, String title, String text, boolean ongoing) {
        Notification n = makeNotification(this, title, text, ongoing);
        n.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
        nm.notify(notifId, n);

        if (ongoing) {
            try { startForeground(notifId, n); } catch (Exception e) { }
        }
    }

    private void runDownload(int appId, String urlStr, String fileName) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        int notifId = 1000 + appId;
        currentAppId = appId;
        cancel = false;
        lastPercent = 0;
        lastNotifyTime = 0L;
        lastUiSpeed = 0L;

        notifyProgress(nm, notifId, getString(R.string.downloading), "0%", true);

        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;

        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(30000);
            conn.connect();

            int len = conn.getContentLength();
            in = conn.getInputStream();

            File f = outFile(fileName);
            out = new FileOutputStream(f);

            byte[] buf = new byte[8192];
            long total = 0;

            long speedWindowStartTime = System.currentTimeMillis();
            long speedWindowStartBytes = 0;

            int r;
            while ((r = in.read(buf)) != -1) {
                if (cancel) throw new RuntimeException("cancel");

                out.write(buf, 0, r);
                total += r;

                int percent = (len > 0) ? (int) (total * 100 / len) : 0;

                // не даЄм прогрессу идти назад
                if (percent < lastPercent) {
                    percent = lastPercent;
                } else {
                    lastPercent = percent;
                }

                long now = System.currentTimeMillis();
                long dt = now - speedWindowStartTime;
                long db = total - speedWindowStartBytes;

                long speed = 0;
                if (dt > 0) {
                    speed = (db * 1000L) / dt;
                    lastUiSpeed = speed;
                }

                // обновл€ть UI/уведомление не чаще чем раз в 800 мс
                if ((now - lastNotifyTime) >= 800 || percent >= 100) {
                    lastNotifyTime = now;

                    String speedText;
                    if (lastUiSpeed >= 1024 * 1024) {
                        speedText = String.format("%.1f MB/s", (lastUiSpeed / 1024f / 1024f));
                    } else {
                        speedText = (lastUiSpeed / 1024) + " KB/s";
                    }

                    String text = percent + "%  Х  " + speedText;
                    notifyProgress(nm, notifId, getString(R.string.downloading), text, true);

                    Intent p = new Intent(ACTION_PROGRESS);
                    p.putExtra("app_id", appId);
                    p.putExtra("percent", percent);
                    p.putExtra("speed_bps", lastUiSpeed);
                    p.putExtra("done", false);
                    sendBroadcast(p);
                }

                // пересчитывать скорость окна раз в 1 секунду
                if (dt >= 1000) {
                    speedWindowStartTime = now;
                    speedWindowStartBytes = total;
                }
            }

            // done
            notifyProgress(nm, notifId, getString(R.string.downloading), getString(R.string.download_complete), false);

            Intent done = new Intent(ACTION_PROGRESS);
            done.putExtra("app_id", appId);
            done.putExtra("percent", 100);
            done.putExtra("speed_bps", 0);
            done.putExtra("done", true);
            done.putExtra("file_path", outFile(fileName).getAbsolutePath());
            sendBroadcast(done);

        } catch (Exception e) {
            Intent err = new Intent(ACTION_PROGRESS);
            err.putExtra("app_id", appId);
            err.putExtra("error", !cancel);
            err.putExtra("cancelled", cancel);
            err.putExtra("percent", lastPercent);
            err.putExtra("speed_bps", lastUiSpeed);
            sendBroadcast(err);

            if (cancel) {
                notifyProgress(nm, notifId, getString(R.string.downloading), getString(R.string.download_cancelled), false);
            }
        } finally {
            try { if (out != null) out.close(); } catch (Exception e) { }
            try { if (in != null) in.close(); } catch (Exception e) { }
            try { if (conn != null) conn.disconnect(); } catch (Exception e) { }

            try { stopForeground(true); } catch (Exception e) { }
            stopSelf();
        }
    }
}