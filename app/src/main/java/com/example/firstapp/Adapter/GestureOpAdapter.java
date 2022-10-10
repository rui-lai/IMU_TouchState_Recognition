package com.example.firstapp.Adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.firstapp.Bean.Operation;
import com.example.firstapp.R;

import java.util.List;
//整个手势操作列表的适配器
public class GestureOpAdapter extends BaseAdapter {

    public static final String TAG = "GestureAdapter";

    private Context context;
    private List<Operation> operations;

    public GestureOpAdapter(Context context, List<Operation> operations) {
        this.context = context;
        this.operations = operations;
    }

    @Override
    public int getCount() {
        return operations.size();
    }

    @Override
    public Operation getItem(int position) {
        return operations.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressLint("ViewHolder")
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        PackageManager pm = context.getPackageManager();
        convertView = LayoutInflater.from(context).inflate(R.layout.list_item, null);
        TextView gestureName = (TextView) convertView.findViewById(R.id.gestureName);
        Spinner appList = (Spinner)convertView.findViewById(R.id.appList);
        gestureName.setText(getItem(position).getName());
        SpinAdapter spinAdapter = new SpinAdapter(context, getItem(position).getPackageInfos());
        Operation op = getItem(position);
        appList.setAdapter(spinAdapter);
        if (getItem(position).getTargetApp() != null){
            appList.setSelection(getItem(position).getPackageInfos().indexOf(getItem(position).getTargetApp()));
        }
        appList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG,"你点击的是："+op.getName()+"操作："+op.getPackageInfos().get(position).activityInfo.applicationInfo.loadLabel(pm));
                op.setTargetApp(op.getPackageInfos().get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        return convertView;
    }

}
