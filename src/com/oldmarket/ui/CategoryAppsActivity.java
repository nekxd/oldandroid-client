package com.oldmarket.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

import com.oldmarket.R;
import com.oldmarket.model.AppItem;
import com.oldmarket.net.Api;
import com.oldmarket.net.Http;
import com.oldmarket.util.ImageLoader;
import com.oldmarket.util.LocaleHelper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class CategoryAppsActivity extends Activity {
    private ListView list;
    private TextView titleView, subtitleView;
    private View loadingOverlay;
    private AppListAdapter adapter;
    private ArrayList<AppItem> items = new ArrayList<AppItem>();
    private ArrayList<AppItem> originalItems = new ArrayList<AppItem>();
    private View promoRoot;
    private ImageView promoIcon;
    private TextView promoText;
    private Button btnTopFree, btnTopDownloads;
    private AppItem promoApp;
    private boolean sortDownloads = false;

    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LocaleHelper.applySavedLocale(this);
        setContentView(R.layout.activity_category_apps);

        final String title = getIntent().getStringExtra("title");
        final String category = getIntent().getStringExtra("category");
        final boolean isGame = getIntent().getBooleanExtra("is_game", false);

        titleView = (TextView) findViewById(R.id.txtTitle);
        subtitleView = (TextView) findViewById(R.id.txtSubtitle);
        list = (ListView) findViewById(R.id.list);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        btnTopFree = (Button) findViewById(R.id.btnTopFree);
        btnTopDownloads = (Button) findViewById(R.id.btnTopDownloads);

        if (list == null) {
            Toast.makeText(this, "list not found", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        View promoHeader = LayoutInflater.from(this).inflate(R.layout.view_promotion_app, list, false);
        promoRoot = promoHeader.findViewById(R.id.promoRoot);
        promoIcon = (ImageView) promoHeader.findViewById(R.id.promoIcon);
        promoText = (TextView) promoHeader.findViewById(R.id.promoText);
        list.addHeaderView(promoHeader, null, false);

        try {
            ImageButton btnHome = (ImageButton) findViewById(R.id.btnHome);
            if (btnHome != null) {
                                btnHome.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent i = new Intent(CategoryAppsActivity.this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(i);
                        finish();
                    }
                });
            }
            ((ImageButton)findViewById(R.id.btnSearch)).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { startActivity(new Intent(CategoryAppsActivity.this, SearchActivity.class)); }
            });
            Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/storopia.ttf");
            if (titleView != null) titleView.setTypeface(tf);
        } catch (Exception e) { }

        if (titleView != null) titleView.setText(getString(isGame ? R.string.games1 : R.string.apps1));
        if (subtitleView != null) subtitleView.setText(title == null || title.length() == 0 ? getString(isGame ? R.string.all_games : R.string.all_apps) : title);

        adapter = new AppListAdapter(this, items);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idx = position - list.getHeaderViewsCount();
                if (idx < 0 || idx >= items.size()) return;
                AppItem it = items.get(idx);
                Intent i = new Intent(CategoryAppsActivity.this, AppDetailActivity.class);
                i.putExtra("app_id", it.id);
                startActivity(i);
            }
        });

        if (btnTopFree != null) btnTopFree.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sortDownloads = false; applySort(); updateTabButtons(); }
        });
        if (btnTopDownloads != null) btnTopDownloads.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sortDownloads = true; applySort(); updateTabButtons(); }
        });
        if (promoRoot != null) promoRoot.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (promoApp == null) return;
                Intent i = new Intent(CategoryAppsActivity.this, AppDetailActivity.class);
                i.putExtra("app_id", promoApp.id);
                startActivity(i);
            }
        });

        updateTabButtons();
        loadApps(category, isGame);
    }

    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.refreshInstalledPackages();
            adapter.notifyDataSetChanged();
        }
    }

    private void loadApps(final String category, final boolean isGame) {
        showLoading(true);
        new AsyncTask<Void, Void, ArrayList<AppItem>>() {
            protected ArrayList<AppItem> doInBackground(Void... v) {
                try {
                    String url = Api.baseUrl(CategoryAppsActivity.this) + "/api/apps?is_game=" + (isGame ? "1" : "0");
                    if (category != null && category.length() > 0) url += "&category=" + java.net.URLEncoder.encode(category, "UTF-8");
                    String s = Http.getString(url);
                    if (s == null) return null;
                    JSONArray arr = new JSONArray(s);
                    ArrayList<AppItem> out = new ArrayList<AppItem>();
                    int deviceApi = Build.VERSION.SDK_INT;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        AppItem a = new AppItem();
                        a.id = o.optInt("id", 0);
                        a.name = o.optString("name", "");
                        a.developer = o.optString("developer", o.optString("author", ""));
                        a.icon = o.optString("icon", "");
                        a.api = o.optInt("api", 1);
                        a.packageName = o.optString("package", o.optString("package_name", ""));
                        a.isGame = o.optBoolean("is_game", false);
                        a.categoryCode = o.optString("category_code", o.optString("category", ""));
                        a.categoryLabel = o.optString("category_label", a.categoryCode);
                        a.rating = (float) o.optDouble("rating", 0.0);
                        a.downloads = o.optInt("downloads", 0);
                        a.description = o.optString("description", "");
                        if (a.api <= deviceApi) out.add(a);
                    }
                    return out;
                } catch (Exception e) { return null; }
            }
            protected void onPostExecute(ArrayList<AppItem> out) {
                showLoading(false);
                if (out == null) {
                    Toast.makeText(CategoryAppsActivity.this, R.string.error_network, Toast.LENGTH_SHORT).show();
                    return;
                }
                originalItems.clear();
                originalItems.addAll(out);
                bindPromotion();
                applySort();
                updateTabButtons();
            }
        }.execute();
    }

    private void applySort() {
        items.clear();
        items.addAll(originalItems);
        if (sortDownloads) {
            Collections.sort(items, new Comparator<AppItem>() {
                public int compare(AppItem a, AppItem b) { return b.downloads - a.downloads; }
            });
        } else {
            Collections.sort(items, new Comparator<AppItem>() {
                public int compare(AppItem a, AppItem b) {
                    int r = Float.compare(b.rating, a.rating);
                    if (r != 0) return r;
                    return AppItem.safe(a.name).compareToIgnoreCase(AppItem.safe(b.name));
                }
            });
        }
        adapter.refreshInstalledPackages();
        adapter.notifyDataSetChanged();
    }

    private void bindPromotion() {
        if (promoRoot == null || promoIcon == null || promoText == null) return;
        if (originalItems.isEmpty()) {
            promoRoot.setVisibility(View.GONE);
            return;
        }
        promoRoot.setVisibility(View.VISIBLE);
        promoApp = originalItems.get(new Random().nextInt(originalItems.size()));
        ImageLoader.load(this, Api.iconUrl(this, promoApp.icon), promoIcon, R.drawable.icon_placeholder);
        String text = promoApp.description == null ? promoApp.name : promoApp.description;
        if (text.length() == 0) text = promoApp.name;
        if (text.length() > 90) text = text.substring(0, 90) + "...";
        promoText.setText(text);
    }

    private void updateTabButtons() {
        if (btnTopFree != null) {
            btnTopFree.setCompoundDrawablePadding(6);
            btnTopFree.setCompoundDrawablesWithIntrinsicBounds(sortDownloads ? R.drawable.btn_strip_mark_off : R.drawable.btn_strip_mark_on, 0, 0, 0);
        }
        if (btnTopDownloads != null) {
            btnTopDownloads.setCompoundDrawablePadding(6);
            btnTopDownloads.setCompoundDrawablesWithIntrinsicBounds(sortDownloads ? R.drawable.btn_strip_mark_on : R.drawable.btn_strip_mark_off, 0, 0, 0);
        }
    }

    private void showLoading(boolean show) {
        if (loadingOverlay != null) loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
