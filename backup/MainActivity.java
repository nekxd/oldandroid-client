package com.oldmarket.ui;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import com.oldmarket.R;
import com.oldmarket.model.AppItem;
import com.oldmarket.net.Api;
import com.oldmarket.net.Http;
import com.oldmarket.util.ImageLoader;
import com.oldmarket.util.LocaleHelper;
import com.oldmarket.util.Prefs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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

    private ImageView bannerImage;
    private ImageButton btnSearch;
    private Button btnApps, btnGames;
    private TextView txtSection;
    private View loadingOverlay;
    private ImageView logo;

    private JSONArray bannersArr = null;
    private int bannerIdx = 0;
    private String currentBannerUrl = null;
    private Handler bannerHandler = new Handler();
    private Runnable bannerRunnable = null;

    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LocaleHelper.applySavedLocale(this);
        setContentView(R.layout.activity_main);

        loadingOverlay = (View) findViewById(R.id.loadingOverlay);
        logo = (ImageView) findViewById(R.id.logo);
        list = (ListView) findViewById(R.id.list);
        btnApps = (Button) findViewById(R.id.btnApps);
        btnGames = (Button) findViewById(R.id.btnGames);
        btnSearch = (ImageButton) findViewById(R.id.btnSearch);
        txtSection = (TextView) findViewById(R.id.txtSection);

        View header = getLayoutInflater().inflate(R.layout.main_list_header, list, false);
        bannerImage = (ImageView) header.findViewById(R.id.bannerImage);
        list.addHeaderView(header, null, false);

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
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SearchActivity.class));
            }
        });

        btnApps.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, CategoryListActivity.class);
                i.putExtra("is_game", false);
                i.putExtra("title", getString(R.string.apps));
                startActivity(i);
            }
        });

        btnGames.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, CategoryListActivity.class);
                i.putExtra("is_game", true);
                i.putExtra("title", getString(R.string.games));
                startActivity(i);
            }
        });

        bannerImage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (currentBannerUrl != null && currentBannerUrl.trim().length() > 0) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(currentBannerUrl.trim()));
                        startActivity(i);
                    } catch (Exception e) { }
                }
            }
        });

        showLoading(true);
        loadServerLogo();
        showApi25WarningIfNeeded();
        loadBanners();
        loadTopContent();
        if (Prefs.isBannerHidden(this) && bannerImage != null) bannerImage.setVisibility(View.GONE);
    }

    protected void onResume() {
        super.onResume();
        adapter.refreshInstalledPackages();
        adapter.notifyDataSetChanged();
        loadBanners();
        loadServerLogo();
        loadTopContent();
    }

    protected void onPause() {
        super.onPause();
        stopBannerRotation();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, Prefs.isLoggedIn(this) ? getString(R.string.profile) : getString(R.string.login));
        menu.add(0, 2, 1, getString(R.string.settings));
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
        return super.onOptionsItemSelected(item);
    }

    private void loadServerLogo() {
        if (logo == null) return;
        ImageLoader.loadBanner(this, Api.logoUrl(this), logo, R.drawable.logo);
    }

    private void loadBanners() {
        new AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... v) {
                try { return Http.getString(Api.baseUrl(MainActivity.this) + "/api/banners"); }
                catch (Exception e) { return null; }
            }
            protected void onPostExecute(String s) {
                if (s == null) return;
                try {
                    bannersArr = new JSONArray(s);
                    bannerIdx = 0;
                    startBannerRotation();
                } catch (Exception e) { }
            }
        }.execute();
    }

    private void startBannerRotation() {
        stopBannerRotation();
        if (bannersArr == null || bannersArr.length() == 0) return;
        if (Prefs.isBannerHidden(this)) return;

        bannerRunnable = new Runnable() {
            public void run() {
                try {
                    JSONObject b = bannersArr.getJSONObject(bannerIdx % bannersArr.length());
                    String img = b.optString("image", "");
                    currentBannerUrl = b.optString("url", "");
                    if (img != null && img.length() > 0) {
                        ImageLoader.loadBanner(MainActivity.this, Api.bannerUrl(MainActivity.this, img), bannerImage, R.drawable.banner_placeholder);
                    }
                    bannerIdx++;
                } catch (Exception e) { }
                bannerHandler.postDelayed(this, 5000);
            }
        };
        bannerHandler.post(bannerRunnable);
    }

    private void stopBannerRotation() {
        if (bannerRunnable != null) {
            bannerHandler.removeCallbacks(bannerRunnable);
            bannerRunnable = null;
        }
    }

    private void loadTopContent() {
        showLoading(true);
        final int deviceApi = Build.VERSION.SDK_INT;
        new AsyncTask<Void, Void, ArrayList<AppItem>>() {
            protected ArrayList<AppItem> doInBackground(Void... v) {
                ArrayList<AppItem> out = new ArrayList<AppItem>();
                loadEndpoint("/api/top-apps", false, deviceApi, out);
                loadEndpoint("/api/top-games", true, deviceApi, out);
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
                adapter.refreshInstalledPackages();
                adapter.notifyDataSetChanged();
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

    private void showLoading(boolean show) {
        if (loadingOverlay != null) loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showApi25WarningIfNeeded() {
        if (Build.VERSION.SDK_INT == 25) {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Android 7.1.1 has issues with some SSL/network configurations. If something loads slowly, try HTTP server address in settings.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) { }
                    })
                    .show();
        }
    }
}
