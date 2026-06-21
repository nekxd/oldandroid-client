package com.oldmarket.ui;

import java.io.File;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import com.oldmarket.R;
import com.oldmarket.net.Api;
import com.oldmarket.net.Http;
import com.oldmarket.service.DownloadService;
import com.oldmarket.util.ImageLoader;
import com.oldmarket.util.LocaleHelper;
import com.oldmarket.util.Prefs;
import com.oldmarket.util.AndroidVersions;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class AppDetailActivity extends Activity {

    private int appId;

    // header views
    private View header;
    private ImageView imgIcon;
    private TextView txtName, txtAuthor, txtMeta, txtDesc;
    private Button btnInstall, btnOpen, btnUninstall;
    private TextView txtScreensTitle, txtReviewsTitle;
    private HorizontalScrollView screensScroll;
    private LinearLayout screensContainer;
    private Button btnAddReview;

    // list
    private ListView list;
    private ArrayList<ReviewItem> reviews = new ArrayList<ReviewItem>();
    private ReviewAdapter adapter;

    private String pkgName = "";
    private String selectedVersion = "";

    private ProgressDialog dlDialog;
    private View loadingOverlay;
    private TextView txtLoading;

    // download progress receiver
    private BroadcastReceiver dlReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            int id = intent.getIntExtra("app_id", -1);
            if (id != appId) return;

            int percent = intent.getIntExtra("percent", 0);
            long speed = intent.getLongExtra("speed_bps", 0);
            boolean done = intent.getBooleanExtra("done", false);
            boolean error = intent.getBooleanExtra("error", false);
            boolean cancelled = intent.getBooleanExtra("cancelled", false);

            if (dlDialog != null) {
                dlDialog.setProgress(percent);
                String speedText;
                if (speed >= 1024 * 1024) {
                    speedText = String.format("%.1f MB/s", (speed / 1024f / 1024f));
                } else {
                    speedText = (speed / 1024) + " KB/s";
                }

                dlDialog.setMessage(getString(R.string.downloading) + " " + percent + "%\n" + speedText);
            }

            if (done) {
                if (dlDialog != null) { dlDialog.dismiss(); dlDialog = null; }
                String path = intent.getStringExtra("file_path");
                if (path != null) openInstaller(path);
            }

            if (error || cancelled) {
                if (dlDialog != null) { dlDialog.dismiss(); dlDialog = null; }
            }
        }
    };

    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LocaleHelper.applySavedLocale(this);
        setContentView(R.layout.activity_app_detail);

        appId = getIntent().getIntExtra("app_id", 0);

        loadingOverlay = findViewById(R.id.loadingOverlay);
        txtLoading = (TextView) findViewById(R.id.txtLoading);
        list = (ListView) findViewById(R.id.listReviews);

        header = LayoutInflater.from(this).inflate(R.layout.app_detail_header, list, false);
        list.addHeaderView(header, null, false);

        // bind header
        imgIcon = (ImageView) header.findViewById(R.id.imgIcon);
        txtName = (TextView) header.findViewById(R.id.txtName);
        txtAuthor = (TextView) header.findViewById(R.id.txtAuthor);
        txtMeta = (TextView) header.findViewById(R.id.txtMeta);
        txtDesc = (TextView) header.findViewById(R.id.txtDesc);

        btnInstall = (Button) findViewById(R.id.btnInstall);
        btnOpen = (Button) findViewById(R.id.btnOpen);
        btnUninstall = (Button) findViewById(R.id.btnUninstall);

        txtScreensTitle = (TextView) header.findViewById(R.id.txtScreensTitle);
        screensScroll = (HorizontalScrollView) header.findViewById(R.id.screensScroll);
        screensContainer = (LinearLayout) header.findViewById(R.id.screensContainer);

        txtReviewsTitle = (TextView) header.findViewById(R.id.txtReviewsTitle);
        btnAddReview = (Button) header.findViewById(R.id.btnAddReview);

        adapter = new ReviewAdapter();
        list.setAdapter(adapter);

        // open user profile by tap on review row (not header)
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - 1; // 0 is header
                if (idx < 0 || idx >= reviews.size()) return;
                ReviewItem r = reviews.get(idx);
                if (r.userId > 0) {
                    Intent i = new Intent(AppDetailActivity.this, UserProfileActivity.class);
                    i.putExtra("user_id", r.userId);
                    startActivity(i);
                }
            }
        });

        btnInstall.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { chooseVersionAndDownload(); }
        });
        btnOpen.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openApp(); }
        });
        btnUninstall.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { uninstallApp(); }
        });

        btnAddReview.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!Prefs.isLoggedIn(AppDetailActivity.this)) {
                    startActivity(new Intent(AppDetailActivity.this, LoginActivity.class));
                    return;
                }
                showAddReviewDialog();
            }
        });

        showLoading(true, getString(R.string.loading));
        loadDetails();
        loadScreenshots();
        loadReviews();
    }

    protected void onResume() {
        super.onResume();
        registerReceiver(dlReceiver, new IntentFilter(DownloadService.ACTION_PROGRESS));
        refreshInstalledButtons();
        loadReviews();
    }

    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(dlReceiver); } catch (Exception e) {}
    }

    // ---------- DETAILS ----------
    private void loadDetails() {
        showLoading(true, getString(R.string.loading));
        new AsyncTask<Void, Void, JSONObject>() {
            protected JSONObject doInBackground(Void... v) {
                try {
                    String url = Api.baseUrl(AppDetailActivity.this) + "/api/app/" + appId;
                    String s = Http.getString(url);
                    if (s == null) return null;
                    return new JSONObject(s);
                } catch (Exception e) { return null; }
            }
            protected void onPostExecute(JSONObject o) {
                showLoading(false, null);
                if (o == null) { msg(getString(R.string.error_network)); return; }

                String name = o.optString("name", "");
                String dev = o.optString("developer", o.optString("author", ""));
                String desc = o.optString("description", "");
                String icon = o.optString("icon", "");
                int api = o.optInt("api", 1);
                pkgName = o.optString("package", o.optString("package_name", ""));
                String version = o.optString("version", "");

                txtName.setText(name);
                txtAuthor.setText(dev);
                txtDesc.setText(desc);
                txtMeta.setText("Android " + AndroidVersions.apiToAndroid(api)
                        + "   Package: " + pkgName
                        + "   Version: " + version);

                if (icon != null && icon.length() > 0) {
                    ImageLoader.load(AppDetailActivity.this, Api.iconUrl(AppDetailActivity.this, icon),
                            imgIcon, R.drawable.icon_placeholder);
                } else {
                    imgIcon.setImageResource(R.drawable.icon_placeholder);
                }

                refreshInstalledButtons();

                // If app requires higher API than device -> disable install
                if (api > Build.VERSION.SDK_INT) {
                    btnInstall.setEnabled(false);
                    btnInstall.setText(getString(R.string.not_compatible));
                } else {
                    btnInstall.setEnabled(true);
                    btnInstall.setText(getString(R.string.install));
                }
            }
        }.execute();
    }

    // ---------- SCREENSHOTS ----------
    private void loadScreenshots() {
        new AsyncTask<Void, Void, JSONArray>() {
            protected JSONArray doInBackground(Void... v) {
                try {
                    String url = Api.baseUrl(AppDetailActivity.this) + "/api/app/" + appId + "/screenshots";
                    String s = Http.getString(url);
                    if (s == null) return null;
                    return new JSONArray(s);
                } catch (Exception e) { return null; }
            }
            protected void onPostExecute(JSONArray arr) {
                if (arr == null || arr.length() == 0) {
                    txtScreensTitle.setVisibility(View.GONE);
                    screensScroll.setVisibility(View.GONE);
                    return;
                }
                txtScreensTitle.setVisibility(View.VISIBLE);
                screensScroll.setVisibility(View.VISIBLE);

                screensContainer.removeAllViews();

                for (int i = 0; i < arr.length(); i++) {
                    String file = arr.optString(i, "");
                    if (file == null || file.length() == 0) continue;

                    ImageView iv = new ImageView(AppDetailActivity.this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(240, 400);
                    lp.rightMargin = 10;
                    iv.setLayoutParams(lp);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    screensContainer.addView(iv);

                    ImageLoader.load(AppDetailActivity.this, Api.screenshotUrl(AppDetailActivity.this, file),
                            iv, R.drawable.banner_placeholder);
                }
            }
        }.execute();
    }

    // ---------- REVIEWS ----------
    private void loadReviews() {
        new AsyncTask<Void, Void, Object>() {
            protected Object doInBackground(Void... v) {
                try {
                    int viewerId = Prefs.getUserId(AppDetailActivity.this);
                    String url = Api.appReviewsUrl(AppDetailActivity.this, appId, viewerId);
                    String s = Http.getString(url);
                    if (s == null) return "null response";

                    JSONArray arr = new JSONArray(s);
                    ArrayList<ReviewItem> out = new ArrayList<ReviewItem>();
                    for (int i = 0; i < arr.length(); i++) out.add(parseReview(arr.getJSONObject(i)));
                    return out;
                } catch (Exception e) {
                    return e.toString();
                }
            }

            @SuppressWarnings("unchecked")
            protected void onPostExecute(Object out) {
                if (out instanceof String) {
                    txtReviewsTitle.setText("Отзывы (0)");
                    return;
                }
                ArrayList<ReviewItem> listOut = (ArrayList<ReviewItem>) out;

                reviews.clear();
                reviews.addAll(listOut);
                txtReviewsTitle.setText("Отзывы (" + reviews.size() + ")");
                adapter.notifyDataSetChanged();
            }
        }.execute();
    }

    private ReviewItem parseReview(JSONObject r) {
        ReviewItem ri = new ReviewItem();
        ri.id = r.optInt("id", 0);
        ri.userId = r.optInt("user_id", 0);
        ri.username = r.optString("username", "User");
        ri.avatar = r.optString("avatar", "default_avatar.png");
        ri.rating = r.optInt("rating", 0);
        ri.text = r.optString("comment", r.optString("text", ""));
        ri.createdAt = r.optString("created_at", "");
        ri.likes = r.optInt("likes", 0);
        ri.dislikes = r.optInt("dislikes", 0);
        ri.commentsCount = r.optInt("comments_count", 0);
        ri.userReaction = r.optInt("user_reaction", 0);
        return ri;
    }

    private void showAddReviewDialog() {

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(10 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        // выбор оценки
        TextView lbl = new TextView(this);
        lbl.setText("Rating");
        layout.addView(lbl);

        final Spinner spRating = new Spinner(this);

        String[] ratings = new String[]{"1","2","3","4","5"};
        ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(this,
                        android.R.layout.simple_spinner_item,
                        ratings);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRating.setAdapter(adapter);
        spRating.setSelection(4); // по умолчанию 5

        layout.addView(spRating);

        // текст отзыва
        final EditText edt = new EditText(this);
        edt.setHint("Your review");
        edt.setMinLines(3);
        layout.addView(edt);

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Add review");
        b.setView(layout);

        b.setPositiveButton("Send", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                String text = edt.getText().toString().trim();
                int rating = spRating.getSelectedItemPosition() + 1;

                if (text.length() == 0) {
                    Toast.makeText(AppDetailActivity.this, "Write review text", Toast.LENGTH_SHORT).show();
                    return;
                }

                sendReview(text, rating);
            }
        });

        b.setNegativeButton("Cancel", null);

        b.show();
    }

    private void sendReview(final String text, final int rating) {
        final int uid = Prefs.getUserId(this);
        if (uid <= 0) { msg("Login required"); return; }

        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Sending...");
        pd.setCancelable(false);
        pd.show();

        new AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... v) {
                try {
                    String url = Api.baseUrl(AppDetailActivity.this) + "/api/app/" + appId + "/review";

                    JSONObject o = new JSONObject();
                    o.put("user_id", uid);
                    o.put("rating", rating);
                    o.put("comment", text);

                    String resp = Http.postJson(url, o.toString());
                    if (resp == null) return "null response (network?)";
                    return resp;
                } catch (Exception e) {
                    return "EX: " + e.toString();
                }
            }

            protected void onPostExecute(String s) {
                try { pd.dismiss(); } catch (Exception e) {}

                // временно показываем ответ сервера, чтобы понять причину
                // потом можно убрать и оставить только loadReviews();
                if (s == null) {
                    msg("Network error");
                    return;
                }

                // если сервер возвращает {"ok":true} или {"error":...}
                try {
                    JSONObject r = new JSONObject(s);
                    boolean ok = r.optBoolean("ok", false);
                    String err = r.optString("error", "");

                    if (!ok && err.length() > 0) {
                        msg("Review error: " + err);
                        return;
                    }
                } catch (Exception ignore) {
                    // если сервер возвращает не JSON — тоже покажем
                }

                // обновить список
                loadReviews();

                Toast.makeText(AppDetailActivity.this, "Sent", Toast.LENGTH_SHORT).show();
            }
        }.execute();
    }

    // ---------- COMMENTS ----------
    private void showCommentsDialog(final int reviewId) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Loading...");
        pd.setCancelable(false);
        pd.show();

        new AsyncTask<Void, Void, Object>() {
            protected Object doInBackground(Void... v) {
                try {
                    String s = Http.getString(Api.reviewCommentsUrl(AppDetailActivity.this, reviewId));
                    if (s == null) return "null response";
                    return new JSONArray(s);
                } catch (Exception e) {
                    return e.toString();
                }
            }

            protected void onPostExecute(Object out) {
                try { pd.dismiss(); } catch (Exception e) {}

                if (out instanceof String) {
                    msg("Comments error: " + out);
                    return;
                }

                JSONArray arr = (JSONArray) out;
                final String[] items = new String[arr.length()];

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.optJSONObject(i);
                    if (c == null) {
                        items[i] = String.valueOf(arr.opt(i));
                        continue;
                    }
                    String u = c.optString("username", "User");
                    String t = c.optString("text", "");
                    String d = c.optString("created_at", "");
                    items[i] = u + ": " + t + (d.length() > 0 ? ("  (" + d + ")") : "");
                }

                AlertDialog.Builder b = new AlertDialog.Builder(AppDetailActivity.this);
                b.setTitle("Comments");
                b.setItems(items, null);

                b.setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (!Prefs.isLoggedIn(AppDetailActivity.this)) {
                            startActivity(new Intent(AppDetailActivity.this, LoginActivity.class));
                            return;
                        }
                        showAddCommentDialog(reviewId);
                    }
                });

                b.setNegativeButton("Close", null);
                b.show();
            }
        }.execute();
    }

    private void showAddCommentDialog(final int reviewId) {
        final EditText edt = new EditText(this);
        edt.setHint("Comment");

        new AlertDialog.Builder(this)
                .setTitle("Add comment")
                .setView(edt)
                .setPositiveButton("Send", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String text = edt.getText().toString().trim();
                        if (text.length() == 0) return;
                        addReviewComment(reviewId, text);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addReviewComment(final int reviewId, final String text) {
        final int uid = Prefs.getUserId(this);
        if (uid <= 0) { msg("Login required"); return; }

        new AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... v) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("user_id", uid);
                    o.put("text", text);
                    return Http.postJson(Api.reviewAddCommentUrl(AppDetailActivity.this, reviewId), o.toString());
                } catch (Exception e) { return null; }
            }
            protected void onPostExecute(String s) {
                if (s == null) { msg("Network error"); return; }
                loadReviews();
            }
        }.execute();
    }

    // ---------- REACTION / REPORT ----------
    private void sendReaction(final int reviewId, final int value) {
        final int uid = Prefs.getUserId(this);
        if (uid <= 0) { msg("Login required"); return; }

        new AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... v) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("user_id", uid);
                    o.put("value", value);
                    return Http.postJson(Api.reviewReactionUrl(AppDetailActivity.this, reviewId), o.toString());
                } catch (Exception e) { return null; }
            }
            protected void onPostExecute(String s) {
                if (s == null) { msg("Network error"); return; }
                loadReviews();
            }
        }.execute();
    }

    private void reportReview(final int reviewId) {
        final int uid = Prefs.getUserId(this);
        if (uid <= 0) { msg("Login required"); return; }

        new AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... v) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("user_id", uid);
                    return Http.postJson(Api.reviewReportUrl(AppDetailActivity.this, reviewId), o.toString());
                } catch (Exception e) { return null; }
            }
            protected void onPostExecute(String s) {
                if (s == null) { msg("Network error"); return; }
                msg("Reported");
            }
        }.execute();
    }

    // ---------- VERSIONS + DOWNLOAD (FIXED) ----------
    private void chooseVersionAndDownload() {
        if (!btnInstall.isEnabled()) return;

        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage(getString(R.string.loading_versions));
        pd.setCancelable(false);
        pd.show();

        new AsyncTask<Void, Void, Object>() {
            protected Object doInBackground(Void... v) {
                try {
                    String s = Http.getString(Api.appVersionsUrl(AppDetailActivity.this, appId));
                    if (s == null) return "null response";
                    return new JSONArray(s);
                } catch (Exception e) {
                    return e.toString();
                }
            }

            protected void onPostExecute(Object out) {
                try { pd.dismiss(); } catch (Exception e) {}

                if (out instanceof String) {
                    // fallback: download latest
                    startDownload("");
                    return;
                }

                JSONArray arr = (JSONArray) out;
                if (arr.length() == 0) {
                    startDownload("");
                    return;
                }

                // FIX: server returns array of objects {version, apk_file} (and maybe strings)
                final ArrayList<String> versList = new ArrayList<String>();
                final ArrayList<String> versValue = new ArrayList<String>(); // то что реально пойдёт в download

                for (int i = 0; i < arr.length(); i++) {
                    Object it = arr.opt(i);

                    if (it instanceof JSONObject) {
                        JSONObject o = (JSONObject) it;
                        String v = o.optString("version", "");
                        if (v == null || v.length() == 0) continue;

                        int api = o.optInt("api", o.optInt("min_api", 0));
                        String label = v;
                        if (api > 0) label = v + " (Android " + AndroidVersions.apiToAndroid(api) + ")";

                        versList.add(label);
                        versValue.add(v);
                    } else if (it != null) {
                        String v = String.valueOf(it);
                        if (v == null || v.length() == 0) continue;
                        versList.add(v);
                        versValue.add(v);
                    }
                }

                if (versList.size() == 0) { startDownload(""); return; }

                final String[] labels = versList.toArray(new String[versList.size()]);

                new AlertDialog.Builder(AppDetailActivity.this)
                    .setTitle("Select version")
                    .setItems(labels, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            startDownload(versValue.get(which)); // скачиваем чистую версию
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        }.execute();
    }

    private void startDownload(String version) {
        selectedVersion = (version == null ? "" : version);

        final int uid = Prefs.getUserId(this);

        // IMPORTANT: encode version as path segment
        String safeVersion = selectedVersion;
        if (safeVersion != null && safeVersion.length() > 0) {
            safeVersion = Uri.encode(safeVersion);
        }

        final String url = Api.baseUrl(this)
                + (safeVersion != null && safeVersion.length() > 0
                    ? ("/api/download/" + appId + "/" + safeVersion)
                    : ("/api/download/" + appId))
                + (uid > 0 ? ("?user_id=" + uid) : "");

        dlDialog = new ProgressDialog(this);
        dlDialog.setTitle(getString(R.string.downloading));
        dlDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dlDialog.setMax(100);
        dlDialog.setProgress(0);
        dlDialog.setCancelable(false);
        dlDialog.setButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent i = new Intent(AppDetailActivity.this, DownloadService.class);
                i.setAction(DownloadService.ACTION_CANCEL);
                i.putExtra("app_id", appId);
                startService(i);
            }
        });
        dlDialog.show();

        Intent i = new Intent(this, DownloadService.class);
        i.setAction(DownloadService.ACTION_START);
        i.putExtra("app_id", appId);
        i.putExtra("url", url);
        i.putExtra("file_name", "oldmarket_" + appId + (selectedVersion.length() > 0 ? ("_" + selectedVersion) : "") + ".apk");
        startService(i);
    }

    // ---------- INSTALL/OPEN/UNINSTALL ----------
    private void refreshInstalledButtons() {
        boolean installed = (pkgName != null && pkgName.length() > 0 && isInstalled(pkgName));
        btnInstall.setVisibility(installed ? View.GONE : View.VISIBLE);
        btnOpen.setVisibility(installed ? View.VISIBLE : View.GONE);
        btnUninstall.setVisibility(installed ? View.VISIBLE : View.GONE);
    }

    private boolean isInstalled(String packageName) {
        if (packageName == null || packageName.length() == 0) return false;
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception e) { return false; }
    }

    private void openApp() {
        if (pkgName == null || pkgName.length() == 0) return;
        PackageManager pm = getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(pkgName);
        if (launch != null) startActivity(launch);
    }

    private void uninstallApp() {
        if (pkgName == null || pkgName.length() == 0) return;
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + pkgName));
        startActivity(intent);
    }

    private void openInstaller(String path) {
        try {
            File f = new File(path);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(f), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {}
    }

    private void msg(String s) {
        try {
            new AlertDialog.Builder(this)
                    .setMessage(s)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {}
    }


    private void showLoading(boolean show, String text) {
        if (txtLoading != null && text != null) txtLoading.setText(text);
        if (loadingOverlay != null) loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ---------- MODEL ----------
    private static class ReviewItem {
        int id;
        int userId;
        String username = "User";
        String avatar = "default_avatar.png";
        int rating = 0;
        String text = "";
        String createdAt = "";
        int likes = 0;
        int dislikes = 0;
        int commentsCount = 0;
        int userReaction = 0;
    }

    // ---------- ADAPTER ----------
    private class ReviewAdapter extends BaseAdapter {

        public int getCount() { return reviews.size(); }
        public Object getItem(int position) { return reviews.get(position); }
        public long getItemId(int position) { return position; }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(AppDetailActivity.this)
                        .inflate(R.layout.list_item_review, parent, false);
            }

            final ReviewItem r = reviews.get(position);

            ImageView imgUser = (ImageView) convertView.findViewById(R.id.imgUser);
            TextView txtUser = (TextView) convertView.findViewById(R.id.txtUser);
            View.OnClickListener openProfileClick = new View.OnClickListener() {
                public void onClick(View v) {
                    if (r.userId > 0) {
                        Intent i = new Intent(AppDetailActivity.this, UserProfileActivity.class);
                        i.putExtra("user_id", r.userId);
                        startActivity(i);
                    }
                }
            };

            imgUser.setOnClickListener(openProfileClick);
            txtUser.setOnClickListener(openProfileClick);
            
            TextView txtMeta = (TextView) convertView.findViewById(R.id.txtMeta);
            TextView txtText = (TextView) convertView.findViewById(R.id.txtText);

            Button btnLike = (Button) convertView.findViewById(R.id.btnLike);
            Button btnDislike = (Button) convertView.findViewById(R.id.btnDislike);
            Button btnComments = (Button) convertView.findViewById(R.id.btnComments);
            Button btnReport = (Button) convertView.findViewById(R.id.btnReport);

            txtUser.setText(r.username);
            txtText.setText(r.text);

            String meta = "";
            if (r.rating > 0) meta += r.rating + "★";
            if (r.createdAt != null && r.createdAt.length() > 0) {
                if (meta.length() > 0) meta += " • ";
                meta += r.createdAt;
            }
            txtMeta.setText(meta);

            ImageLoader.load(AppDetailActivity.this, Api.avatarUrl(AppDetailActivity.this, r.avatar),
                    imgUser, R.drawable.icon_placeholder);

            btnLike.setText("   " + r.likes);
            btnDislike.setText("   " + r.dislikes);
            btnComments.setText("   " + r.commentsCount);
         // подсветка реакции пользователя
            if (r.userReaction == 1) {
                btnLike.setBackgroundResource(R.drawable.reaction_active);
                btnDislike.setBackgroundResource(R.drawable.reaction_normal);
            }
            else if (r.userReaction == -1) {
                btnDislike.setBackgroundResource(R.drawable.reaction_active);
                btnLike.setBackgroundResource(R.drawable.reaction_normal);
            }
            else {
                btnLike.setBackgroundResource(R.drawable.reaction_normal);
                btnDislike.setBackgroundResource(R.drawable.reaction_normal);
            }
            btnLike.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { sendReaction(r.id, 1); }
            });
            btnDislike.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { sendReaction(r.id, -1); }
            });
            btnComments.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showCommentsDialog(r.id); }
            });
            btnReport.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { reportReview(r.id); }
            });

            return convertView;
        }
    }
}