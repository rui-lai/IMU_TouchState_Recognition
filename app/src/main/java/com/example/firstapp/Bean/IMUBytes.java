package com.example.firstapp.Bean;

import android.util.Log;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
//存储IMU数据并将原始数据转换成在真实的物理数据
public class IMUBytes {
    private byte[] rawData;
    private List<Float> derData = new ArrayList<>();

    public void setRawData(byte[] rawData) {
        this.rawData = rawData;
    }

    public byte[] getRawData() {
        return rawData;
    }

    public void setDerData(List<Float> derData) {
        this.derData = derData;
    }

    public List<Float> getDerData() {
        return derData;
    }

    @Override
    public String toString() {
        return "IMUBytes{" +
                "rawData=" + Arrays.toString(rawData) + "\n" +
                "derData=" + derData +
                '}';
    }

    public void rawToder(int AccelLSB, double AngularLSB) {
        derData.clear();
        DecimalFormat dec = new DecimalFormat("0.000");
        //先把所有的数转成无符号范围在0-255
        for (int i = 0; i < 6; i += 2) {
            double data = ((rawData[i + 1] << 8) | rawData[i]) * 9.8 / AccelLSB;
            derData.add(Float.parseFloat(dec.format(data)));
        }
        for (int i = 6; i < 12; i += 2) {
            double data = ((rawData[i + 1] << 8) | rawData[i]) * (Math.PI / 180) / AngularLSB;
            derData.add(Float.parseFloat(dec.format(data)));
        }
    }
}