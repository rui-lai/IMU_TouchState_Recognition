package com.example.firstapp.Adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.example.firstapp.R;

import java.util.List;
//手势操作列表中每一项的适配器
public class SpinAdapter extends BaseAdapter implements SpinnerAdapter {
    private Context context;
    private List<ResolveInfo> appInfoList;

    public SpinAdapter(Context context, List<ResolveInfo> appInfoList) {
        this.context = context;
        this.appInfoList = appInfoList;
    }

    @Override
    public int getCount() {
        return appInfoList.size();
    }

    @Override
    public ResolveInfo getItem(int position) {
        return appInfoList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressLint("ViewHolder")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        PackageManager pm = context.getPackageManager();
        convertView = LayoutInflater.from(context).inflate(R.layout.spinner_item, null);
        String label = getItem(position).activityInfo.applicationInfo.loadLabel(pm)+"";
        Drawable d = pm.getApplicationIcon(getItem(position).activityInfo.applicationInfo);
        ImageView icon = (ImageView) convertView.findViewById(R.id.appIcon);
        TextView name = (TextView) convertView.findViewById(R.id.appName);
        icon.setImageDrawable(d);
        name.setText(label);
        return convertView;
    }
}
