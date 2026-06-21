package com.oldmarket.ui;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.oldmarket.R;
import com.oldmarket.model.AppItem;
import com.oldmarket.net.Api;
import com.oldmarket.net.Http;
import com.oldmarket.util.LocaleHelper;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class SearchActivity extends Activity {
    private EditText edt;
    private ImageButton btn;
    private ListView list;
    private ArrayList<AppItem> data = new ArrayList<AppItem>();
    private AppListAdapter adapter;

    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LocaleHelper.applySavedLocale(this);
        setContentView(R.layout.activity_search);

        edt = (EditText) findViewById(R.id.edtQuery);
        btn = (ImageButton) findViewById(R.id.btnDoSearch);
        list = (ListView) findViewById(R.id.list);

        adapter = new AppListAdapter(this, data);
        list.setAdapter(adapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppItem it = data.get(position);
                Intent i = new Intent(SearchActivity.this, AppDetailActivity.class);
                i.putExtra("app_id", it.id);
                startActivity(i);
            }
        });

        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doSearch();
            }
        });

        edt.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    doSearch();
                    return true;
                }
                return false;
            }
        });
    }

    private void doSearch() {
        final String q = edt.getText().toString().trim();
        if (q.length() == 0) return;

        new AsyncTask<Void, Void, Object>() {
        	protected Object doInBackground(Void... v) {
        	    String url = null;
        	    try {
        	        url = Api.baseUrl(SearchActivity.this)
        	                + "/api/apps/search?q=" + java.net.URLEncoder.encode(q, "UTF-8")
        	                + "&limit=200&offset=0";

        	        String s = Http.getString(url);
        	        if (s == null) return "HTTP returned null\nURL=" + url;

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
        	            a.isGame = o.optBoolean("is_game", false);
        	            if (a.api <= deviceApi) out.add(a);
        	        }
        	        return out;
        	    } catch (Exception e) {
        	        return "URL=" + url + "\n" + e.toString();
        	    }
        	}

            @SuppressWarnings("unchecked")
            protected void onPostExecute(Object out) {
                if (out instanceof String) {
                    Toast.makeText(SearchActivity.this, "Search error: " + out, Toast.LENGTH_LONG).show();
                    return;
                }
                List<AppItem> listOut = (List<AppItem>) out;

                data.clear();
                data.addAll(listOut);
                adapter.notifyDataSetChanged();

                if (listOut.size() == 0) Toast.makeText(SearchActivity.this, R.string.nothing_found, Toast.LENGTH_SHORT).show();
            }
        }.execute();
    }
}
