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
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class CategoryListActivity extends Activity {

    private static class CategoryItem {
        public final String code;
        public final String label;
        public String preview = "";
        public CategoryItem(String code, String label) { this.code = code; this.label = label; }
        public String toString() { return label; }
    }

    private ListView list;
    private TextView titleView;
    private View loadingOverlay;
    private ArrayList<CategoryItem> items = new ArrayList<CategoryItem>();
    private ArrayList<AppItem> allApps = new ArrayList<AppItem>();
    private ArrayAdapter<CategoryItem> adapter;
    private boolean isGame;
    private View promoHeader;
    private View promoRoot;
    private ImageView promoIcon;
    private TextView promoText;
    private AppItem promoApp;

    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LocaleHelper.applySavedLocale(this);
        setContentView(R.layout.activity_category_list);

        isGame = getIntent().getBooleanExtra("is_game", false);

        titleView = (TextView) findViewById(R.id.txtTitle);
        list = (ListView) findViewById(R.id.list);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        ImageButton btnHome = (ImageButton) findViewById(R.id.btnHome);
        ImageButton btnSearch = (ImageButton) findViewById(R.id.btnSearch);

        if (btnHome != null) {
                        btnHome.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent i = new Intent(CategoryListActivity.this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                    finish();
                }
            });
        }
        if (btnSearch != null) {
            btnSearch.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { startActivity(new Intent(CategoryListActivity.this, SearchActivity.class)); }
            });
        }

        if (titleView != null) {
            titleView.setText(getString(isGame ? R.string.games1 : R.string.apps1));
            try {
                Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/storopia.ttf");
                titleView.setTypeface(tf);
            } catch (Exception e) { }
        }

        LayoutInflater inf = LayoutInflater.from(this);
        promoHeader = inf.inflate(R.layout.view_promotion_app, list, false);
        promoRoot = promoHeader.findViewById(R.id.promoRoot);
        promoIcon = (ImageView) promoHeader.findViewById(R.id.promoIcon);
        promoText = (TextView) promoHeader.findViewById(R.id.promoText);
        if (promoRoot != null) {
            promoRoot.setVisibility(View.GONE);
            promoRoot.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (promoApp == null) return;
                    Intent i = new Intent(CategoryListActivity.this, AppDetailActivity.class);
                    i.putExtra("app_id", promoApp.id);
                    startActivity(i);
                }
            });
        }
        if (list != null) list.addHeaderView(promoHeader, null, false);

        adapter = new ArrayAdapter<CategoryItem>(this, R.layout.list_item_category, R.id.text1, items) {
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = (TextView) v.findViewById(R.id.text1);
                TextView tvSub = (TextView) v.findViewById(R.id.text2);
                CategoryItem item = items.get(position);
                if (tv != null) {
                    tv.setText(item.label);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                }
                if (tvSub != null) {
                    tvSub.setText(item.preview == null ? "" : item.preview);
                    tvSub.setVisibility(item.preview != null && item.preview.length() > 0 ? View.VISIBLE : View.GONE);
                }
                return v;
            }
        };
        if (list != null) {
            list.setAdapter(adapter);
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    int idx = position - list.getHeaderViewsCount();
                    if (idx < 0 || idx >= items.size()) return;
                    CategoryItem item = items.get(idx);
                    Intent i = new Intent(CategoryListActivity.this, CategoryAppsActivity.class);
                    i.putExtra("is_game", isGame);
                    i.putExtra("category", item.code);
                    i.putExtra("title", item.label);
                    startActivity(i);
                }
            });
        }
        loadData();
    }

    private void loadData() {
        showLoading(true);
        new AsyncTask<Void, Void, Boolean>() {
            ArrayList<CategoryItem> outCats = new ArrayList<CategoryItem>();
            ArrayList<AppItem> outApps = new ArrayList<AppItem>();
            protected Boolean doInBackground(Void... params) {
                try {
                    String s = Http.getString(Api.baseUrl(CategoryListActivity.this) + "/api/categories?is_game=" + (isGame ? "1" : "0"));
                    String appsStr = Http.getString(Api.baseUrl(CategoryListActivity.this) + "/api/apps?is_game=" + (isGame ? "1" : "0"));
                    if (s == null || appsStr == null) return false;
                    JSONArray arr = new JSONArray(s);
                    JSONArray appsArr = new JSONArray(appsStr);

                    outCats.add(new CategoryItem("", getString(isGame ? R.string.all_games : R.string.all_apps)));
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        outCats.add(new CategoryItem(o.optString("code", ""), o.optString("label", "")));
                    }

                    int deviceApi = Build.VERSION.SDK_INT;
                    for (int i = 0; i < appsArr.length(); i++) {
                        JSONObject o = appsArr.getJSONObject(i);
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
                        if (a.api <= deviceApi) outApps.add(a);
                    }

                    HashMap<String, ArrayList<String>> previews = new HashMap<String, ArrayList<String>>();
                    for (int i = 0; i < outApps.size(); i++) {
                        AppItem a = outApps.get(i);
                        String key = a.categoryCode == null ? "" : a.categoryCode;
                        ArrayList<String> names = previews.get(key);
                        if (names == null) { names = new ArrayList<String>(); previews.put(key, names); }
                        if (names.size() < 3) names.add(a.name);
                    }
                    ArrayList<String> allNames = new ArrayList<String>();
                    for (int i = 0; i < outApps.size() && allNames.size() < 3; i++) allNames.add(outApps.get(i).name);

                    for (int i = 0; i < outCats.size(); i++) {
                        CategoryItem c = outCats.get(i);
                        ArrayList<String> names = c.code.length() == 0 ? allNames : previews.get(c.code);
                        if (names != null && names.size() > 0) {
                            StringBuilder sb = new StringBuilder();
                            for (int j = 0; j < names.size(); j++) {
                                if (j > 0) sb.append(", ");
                                sb.append(names.get(j));
                            }
                            c.preview = sb.toString();
                        } else {
                            c.preview = "";
                        }
                    }
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }

            protected void onPostExecute(Boolean ok) {
                showLoading(false);
                if (!ok.booleanValue()) {
                    Toast.makeText(CategoryListActivity.this, R.string.error_network, Toast.LENGTH_SHORT).show();
                    return;
                }
                items.clear();
                items.addAll(outCats);
                allApps.clear();
                allApps.addAll(outApps);
                adapter.notifyDataSetChanged();
                bindPromotion();
            }
        }.execute();
    }

    private void bindPromotion() {
        if (promoRoot == null || promoIcon == null || promoText == null) return;
        if (allApps.isEmpty()) {
            promoRoot.setVisibility(View.GONE);
            return;
        }
        promoRoot.setVisibility(View.VISIBLE);
        promoApp = allApps.get(new Random().nextInt(allApps.size()));
        ImageLoader.load(this, Api.iconUrl(this, promoApp.icon), promoIcon, R.drawable.icon_placeholder);
        String text = promoApp.description == null ? promoApp.name : promoApp.description;
        if (text.length() == 0) text = promoApp.name;
        if (text.length() > 90) text = text.substring(0, 90) + "...";
        promoText.setText(text);
    }

    private void showLoading(boolean show) {
        if (loadingOverlay != null) loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
