package com.example.firstapp.Interface;

import androidx.annotation.NonNull;

import java.util.List;
//已弃用
public interface DataProcess {

    String DecToHex(byte dec);

    List<String> ArrayDecToHex(byte[] Dec);

    int HexToDec(String hex);

    List<Integer> ListHexToInt(@NonNull List<String> Hex);

    List<String> mergeStringList(@NonNull List<String> a, @NonNull List<String> b);
}
