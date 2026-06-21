package com.oldmarket.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oldmarket.R;
import com.oldmarket.model.AppItem;
import com.oldmarket.net.Api;
import com.oldmarket.util.ImageLoader;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

public class AppListAdapter extends BaseAdapter {
    private final Context c;
    private final List<AppItem> items;
    private final LayoutInflater inf;
    private final Map<String, Integer> installedPackages = new HashMap<String, Integer>();

    public AppListAdapter(Context c, List<AppItem> items) {
        this.c = c;
        this.items = items;
        this.inf = LayoutInflater.from(c);
        refreshInstalledPackages();
    }

    public void refreshInstalledPackages() {
        installedPackages.clear();
        try {
            PackageManager pm = c.getPackageManager();
            List<PackageInfo> list = pm.getInstalledPackages(0);
            for (int i = 0; i < list.size(); i++) {
                PackageInfo pi = list.get(i);
                if (pi != null && pi.packageName != null) {
                    installedPackages.put(pi.packageName, pi.versionCode);
                }
            }
        } catch (Throwable e) { }
    }

    public int getInstalledVersionCode(String packageName) {
        Integer v = installedPackages.get(packageName);
        return v == null ? 0 : v.intValue();
    }

    public int getCount() { return items.size(); }
    public Object getItem(int position) { return items.get(position); }
    public long getItemId(int position) { return items.get(position).id; }

    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) v = inf.inflate(R.layout.list_item_app, parent, false);

        ImageView img = (ImageView) v.findViewById(R.id.img);
        TextView title = (TextView) v.findViewById(R.id.title);
        TextView developer = (TextView) v.findViewById(R.id.subtitle);
        TextView status = (TextView) v.findViewById(R.id.txtStatus);
        RatingBar ratingBar = (RatingBar) v.findViewById(R.id.ratingBar);

        AppItem a = items.get(position);
        title.setText(AppItem.safe(a.name));
        developer.setText(AppItem.safe(a.developer));
        ratingBar.setRating(a.rating);

        String packageName = AppItem.safe(a.packageName);
        boolean installed = packageName.length() > 0 && installedPackages.containsKey(packageName);
        int installedVersionCode = getInstalledVersionCode(packageName);
        a.installedVersionCode = installedVersionCode;

        if (installed && a.versionCode > 0 && installedVersionCode > 0 && a.versionCode > installedVersionCode) {
            status.setText(c.getString(R.string.updates_available));
            status.setTextColor(0xfff28c18);
        } else if (installed) {
            status.setText(c.getString(R.string.installed));
            status.setTextColor(0xff303030);
        } else {
            status.setText(c.getString(R.string.free));
            status.setTextColor(0xff303030);
        }

        String iconUrl = (a.icon == null) ? "" : Api.iconUrl(c, a.icon);
        ImageLoader.load(c, iconUrl, img, R.drawable.icon_placeholder);

        return v;
    }
}
