package com.example.firstapp.Bean;

import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;

import java.util.List;
//手势识别的操作，记录手势打开的对应系统应用
public class Operation {
    private  String name;
    private List<ResolveInfo> packageInfos;
    private ResolveInfo targetApp;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ResolveInfo> getPackageInfos() {
        return packageInfos;
    }

    public void setPackageInfos(List<ResolveInfo> packageInfos) {
        this.packageInfos = packageInfos;
    }

    public ResolveInfo getTargetApp() {
        return targetApp;
    }

    public void setTargetApp(ResolveInfo targetApp) {
        this.targetApp = targetApp;
    }

    @Override
    public String toString() {
        return "Operation{" +
                "name='" + name + '\'' +
                ", packageInfos=" + packageInfos +
                ", targetApp=" + targetApp +
                '}';
    }
}
