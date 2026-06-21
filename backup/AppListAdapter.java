package com.oldmarket.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final Set<String> installedPackages = new HashSet<String>();

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
                if (pi != null && pi.packageName != null) installedPackages.add(pi.packageName);
            }
        } catch (Throwable e) { }
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
        boolean installed = packageName.length() > 0 && installedPackages.contains(packageName);
        status.setText(installed ? c.getString(R.string.installed) : c.getString(R.string.free));

        String iconUrl = (a.icon == null) ? "" : Api.iconUrl(c, a.icon);
        ImageLoader.load(c, iconUrl, img, R.drawable.icon_placeholder);

        return v;
    }
}
