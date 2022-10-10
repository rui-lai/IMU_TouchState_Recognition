package com.example.firstapp.Utilities;
import android.util.Log;
import jama.Matrix;
import jkalman.JKalman;

//该类弃用本来是想用卡尔曼滤波来过滤噪声以便进行手势识别的
public class KalManFilter {

    private final String TAG = "KalmanFilter";

    private JKalman mFilter;
    private Matrix mPredictValue;
    private Matrix mCorrectedValue;
    private Matrix mMeasurementValue;


    public KalManFilter() {
    }


    public void initial(){
        try {
            mFilter = new JKalman(6, 3);

            double x = 0;
            double y = 0;
            double z = 0;

            double dx = 100;
            double dy = 100;
            double dz = 100;

            // init
            mPredictValue = new Matrix(6, 1); // predict state [x, y, dx, dy, dxy]
            mCorrectedValue = new Matrix(6, 1); // corrected state [x, y, dx, dy, dxy]

            mMeasurementValue = new Matrix(3, 1); // measurement [x]
            mMeasurementValue.set(0, 0, x);
            mMeasurementValue.set(1, 0, y);
            mMeasurementValue.set(2, 0, z);

            // transitions for x, y, z, dx, dy, dz
            double[][] tr = {   {1, 0, 0, 100, 0, 0},
                                {0, 1, 0, 0, 100, 0},
                                {0, 0, 1, 0, 0, 100},
                                {0, 0, 0, 1, 0, 0},
                                {0, 0, 0, 0, 1, 0},
                                {0, 0, 0, 0, 0, 1}};
            mFilter.setTransition_matrix(new Matrix(tr));

            // 1s somewhere?
            mFilter.setError_cov_post(mFilter.getError_cov_post().identity());

            // Init first assumption similar to first observation (cheat :)
        } catch (Exception ex) {
            Log.e(TAG, ex.getMessage());
        }
    }


    public float[] filter(float[] oldValue) {
        float[] newValue = new float[3];
        // check state before
        mPredictValue = mFilter.Predict();
        mMeasurementValue.set(0, 0, oldValue[0]);
        mMeasurementValue.set(1, 0, oldValue[1]);
        mMeasurementValue.set(2, 0, oldValue[2]);

        // look better
        mCorrectedValue = mFilter.Correct(mMeasurementValue);

        newValue[0] = (float)mPredictValue.get(0,0);
        newValue[1] = (float)mPredictValue.get(1,0);
        newValue[2] = (float)mPredictValue.get(2,0);

        return newValue;
    }


}
