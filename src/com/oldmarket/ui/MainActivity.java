package com.oldmarket.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

import com.oldmarket.R;
import com.oldmarket.model.AppItem;
import com.oldmarket.net.Api;
import com.oldmarket.net.Http;
import com.oldmarket.service.UpdateCheckService;
import com.oldmarket.util.ImageLoader;
import com.oldmarket.util.LocaleHelper;
import com.oldmarket.util.Prefs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private ListView list;
    private AppListAdapter adapter;
    private ArrayList<AppItem> items = new ArrayList<AppItem>();

    private ImageButton btnSearch;
    private Button btnApps, btnGames, btnDownloads;
    private TextView txtSection, txtMarket, txtPromoType, txtBrowseCategory;
    private View loadingOverlay, promoMainRoot;
    private ImageButton logo;
    private ImageView promoIcon1, promoIcon2, promoIcon3;
    private ImageView promoMirror1, promoMirror2, promoMirror3;
    private PromoCategory currentPromoCategory;

    private static class PromoCategory {
        String code;
        String label;
        boolean isGame;
        ArrayList<AppItem> apps = new ArrayList<AppItem>();
    }

    private static ArrayList<AppItem> CACHE_ITEMS = new ArrayList<AppItem>();
    private static ArrayList<AppItem> CACHE_PROMO_SOURCE = new ArrayList<AppItem>();
    private static long CACHE_TIME = 0L;

    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LocaleHelper.applySavedLocale(this);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        setContentView(R.layout.activity_main);

        loadingOverlay = findViewById(R.id.loadingOverlay);
        logo = (ImageButton) findViewById(R.id.logo);
        list = (ListView) findViewById(R.id.list);
        btnApps = (Button) findViewById(R.id.btnApps);
        btnGames = (Button) findViewById(R.id.btnGames);
        btnDownloads = (Button) findViewById(R.id.btnDownloads);
        btnSearch = (ImageButton) findViewById(R.id.btnSearch);
        txtMarket = (TextView) findViewById(R.id.txtMarket);

        View header = getLayoutInflater().inflate(R.layout.main_list_header, list, false);
        promoMainRoot = header.findViewById(R.id.promoMainRoot);
        promoIcon1 = (ImageView) header.findViewById(R.id.promoIcon1);
        promoIcon2 = (ImageView) header.findViewById(R.id.promoIcon2);
        promoIcon3 = (ImageView) header.findViewById(R.id.promoIcon3);
        promoMirror1 = (ImageView) header.findViewById(R.id.promoMirror1);
        promoMirror2 = (ImageView) header.findViewById(R.id.promoMirror2);
        promoMirror3 = (ImageView) header.findViewById(R.id.promoMirror3);
        txtPromoType = (TextView) header.findViewById(R.id.txtPromoType);
        txtBrowseCategory = (TextView) header.findViewById(R.id.txtBrowseCategory);
        txtSection = (TextView) header.findViewById(R.id.txtSection);
        list.addHeaderView(header, null, false);

        try {
            Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/storopia.ttf");
            txtMarket.setTypeface(tf);
            txtPromoType.setTypeface(tf);
        } catch (Exception e) { }

        adapter = new AppListAdapter(this, items);
        list.setAdapter(adapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - list.getHeaderViewsCount();
                if (idx < 0 || idx >= items.size()) return;
                AppItem it = items.get(idx);
                Intent i = new Intent(MainActivity.this, AppDetailActivity.class);
                i.putExtra("app_id", it.id);
                startActivity(i);
            }
        });

        btnSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(MainActivity.this, SearchActivity.class)); }
        });
        btnApps.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openCategories(false); }
        });
        btnGames.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openCategories(true); }
        });
        btnDownloads.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openDownloads(); }
        });
        logo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { openOptionsMenu(); } catch (Exception e) { }
            }
        });
        promoMainRoot.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (currentPromoCategory == null) return;
                Intent i = new Intent(MainActivity.this, CategoryAppsActivity.class);
                i.putExtra("category", currentPromoCategory.code);
                i.putExtra("title", currentPromoCategory.label);
                i.putExtra("is_game", currentPromoCategory.isGame);
                startActivity(i);
            }
        });

        txtMarket.setText(isRu() ? "маркет" : "market");
        try {
            int androidLogoRes = getResources().getIdentifier("market_android_logo", "drawable", getPackageName());
            ImageView iw = (ImageView) findViewById(R.id.imgAndroidWord);
            if (iw != null && androidLogoRes != 0) iw.setImageResource(androidLogoRes);
        } catch (Exception e) { }
        if (!restoreFromCache()) {
            loadTopContent();
        }
        checkClientUpdateIfNeeded();
        sendAnalyticsIfNeeded();
        scheduleUpdateChecks();
    }

    private boolean isRu() {
        String lang = java.util.Locale.getDefault().getLanguage();
        return lang != null && lang.toLowerCase().startsWith("ru");
    }

    private void scheduleUpdateChecks() {
        try {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            Intent i = new Intent(this, UpdateCheckService.class);
            PendingIntent pi = PendingIntent.getService(this, 30001, i, PendingIntent.FLAG_UPDATE_CURRENT);
            long interval = 5L * 60L * 1000L;
            long first = System.currentTimeMillis() + 15000L;
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, interval, pi);
        } catch (Exception e) { }
    }

    protected void onResume() {
        super.onResume();
        adapter.refreshInstalledPackages();
        adapter.notifyDataSetChanged();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem downloads = menu.add(0, 3, 0, getString(R.string.downloads));
        downloads.setIcon(R.drawable.ic_menu_downloads);
        MenuItem profile = menu.add(0, 1, 1, Prefs.isLoggedIn(this) ? getString(R.string.profile) : getString(R.string.login));
        profile.setIcon(R.drawable.ic_menu_account);
        MenuItem settings = menu.add(0, 2, 2, getString(R.string.settings));
        settings.setIcon(R.drawable.ic_menu_settings_custom);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            startActivity(new Intent(this, Prefs.isLoggedIn(this) ? ProfileActivity.class : LoginActivity.class));
            return true;
        }
        if (item.getItemId() == 2) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (item.getItemId() == 3) {
            openDownloads();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openCategories(boolean isGame) {
        Intent i = new Intent(this, CategoryListActivity.class);
        i.putExtra("is_game", isGame);
        startActivity(i);
    }

    private void openDownloads() { startActivity(new Intent(this, DownloadsActivity.class)); }

    private boolean restoreFromCache() {
        if (CACHE_ITEMS == null || CACHE_ITEMS.isEmpty()) return false;
        if (System.currentTimeMillis() - CACHE_TIME > 120000L) return false;
        items.clear();
        items.addAll(CACHE_ITEMS);
        adapter.refreshInstalledPackages();
        adapter.notifyDataSetChanged();
        if (CACHE_PROMO_SOURCE != null && !CACHE_PROMO_SOURCE.isEmpty()) bindPromo(CACHE_PROMO_SOURCE);
        showLoading(false);
        return true;
    }

    private void loadTopContent() {
        showLoading(true);
        final int deviceApi = Build.VERSION.SDK_INT;
        new AsyncTask<Void, Void, ArrayList<AppItem>>() {
            ArrayList<AppItem> promoSource = new ArrayList<AppItem>();
            protected ArrayList<AppItem> doInBackground(Void... v) {
                ArrayList<AppItem> out = new ArrayList<AppItem>();
                loadEndpoint("/api/top-apps", false, deviceApi, out);
                loadEndpoint("/api/top-games", true, deviceApi, out);
                // wider source for promo
                loadEndpoint("/api/apps?is_game=0", false, deviceApi, promoSource);
                loadEndpoint("/api/apps?is_game=1", true, deviceApi, promoSource);
                return out;
            }
            protected void onPostExecute(ArrayList<AppItem> out) {
                showLoading(false);
                if (out == null) {
                    Toast.makeText(MainActivity.this, R.string.error_network, Toast.LENGTH_SHORT).show();
                    return;
                }
                items.clear();
                items.addAll(out);
                CACHE_ITEMS.clear();
                CACHE_ITEMS.addAll(out);
                CACHE_PROMO_SOURCE.clear();
                CACHE_PROMO_SOURCE.addAll(promoSource);
                CACHE_TIME = System.currentTimeMillis();
                adapter.refreshInstalledPackages();
                adapter.notifyDataSetChanged();
                bindPromo(promoSource);
            }
        }.execute();
    }

    private void loadEndpoint(String endpoint, boolean isGame, int deviceApi, ArrayList<AppItem> out) {
        try {
            String s = Http.getString(Api.baseUrl(this) + endpoint);
            if (s == null) return;
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                AppItem a = new AppItem();
                a.id = o.optInt("id", 0);
                a.name = o.optString("name", "");
                a.developer = o.optString("developer", o.optString("author", ""));
                a.icon = o.optString("icon", "");
                a.api = o.optInt("api", 1);
                a.packageName = o.optString("package", o.optString("package_name", ""));
                a.isGame = isGame || o.optBoolean("is_game", false);
                a.categoryCode = o.optString("category_code", o.optString("category", ""));
                a.categoryLabel = o.optString("category_label", a.categoryCode);
                a.rating = (float) o.optDouble("rating", 0.0);
                a.downloads = o.optInt("downloads", 0);
                if (a.api <= deviceApi) out.add(a);
            }
        } catch (Exception e) { }
    }

    private void bindPromo(ArrayList<AppItem> source) {
        if (source == null || source.size() == 0) return;
        HashMap<String, PromoCategory> map = new HashMap<String, PromoCategory>();
        for (int i = 0; i < source.size(); i++) {
            AppItem a = source.get(i);
            if (a.categoryCode == null || a.categoryCode.length() == 0) continue;
            String key = a.categoryCode + "|" + a.isGame;
            PromoCategory pc = map.get(key);
            if (pc == null) {
                pc = new PromoCategory();
                pc.code = a.categoryCode;
                pc.label = a.categoryLabel;
                pc.isGame = a.isGame;
                map.put(key, pc);
            }
            if (pc.apps.size() < 6) pc.apps.add(a);
        }
        if (map.size() == 0) return;
        ArrayList<PromoCategory> cats = new ArrayList<PromoCategory>(map.values());
        currentPromoCategory = cats.get(new Random().nextInt(cats.size()));
        txtPromoType.setText(currentPromoCategory.isGame ? getString(R.string.games).toLowerCase() : getString(R.string.apps).toLowerCase());
        txtBrowseCategory.setText((isRu() ? "Обзор " : "Browse ") + currentPromoCategory.label);

        bindPromoIcon(promoIcon1, promoMirror1, currentPromoCategory.apps, 0);
        bindPromoIcon(promoIcon2, promoMirror2, currentPromoCategory.apps, 1);
        bindPromoIcon(promoIcon3, promoMirror3, currentPromoCategory.apps, 2);
    }

    private void bindPromoIcon(final ImageView iv, final ImageView mirror, ArrayList<AppItem> apps, int idx) {
        if (apps.size() <= idx) {
            iv.setImageResource(R.drawable.icon_placeholder);
            mirror.setImageResource(R.drawable.icon_placeholder);
            return;
        }
        AppItem a = apps.get(idx);
        final String url = Api.iconUrl(this, a.icon);
        ImageLoader.load(this, url, iv, R.drawable.icon_placeholder);
        iv.postDelayed(new Runnable() {
            public void run() { updateMirrorFromImageView(iv, mirror, 0); }
        }, 250);
    }

    private void updateMirrorFromImageView(final ImageView source, final ImageView mirror, final int attempt) {
        try {
            Drawable d = source.getDrawable();
            if (d == null) {
                if (attempt < 8) {
                    source.postDelayed(new Runnable() {
                        public void run() { updateMirrorFromImageView(source, mirror, attempt + 1); }
                    }, 200);
                }
                return;
            }
            Bitmap original = drawableToBitmap(d, source.getWidth() > 0 ? source.getWidth() : 44, source.getHeight() > 0 ? source.getHeight() : 44);
            if (original == null) return;
            Bitmap reflected = createReflectionBitmap(original);
            if (reflected != null) mirror.setImageBitmap(reflected);
        } catch (Throwable e) { }
    }

    private Bitmap drawableToBitmap(Drawable drawable, int reqW, int reqH) {
        try {
            if (drawable instanceof BitmapDrawable) {
                Bitmap b = ((BitmapDrawable) drawable).getBitmap();
                if (b != null) return b;
            }
            int w = reqW > 0 ? reqW : 44;
            int h = reqH > 0 ? reqH : 44;
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, w, h);
            drawable.draw(canvas);
            return bitmap;
        } catch (Throwable e) {
            return null;
        }
    }

    private Bitmap createReflectionBitmap(Bitmap original) {
        try {
            int width = original.getWidth();
            int height = original.getHeight();
            if (width <= 0 || height <= 1) return null;
            int reflectionHeight = Math.max(8, height / 2);
            Matrix matrix = new Matrix();
            matrix.preScale(1, -1);
            Bitmap reflection = Bitmap.createBitmap(original, 0, height - reflectionHeight, width, reflectionHeight, matrix, false);
            Bitmap bitmapWithReflection = Bitmap.createBitmap(width, reflectionHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmapWithReflection);
            canvas.drawBitmap(reflection, 0, 0, null);
            Paint paint = new Paint();
            LinearGradient shader = new LinearGradient(0, 0, 0, reflectionHeight, 0x66ffffff, 0x00ffffff, Shader.TileMode.CLAMP);
            paint.setShader(shader);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            canvas.drawRect(0, 0, width, reflectionHeight, paint);
            return bitmapWithReflection;
        } catch (Throwable e) {
            return null;
        }
    }

    private void showLoading(boolean show) {
        if (loadingOverlay != null) loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }


    private void checkClientUpdateIfNeeded() {
        final boolean ru = isRu();
        new AsyncTask<Void, Void, JSONObject>() {
            protected JSONObject doInBackground(Void... params) {
                try {
                    String s = Http.getString(Api.clientLatestUrl(MainActivity.this));
                    if (s == null || s.length() == 0) return null;
                    return new JSONObject(s);
                } catch (Exception e) { return null; }
            }
            protected void onPostExecute(JSONObject o) {
                if (o == null) return;
                try {
                    int latestCode = o.optInt("version_code", 0);
                    String latestName = o.optString("version_name", "");
                    String updateUrl = o.optString("update_url", "");
                    String notes = ru ? o.optString("notes_ru", "") : o.optString("notes_en", "");
                    PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                    if (latestCode > pi.versionCode && updateUrl != null && updateUrl.length() > 0) {
                        StringBuilder msg = new StringBuilder(getString(R.string.client_update_message));
                        if (latestName != null && latestName.length() > 0) {
                            msg.append("\\n\\n").append(ru ? "Версия: " : "Version: ").append(latestName);
                        }
                        if (notes != null && notes.length() > 0) {
                            msg.append("\\n\\n").append(getString(R.string.client_update_note_prefix)).append(notes);
                        }
                        final String finalUrl = updateUrl;
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(getString(R.string.client_update_title))
                                .setMessage(msg.toString())
                                .setPositiveButton(getString(R.string.update_now), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)));
                                        } catch (Exception e) { }
                                    }
                                })
                                .setNegativeButton(getString(R.string.later), null)
                                .show();
                    }
                } catch (Exception e) { }
            }
        }.execute();
    }

    private void sendAnalyticsIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - Prefs.getLastAnalyticsSentAt(this) < 12L * 60L * 60L * 1000L) return;
        Prefs.setLastAnalyticsSentAt(this, now);
        new AsyncTask<Void, Void, Void>() {
            protected Void doInBackground(Void... params) {
                try {
                    PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                    JSONObject o = new JSONObject();
                    o.put("api_level", Build.VERSION.SDK_INT);
                    o.put("app_version_code", pi.versionCode);
                    o.put("app_version_name", pi.versionName == null ? "" : pi.versionName);
                    o.put("device_model", Build.MODEL == null ? "" : Build.MODEL);
                    o.put("manufacturer", Build.MANUFACTURER == null ? "" : Build.MANUFACTURER);
                    o.put("lang", java.util.Locale.getDefault().getLanguage());
                    Http.postJson(Api.clientAnalyticsUrl(MainActivity.this), o.toString());
                } catch (Exception e) { }
                return null;
            }
        }.execute();
    }

    private void showApi25WarningIfNeeded() {
        if (Build.VERSION.SDK_INT == 25) {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("OldMarket can work badly on newer devices.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) { }
                    })
                    .show();
        }
    }
}
