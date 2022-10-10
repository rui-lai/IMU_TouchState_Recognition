package com.example.firstapp.Service;

import static com.example.firstapp.MainActivity.targetDevice;

import android.app.Instrumentation;
import android.app.Service;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.graphics.ColorSpace;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import com.apkfuns.logutils.LogUtils;
import com.example.firstapp.Bean.IMUBytes;
import com.example.firstapp.Bean.Operation;
import com.example.firstapp.MainActivity;
import com.example.firstapp.Utilities.KalManFilter;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

//提供蓝牙服务、触摸检测模型加载服务以及简单的手势识别服务
public class MyService extends Service {

    public static final String TAG = "MyService";
    private final UUID DEVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private final UUID CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private final int AccelLSB = 16384;
    private final double AngularLSB = 131;
    private final String Module_Name = "true_ds_s1_epoch_1_accuracy_0.919_script.txt";
    private final int dataBlockSize = 120;
    private final int dataLength = 6;

    private byte[] DeviceValue = null;
    private List<float[]> dataBlock;
    private List<Operation> ops;
    private BluetoothGatt mBluetoothGatt;
    private MyBinder mBinder = new MyBinder();
    private IMUBytes imuBytes;
    private Module module;
    private boolean isDist = false;

    float[] accelerationx = new float[2];
    float[] accelerationy = new float[2];
    float[] accelerationz = new float[2];
    float[] velocityx = new float[2];
    float[] velocityy = new float[2];
    float[] velocityz = new float[2];
    float[] positionX = new float[2];
    float[] positionY = new float[2];
    float[] positionZ = new float[2];
    private float sstatex = 5.000f;
    private float sstatey = 0.500f;
    private float sstatez = 9.865457f;
    private float windowlength = 2.5f;
    private float displacementLimit = 2.0f;

    private int sampleNum;
    private int countx;
    private int county;
    private int countz;
    private int gesture = -1;

    private String direction = "";

//    private  KalManFilter kalManFilter;
    private long actualTime;
    private long lastUpdate;

    public static boolean gestureReco;

    private BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if(status == BluetoothGatt.GATT_SUCCESS){
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.d(TAG, "设备连接上 开始扫描服务");
                    // 开始扫描服务，安卓蓝牙开发重要步骤之一
                    if(mBluetoothGatt.discoverServices()){
                        Log.d(TAG,"发现服务成功");
                    }else{
                        Log.d(TAG,"发现服务失败");
                    }
                }
                if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    // 连接断开
                    Log.d(TAG, "设备已断开");
                    /*连接断开后的相应处理*/
                    if(mBluetoothGatt != null){
                        mBluetoothGatt.disconnect();
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                    }
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService mDeviceService;
            BluetoothGattCharacteristic mChar;
            //获取服务列表
            if (status == BluetoothGatt.GATT_SUCCESS){
                mDeviceService = mBluetoothGatt.getService(DEVICE_UUID);
                if (mDeviceService == null){
                    Log.d(TAG, "设备服务为空，断开Gatt");
                    mBluetoothGatt.disconnect();
                    mBluetoothGatt.close();
                    return;
                }
                mChar = mDeviceService.getCharacteristic(CHAR_UUID);
                if (mChar == null){
                    Log.d(TAG, "未找到目标Char");
                    return;
                }else{
                    if(mBluetoothGatt.setCharacteristicNotification(mChar,true)){
                        Log.d(TAG,"读取特征成功");
                        for(BluetoothGattDescriptor dp: mChar.getDescriptors()){
                            if (dp != null) {
                                if ((mChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                    dp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                } else if ((mChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                                    dp.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                                }
                                gatt.writeDescriptor(dp);
                                Log.d(TAG,dp.getUuid().toString());
                            }
                        }
                    }else{
                        Log.d(TAG,"读取特征失败");
                    }
                }
            }else{
                Log.d(TAG, "onServicesDiscovered failed");
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if(characteristic.getUuid().equals(CHAR_UUID)) {
                DeviceValue = characteristic.getValue();
                //Log.d(TAG, Arrays.toString(DeviceValue));
                imuBytes.setRawData(DeviceValue);
                //将原生数据转化成真实的物理数据，只截取前12位
                imuBytes.rawToder(AccelLSB, AngularLSB);
//                imuBytes.MaHonyAHRSupdate();
//                Log.d(TAG, Arrays.toString(imuBytes.getQ()));
//                float[] tempQ = imuBytes.getQ();
//                float pitch =(float) Math.asin(2 * (tempQ[0] * tempQ[2] - tempQ[1] * tempQ[3]));
//                float roll = (float) Math.atan((tempQ[0] * tempQ[3] + tempQ[1] * tempQ[2]) / (1 - 2 * (tempQ[2] * tempQ[2] + tempQ[3] * tempQ[3])));
//                float yaw = (float) Math.atan((tempQ[0] * tempQ[1] + tempQ[2] * tempQ[3]) / (1 - 2 * (tempQ[1] * tempQ[1] + tempQ[2] * tempQ[2])));
//                Log.d(TAG, "pitch:"+pitch+" roll:"+roll+" yaw:"+yaw);
                Intent intent = new Intent();
                intent.setAction(MainActivity.DATA_RECEIVE_ACTION);
                intent.putExtra("rawData", imuBytes.getRawData());
                intent.putExtra("derData", Floats.toArray(imuBytes.getDerData()));
                //intent.putExtra("derData", newvalue);
                //Log.d("Kalman", Floats.toArray(imuBytes.getDerData()));
                sendBroadcast(intent);
                if(isDist){
                    if ((int)Floats.toArray(imuBytes.getDerData())[2] != 0 && (int)Floats.toArray(imuBytes.getDerData())[0] != 0){
                        //Log.d(TAG, Arrays.toString(Floats.toArray(imuBytes.getDerData())));
                        getGesture();
                    }
                    prepareData(imuBytes.getDerData());
                    if(dataBlock.size() == dataBlockSize){
                        float[] data = CopyDataToArray();
                        long[] shape = {1, 1, dataBlockSize, dataLength};
                        Tensor tensor = Tensor.fromBlob(data, shape);
                        IValue input = IValue.from(tensor);
                        Tensor output = module.forward(input).toTensor();
                        float[] predict = output.getDataAsFloatArray();
                        intent.setAction(MainActivity.STATUS_RECOGNIZE);
//                      检测触摸非触摸状态
                        switch (argmax(predict)){
                            case 0:
                                intent.putExtra("status", 0);
                                Log.d(TAG, "NoTouch");
                                break;
                            case 1:
                                intent.putExtra("status", 1);
                                Log.d(TAG, "Touch");
                                Log.d(TAG, gesture+"");
                                if(ops.get(0).getTargetApp() != null && gestureReco){
                                    switch (gesture){
                                        case 0:
                                            Log.d(TAG, "Right");
                                            startOperations(0);
                                            break;
                                        case 1:
                                            Log.d(TAG, "Left");
                                            startOperations(1);
                                            break;
                                        case 2:
                                            Log.d(TAG, "Forward");
//                                            new Thread(new Runnable() {
//                                                @Override
//                                                public void run() {
//                                                    Instrumentation inst = new Instrumentation();
//                                                    inst.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_UP);
//                                                }
//                                            }).start();
                                            startOperations(2);
                                            break;
                                        case 3:
                                            Log.d(TAG, "Back");
//                                            new Thread(new Runnable() {
//                                                @Override
//                                                public void run() {
//                                                    Instrumentation inst = new Instrumentation();
//                                                    inst.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_DOWN);
//                                                }
//                                            }).start();
                                            startOperations(3);
                                            break;
                                        default:
                                            //Log.d(TAG, "Not a Gesture");
                                            break;
                                    }
                                }
                                gesture = -1;
                                break;
                        }
                        sendBroadcast(intent);
                        LogUtils.d(Collections.singletonList(predict));
                    }
                }
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //开启监听成功，可以像设备写入命令了
                Log.e(TAG, "开启监听成功");
            }
        }
    };

    public MyService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        imuBytes = new IMUBytes();
        module = loadModule();
        dataBlock = new ArrayList<float[]>();
//        kalManFilter = new KalManFilter();
//        kalManFilter.initial();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if(mBinder != null){
            return mBinder;
        }
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void startOperations(int id){
        if (ops.get(id).getTargetApp() != null){
            Intent intent = getPackageManager().getLaunchIntentForPackage(ops.get(id).getTargetApp().activityInfo.applicationInfo.packageName);
            if(intent != null){
                intent.putExtra("type", "110");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }
    }

    private Module loadModule() {
        Module module = null;
        if (copyAssetAndWrite(Module_Name)){
            Log.d(TAG , "模型已加载入缓存");
            File module_file = new File(getCacheDir(),Module_Name);
            module = Module.load(module_file.getAbsolutePath());
        }else {
            Log.d(TAG , "模型文件载入缓存失败");
        }
        return module;
    }

    private boolean copyAssetAndWrite(String fileName){
        try {
            File cacheDir=getCacheDir();
            if (!cacheDir.exists()){
                cacheDir.mkdirs();
            }
            File outFile =new File(cacheDir,fileName);
            if (!outFile.exists()){
                boolean res=outFile.createNewFile();
                if (!res){
                    return false;
                }
            }else {
                if (outFile.length()>10){//表示已经写入一次
                    return true;
                }
            }
            InputStream is=getAssets().open(fileName);
            FileOutputStream fos = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int byteCount;
            while ((byteCount = is.read(buffer)) != -1) {
                fos.write(buffer, 0, byteCount);
            }
            fos.flush();
            is.close();
            fos.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private void prepareData(List<Float> derData){
        if (derData != null && derData.size() != 0){
            float[] temp = Floats.toArray(derData);
            if (dataBlock.size() >= dataBlockSize){
                dataBlock.clear();
            }
            dataBlock.add(temp);
        }
    }

    private float[] CopyDataToArray(){
        List<Float> temp = new ArrayList<>();
        if (dataBlock.size() != 0){
            for (int i = 0 ; i < dataBlock.size(); i += 1) {
                float[] a = dataBlock.get(i);
                for (float x: a) {
                    temp.add(x);
                }
            }
        }
        return Floats.toArray(temp);
    }

    private int argmax(float[] input){
        int label = -1;
        float max = 0.f;
        for (int i = 0; i < input.length; i++) {
            if (input[i] >= max){
                max = input[i];
                label = i;
            }
        }
        return label;
    }

    private void getGesture(){
        accelerationx[1] = imuBytes.getDerData().get(0);
        accelerationy[1] = imuBytes.getDerData().get(1);

        accelerationx[1] -= sstatex;
        accelerationy[1] -= sstatey;
        //给定一个窗口大小，这里窗口为4，如果加速度小于这个区间这表明没有移动
        if ((accelerationx[1] <=windowlength)&&(accelerationx[1] >= -windowlength))
        {accelerationx[1] = 0;}

        if ((accelerationy[1] <=windowlength)&&(accelerationy[1] >= -windowlength))
        {accelerationy[1] = 0;}

        //first X integration:
        velocityx[1]= velocityx[0]+ accelerationx[0]+ ((accelerationx[1] - accelerationx[0]) / 2);

        //second X integration:
        positionX[1]= positionX[0] + velocityx[0] + ((velocityx[1] - velocityx[0]) / 2);

        //first Y integration:
        velocityy[1] = velocityy[0] + accelerationy[0] + ((accelerationy[1] - accelerationy[0]) / 2);

        //second Y integration:
        positionY[1] = positionY[0] + velocityy[0] + ((velocityy[1] - velocityy[0]) / 2);

        accelerationx[0] = accelerationx[1];
        accelerationy[0] = accelerationy[1];

        velocityx[0] = velocityx[1];
        velocityy[0] = velocityy[1];

        positionX[0] = positionX[1];
        positionY[0] = positionY[1];
        if (direction == ""){
            if (velocityy[1] < -displacementLimit && accelerationx[1] == 0){
                direction = "右";
                gesture = 0;
                Log.d(TAG , "右");
            }else if (velocityy[1] > displacementLimit && accelerationx[1] == 0){
                direction = "左";
                gesture = 1;
                Log.d(TAG, "左");
            }else if (velocityx[1] < -displacementLimit && accelerationy[1] == 0){
                direction = "前";
                gesture = 2;
                Log.d(TAG, "前");
            }else if(velocityx[1] > displacementLimit && accelerationy[1] == 0) {
                direction = "后";
                gesture = 3;
                Log.d(TAG, "后");
            }
        }
        movement_end_check();
    }

    private void movement_end_check() {
        if (accelerationx[1]==0)         //we count the number of acceleration samples that equals zero
        { countx++;}
        else { countx =0;}
        if (countx>=25)                     //if this number exceeds 25, we can assume that velocity is zero
        {
            velocityx[1]=0;
            velocityx[0]=0;
        }
        if (accelerationy[1]==0)        //we do the same for the Y axis
        { county++;}
        else { county =0;}
        if (county>=25)
        {
            velocityy[1]=0;
            velocityy[0]=0;
        }
        if (velocityx[1]==0&&velocityx[0]==0&&velocityy[1]==0&&velocityy[0]==0){
            direction = "";
        }
    }

    public class MyBinder extends Binder{
        public void connect(){
            Log.d(TAG, "请求蓝牙连接");
            mBluetoothGatt = targetDevice.connectGatt(MyService.this,true,callback);
        }
        public void disconnectDevice(){
            if(mBluetoothGatt != null){
                Log.d(TAG,"请求断开连接");
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }else{
                Log.d(TAG,"尚未连接设备");
            }
        }
//        模型在Service启动时就预加载好了，这里只需要将数据处理好扔进模型就可以了
        public void StartRecognize(){
            isDist = true;
        }
        public void StopRecognize(){
            isDist = false;
        }
        public void SwitchGesture(boolean x){
            gestureReco = x;
        }
        public BluetoothGatt getBLEGatt(){
            return mBluetoothGatt;
        }
        public void setOps(List<Operation> operationList){
            ops = operationList;
        }
    }



}