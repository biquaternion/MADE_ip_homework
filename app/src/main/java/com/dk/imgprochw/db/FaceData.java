package com.dk.imgprochw.db;

import android.view.ViewDebug;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Created by avsavchenko.
 */

public class FaceData implements ClassifierResult, Serializable {
    public String genderLabel;
    public float genderScore;
    public String ethnicityLabel;
    public float ethnicityScore;
    public int age;
    public float ageScore;

    public FaceData() {
    }

    public FaceData(String genderLabel, float genderScore,
                    String ethnicityLabel, float ethnicityScore,
                    int age, float ageScore) {
        this.genderLabel = genderLabel;
        this.genderScore = genderScore;
        this.ethnicityLabel = ethnicityLabel;
        this.ethnicityScore = ethnicityScore;
        this.age = age;
        this.ageScore = ageScore;
    }

    public String toString() {
        String res = "gender: " + genderLabel + "; ethnicity: " + ethnicityLabel + "; age: " + Integer.toString(age);
        return res;
    }
}
