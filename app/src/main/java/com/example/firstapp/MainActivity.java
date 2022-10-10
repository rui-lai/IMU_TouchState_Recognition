package com.example.firstapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.test.internal.util.LogUtil;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.apkfuns.logutils.LogUtils;
import com.example.firstapp.Adapter.GestureOpAdapter;
import com.example.firstapp.Bean.Operation;
import com.example.firstapp.Service.MyService;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Floats;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import com.victor.loading.book.BookLoading;

import org.apache.commons.lang3.ArrayUtils;
import org.pytorch.Module;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private final String DATA_SAVE = Environment.DIRECTORY_DOWNLOADS;
    private final int REQUEST_ENABLE_BT = 0xa01;
    private final int PERMISSION_REQUEST_COARSE_LOCATION = 0xb01;
    private final String TAG = "lairui";
    private final String deviceName = "wireless wearable ring";
    private final String[] authList = {Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.BLUETOOTH,Manifest.permission.BLUETOOTH_ADMIN,Manifest.permission.ACCESS_FINE_LOCATION};
    private final String[] gestures = {"Right", "Left", "Forward", "Back"};

    private byte[] DeviceValue = null;

    private TextView display;
    private TextView dialogContent;
    private TextView consequence;
    private EditText exportFile;
    private EditText dataSize;
    private ImageView setting;
    private CheckBox touchStatus;
    private  Button initDiscovery;
    private Button export;
    private Button close;
    private ToggleButton distinguish;
    private ToggleButton connectChange;
    private BookLoading progressBar;
    private AlertDialog mAlertDialog;
    private ListView listView;
    private SwitchCompat gestureSwitch;
    private SwitchCompat socketService;
    private BluetoothAdapter mBluetoothAdapter;
    private List<List<Float>> imuBytesList;
    private List<ResolveInfo> apps;
    private List<Operation> operations;
    private float[] derData;
    private boolean canExport = false;
    private String fileId;
    private String dataNum;
    private boolean touchStat;
    private boolean connectStat;
    private boolean distStat;
    public static BluetoothDevice targetDevice;
    public static String DATA_RECEIVE_ACTION = "com.example.firstapp.Service.MyService.DataReceive";
    public static String STATUS_RECOGNIZE = "com.example.firstapp.Service.MyService.Status_Recognize";
    private MyService.MyBinder mBinder;
    private GestureOpAdapter gestureOpAdapter;
    private BottomSheetDialog bottomSheetDialog;
    private BottomSheetBehavior mDialogBehavior;
    private BufferedOutputStream bw;
    private BluetoothLeScanner mBleScanner;
    private ScanFilter mScanFilter;
    private List<ScanFilter> scanFilterList;
    private ServerSocket serverSocket;
    private Thread openSocket;
    private Thread dataThread;
    private boolean isStop = false;
    private boolean gestureReco = false;
    private boolean isScanning = false;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBinder = (MyService.MyBinder)service;
            mBinder.connect();
            mBinder.setOps(operations);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    private ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType,result);
            Log.d(TAG,"发现目标设备");
            targetDevice = result.getDevice();
            display.setText("设备名:" + deviceName + "\n设备地址:" + targetDevice.getAddress());
            mBleScanner.stopScan(this);
            Log.d(TAG,"蓝牙扫描结束");
            hideWaitDialog();
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(MainActivity.this, "扫描设备失败"+errorCode, Toast.LENGTH_SHORT).show();
            mBleScanner.stopScan(this);
            Log.d(TAG,"蓝牙扫描结束");
            hideWaitDialog();
        }
    };

    /*关闭接收发现蓝牙设备,接收从Service发来的数据*/
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.d(TAG, "开始扫描...");
            }

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && device.getName() != null) {
                    if (device.getName().equals(deviceName)) {
                        Log.d(TAG,"发现目标设备");
                        targetDevice = device;
                        display.setText("设备名:" + targetDevice.getName() + "\n设备地址:" + targetDevice.getAddress());
                        mBluetoothAdapter.cancelDiscovery();
                        hideWaitDialog();
                    }
                }
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                //mAdapter.notifyDataSetChanged();
                Log.d(TAG, "扫描结束.");
                if (targetDevice == null){
                    Toast.makeText(MainActivity.this,"未发现目标设备", Toast.LENGTH_SHORT).show();
                }
                hideWaitDialog();
            }

            if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)){
                Log.d(TAG, "连接状态改变");
            }

            if (DATA_RECEIVE_ACTION.equals(action)){
                //从Service接收数据，这里有可能出错，因为类型转换的问题
                DeviceValue = intent.getByteArrayExtra("rawData");
                derData = intent.getFloatArrayExtra("derData");
                //Log.d(TAG, Arrays.toString(derData));
                if (bw != null && !isStop){
                     dataThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                bw.write(DeviceValue);
                                bw.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                     dataThread.start();
                }
                if(derData != null && derData.length > 0){
                    dialogContent.setText(Arrays.toString(derData));
                    if (canExport){
                        saveDataToCache(Arrays.asList(ArrayUtils.toObject(derData)));
                    }
                }
            }

            if(STATUS_RECOGNIZE.equals(action)){
                switch (intent.getIntExtra("status", -1)){
                    case 0:
                        consequence.setText("NoTouch");
                        break;
                    case 1:
                        consequence.setText("Touch");
                        break;
                    default:
                        consequence.setText("Not a Gesture");
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG,"存储地址："+DATA_SAVE);
        //初始化文本框
        display = this.findViewById(R.id.display);
        //初始化按钮
        initDiscovery = this.findViewById(R.id.initDiscovery);
        setting = this.findViewById(R.id.setting);
        gestureSwitch = this.findViewById(R.id.gestureSwitch);
        socketService = this.findViewById(R.id.socket);
        //初始化进度条
        progressBar = this.findViewById(R.id.loading);
        //初始化手势操作设置列表
        imuBytesList = new ArrayList<List<Float>>();
        scanFilterList = new ArrayList<>();
        //申请权限
        requestAuth();
        getAccessOfFileSyatem();
        //获取系统应用
        getGestureList();
        RelativeLayout.LayoutParams params= (RelativeLayout.LayoutParams) initDiscovery.getLayoutParams();

        params.width= (int)(progressBar.getLayoutParams().width * (1 / 3.0f));//设置当前控件布局的高度

        initDiscovery.setLayoutParams(params);//将设置好的布局参数应用到控件中

        // 注册广播接收器。
        // 接收蓝牙发现
        IntentFilter filterFound = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filterFound);

        IntentFilter filterStart = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        registerReceiver(mReceiver, filterStart);

        IntentFilter filterFinish = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filterFinish);

        IntentFilter filterDataReveive = new IntentFilter(DATA_RECEIVE_ACTION);
        registerReceiver(mReceiver, filterDataReveive);

        IntentFilter filterStatusRecognize = new IntentFilter(STATUS_RECOGNIZE);
        registerReceiver(mReceiver, filterStatusRecognize);
        /*设置监听*/
        setting.setOnClickListener(this);
        initDiscovery.setOnClickListener(this);
        display.setOnClickListener(this);
        gestureSwitch.setOnCheckedChangeListener(this);
        socketService.setOnCheckedChangeListener(this);
        gestureOpAdapter = new GestureOpAdapter(this, operations);
    }

    /*动态申请权限*/
    public void requestAuth(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[2] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[3] == PackageManager.PERMISSION_GRANTED) {

                }

                break;
        }
    }

    // 申请打开蓝牙请求的回调
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "蓝牙已经开启", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "没有蓝牙权限", Toast.LENGTH_SHORT).show();
            }
        }else{
            Log.d(TAG,"蓝牙工作异常！");
        }
    }

    /*按键点击事件*/
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.initDiscovery:
                if (!isScanning){
                    discovery();
                }else{
                    stopDiscovery();
                }
                break;
            case R.id.display:
                if (targetDevice != null){
                    showOperatorDialog();
                }
                break;
            case R.id.setting:
                bottomSheet();
                break;
            default:
                break;
        }
    }

    private void bottomSheet(){
        if (bottomSheetDialog == null){
            View view = View.inflate(getApplicationContext(), R.layout.setting_list, null);
            listView = view.findViewById(R.id.gestureList);
            if(operations.size() != 0){
                listView.setAdapter(gestureOpAdapter);
            }
            bottomSheetDialog = new BottomSheetDialog(this, R.style.BottomSheetDialog);
            //设置点击dialog外部不消失
            bottomSheetDialog.setCanceledOnTouchOutside(true);
            //核心代码 解决了无法去除遮罩问题
            bottomSheetDialog.getWindow().setDimAmount(0f);
            //设置布局
            bottomSheetDialog.setContentView(view);
            //用户行为
            mDialogBehavior = BottomSheetBehavior.from((View) view.getParent());
        }
        bottomSheetDialog.show();
        //重新用户的滑动状态
        mDialogBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                //监听BottomSheet状态的改变
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetDialog.dismiss();
                    mDialogBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                //监听拖拽中的回调，根据slideOffset可以做一些动画
            }
        });


    }

    /**
     * 计算高度(初始化可以设置默认高度)
     */
    private int getWindowHeight() {
        Resources res = this.getResources();
        DisplayMetrics displayMetrics = res.getDisplayMetrics();
        //设置弹窗高度为屏幕高度的3/4
        return displayMetrics.heightPixels;
    }

    private int getWindowWidth(){
        Resources res = this.getResources();
        DisplayMetrics displayMetrics = res.getDisplayMetrics();
        //设置弹窗高度为屏幕高度的3/4
        return displayMetrics.widthPixels;
    }

    /*初始化蓝牙设备*/
    private void initBLE(){
        //showWaitDialog();
        Toast.makeText(this, "蓝牙设备初始化", Toast.LENGTH_SHORT).show();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // 检查设备是否支持蓝牙设备
        if (mBluetoothAdapter == null) {
            Log.d(TAG, "设备不支持蓝牙");
            // 不支持蓝牙，退出。
            return;
        }
        // 如果用户的设备没有开启蓝牙，则弹出开启蓝牙设备的对话框，让用户开启蓝牙
        if (!mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "请求用户打开蓝牙");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            // 接下去，在onActivityResult回调判断
        }
    }

    /*发现蓝牙设备*/
    private void discovery(){
        showWaitDialog();
        display.setText("");
        Toast.makeText(this, "开始发现蓝牙", Toast.LENGTH_SHORT).show();
        if(mBluetoothAdapter == null){
            initBLE();
        }
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()){
            mBleScanner = mBluetoothAdapter.getBluetoothLeScanner();
            mScanFilter = new ScanFilter.Builder()
                    .setDeviceName(deviceName)
                    .build();
            scanFilterList.clear();
            scanFilterList.add(mScanFilter);
            ScanSettings scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                    .build();
            isScanning = true;
            mBleScanner.startScan(scanFilterList,scanSettings ,scanCallback);
            Log.d(TAG,"正在启动蓝牙发现");
        }
    }

    private void stopDiscovery(){
        if (mBleScanner != null){
            Log.d(TAG,"取消蓝牙扫描");
            mBleScanner.stopScan(scanCallback);
            hideWaitDialog();
            isScanning = false;
        }
    }

    private void DialogInit(){
        dialogContent = mAlertDialog.findViewById(R.id.tv_dialog);
        consequence = mAlertDialog.findViewById(R.id.consequence);
        exportFile = mAlertDialog.findViewById(R.id.exportFile);
        touchStatus = mAlertDialog.findViewById(R.id.touchStatus);
        dataSize = mAlertDialog.findViewById(R.id.dataSize);
        export = mAlertDialog.findViewById(R.id.export);
        close = mAlertDialog.findViewById(R.id.dismiss);
        distinguish = mAlertDialog.findViewById(R.id.module);
        connectChange = mAlertDialog.findViewById(R.id.connect_stat_toggle);
        //模型暂未训练完成所以实时检测模块暂不开放
        export.setEnabled(connectStat);
        distinguish.setEnabled(connectStat);
        exportFile.setText(fileId);
        dataSize.setText(dataNum);
        touchStatus.setChecked(touchStat);
        connectChange.setChecked(connectStat);
        distinguish.setChecked(distStat);
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fileId = exportFile.getText().toString();
                if (!fileId.equals("")){
                    Toast.makeText(MainActivity.this, "开始采集数据……", Toast.LENGTH_SHORT).show();
                    fileId = exportFile.getText().toString();
                    touchStat = touchStatus.isChecked();
                    dataNum = dataSize.getText().toString();
                    if (!dataNum.equals("")){
                        if (Integer.parseInt(dataNum) >= 0){
                            exportFile.setEnabled(false);
                            touchStatus.setEnabled(false);
                            dataSize.setEnabled(false);
                            //导出时不允许关闭弹窗,不允许断开连接
                            close.setEnabled(false);
                            connectChange.setEnabled(false);
                            canExport = true;
                        }
                        else{
                            Toast.makeText(MainActivity.this, "导出数量不可小于0", Toast.LENGTH_SHORT).show();
                            canExport = false;
                        }
                    }
                    else{
                        Toast.makeText(MainActivity.this, "请设置导出数量", Toast.LENGTH_SHORT).show();
                        canExport = false;
                    }
                }else{
                    Toast.makeText(MainActivity.this, "请设置导出文件编号", Toast.LENGTH_SHORT).show();
                    canExport = false;
                }
            }
        });
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAlertDialog.dismiss();
            }
        });
        connectChange.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView != null && buttonView.getId() == R.id.connect_stat_toggle){
                    if (buttonView.isChecked()){
                        Log.d(TAG, "连接设备"+targetDevice.getName());
                        connectDevice();
                    }
                    else {
                        Log.d(TAG, "断开设备"+targetDevice.getName());
                        dialogContent.setText("");
                        disconnectDevice();
                    }
                }
            }
        });
        distinguish.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView != null && buttonView.getId() == R.id.module){
                    if (buttonView.isChecked()){
                        Log.d(TAG, "开始识别");
                        distStat = true;
                        mBinder.StartRecognize();
                        if (operations.get(0).getTargetApp() == null){
                            Toast.makeText(MainActivity.this, "请先设置操作", Toast.LENGTH_SHORT).show();
                        }
                    }else{
                        Log.d(TAG, "结束识别");
                        distStat = false;
                        mBinder.StopRecognize();
                        consequence.setText("");
                    }
                }
            }
        });
    }

    private void showOperatorDialog(){
        View view = View.inflate(getApplicationContext(), R.layout.dialog_layout, null);
        mAlertDialog = new AlertDialog.Builder(this)
                .setView(view)
                .setTitle(targetDevice.getName())
                .create();
        mAlertDialog.show();
        mAlertDialog.setCanceledOnTouchOutside(false);
        DialogInit();
    }

    /*连接蓝牙设备*/
    private void connectDevice(){
        if(targetDevice != null && !display.getText().equals("")){
            //mBluetoothGatt = targetDevice.connectGatt(this,true,callback);
            Intent connect = new Intent(MainActivity.this, MyService.class);
            bindService(connect, mConnection, BIND_AUTO_CREATE);
            connectStat = true;
            distinguish.setEnabled(true);
            export.setEnabled(true);
        }
    }

    /**
     * Diconnect Device
     */
    private void disconnectDevice(){
        Intent disconnect = new Intent(MainActivity.this, MyService.class);
        connectStat = false;
        export.setEnabled(false);
        distinguish.setEnabled(false);
        distinguish.setChecked(false);
        mBinder.disconnectDevice();
        unbindService(mConnection);
        imuBytesList.clear();
        dialogContent.setText("");
        consequence.setText("");
    }

    /**
     * Show the Wait Dialog
     */
    private void showWaitDialog(){
        progressBar.start();
        initDiscovery.setText("取消发现");
    }

    /**
     * Hide the Waiting Dialog
     */
    private void hideWaitDialog(){
        progressBar.stop();
        initDiscovery.setText("发现设备");
    }

    private void getAccessOfFileSyatem(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                Environment.isExternalStorageManager()) {
            Toast.makeText(this, "已获得访问所有文件权限", Toast.LENGTH_SHORT).show();
        } else {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                    .setMessage("本程序需要您同意允许访问所有文件权限")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new  Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivity(intent);
                        }
                    });
            dialog.show();
        }
    }

    /**
     * 保存数据到csv文件
     */
    private void exportToCSV() throws CsvRequiredFieldEmptyException, CsvDataTypeMismatchException, IOException {
        if (imuBytesList.size()<=0){
            Log.d(TAG,"没有数据要导出");
        }else{
            File extDir = Environment.getExternalStorageDirectory();
            String fileName = "imuData_";
            if (touchStat){
                fileName += "touch_"+fileId+".csv";
            }
            else {
                fileName += "notouch_"+fileId+".csv";
            }
            File file = new File(extDir,fileName);
            file.createNewFile();
            file.setWritable(true);
            CSVWriter writer = new CSVWriter(new FileWriter(file));
            for (List<Float> data : imuBytesList) {
                String s = data.toString();
                String[] entries = s.substring(1,s.length()-1).split(", ");
                writer.writeNext(entries);
            }
            writer.close();
        }
    }

    /**
     * save the data to a list which has a limit of 500
     */
    private void saveDataToCache(List<Float> imuByte){
        if (imuBytesList != null){
            if (imuBytesList.size() < Integer.parseInt(dataNum)){
                imuBytesList.add(imuByte);
            }else {
                try {
                    exportToCSV();
                } catch (CsvRequiredFieldEmptyException | CsvDataTypeMismatchException | IOException e) {
                    e.printStackTrace();
                    Log.d(TAG,e.getMessage());
                }
                canExport = false;
                imuBytesList.clear();
                exportFile.setEnabled(true);
                touchStatus.setEnabled(true);
                close.setEnabled(true);
                connectChange.setEnabled(true);
                Toast.makeText(this, "数据已导出", Toast.LENGTH_SHORT).show();
            }
        }else{
            Log.d(TAG,"请先初始化缓存队列");
        }
    }

    private void getGestureList() {
        PackageManager pm = getPackageManager();
        Intent filterIntent = new Intent(Intent.ACTION_MAIN, null);
        filterIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        apps = pm.queryIntentActivities(filterIntent, 0);
        operations = new ArrayList<>();
        for (String s:gestures) {
            Operation op = new Operation();
            op.setName(s);
            op.setPackageInfos(apps);
            operations.add(op);
        }
    }

    private void OpenApp(){
        Intent intent = getPackageManager().getLaunchIntentForPackage(operations.get(0).getTargetApp().activityInfo.applicationInfo.packageName);
        if(intent != null){
            intent.putExtra("type", "110");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }


    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()){
            case R.id.gestureSwitch:
                MyService.gestureReco = buttonView.isChecked();
                Log.d(TAG, "点击了gestureSwitch:"+gestureReco);
                break;
            case R.id.socket:
                if (buttonView.isChecked()){
                    openSocket = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                serverSocket = new ServerSocket(3000);
                                while(!isStop){
                                    Log.d(TAG, "等待设备连接");
                                    Socket client = serverSocket.accept();
                                    if (client.isConnected()){
                                        String address = client.getRemoteSocketAddress().toString();
                                        Log.d(TAG,"连接成功，连接的设备为:"+address);
                                        OutputStream o=client.getOutputStream();
                                        bw = new BufferedOutputStream(o);
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                Log.d(TAG, e.getMessage());
                            }
                        }
                    });
                    openSocket.start();
                }else{
                    isStop = true;
                    try {
                        if(bw != null) {
                            bw.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
        }
    }

}