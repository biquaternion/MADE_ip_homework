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
    public String ageLabel;
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

        if (age < 20) {
            ageLabel = "under20";
        } else if (age < 30) {
            ageLabel = "20to30";
        } else if (age < 40) {
            ageLabel = "30to40";
        } else if (age < 50) {
            ageLabel = "40to50";
        } else {
            ageLabel = "50+";
        }
    }

    public String toString() {
        String res = "gender: " + genderLabel + "; ethnicity: " + ethnicityLabel + "; age: " + Integer.toString(age);
        return res;
    }
}
