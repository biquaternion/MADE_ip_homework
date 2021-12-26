package com.dk.imgprochw;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.dk.imgprochw.db.ClassifierResult;
import com.dk.imgprochw.db.FaceData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class FaceAttrWrapper {
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "FaceAttrTfLiteClassifier";

    //    private static final String GENDER_MODEL_FILENAME = "model_lite_gender_q.tflite";
    private static final String GENDER_MODEL_FILENAME = "age_gender_ethnicity_224_deep-03-0.13-0.97-0.88.tflite";
    private static final String AGE_MODEL_FILENAME = "model_age_q.tflite";

    private static final String GENDER_ATTR_FILENAME = "gender_attr_dict.txt";
    private static final String ETHNICITY_ATTR_FILENAME = "ethnicity_attr_dict.txt";
    private static final String AGE_ATTR_FILENAME = "age_attr_dict.txt";

    public TreeMap<String, Integer> genderLabels2Index = new TreeMap<>();
    private ArrayList<String> genderLabels = new ArrayList<String>();
    public TreeMap<String, Integer> ethnicityLabels2Index = new TreeMap<>();
    private ArrayList<String> ethnicityLabels = new ArrayList<String>();
    public TreeMap<String, Integer> ageLabels2Index = new TreeMap<>();
    private ArrayList<String> ageLabels = new ArrayList<String>();
    private Map<String, Integer> labels2HighLevelCategories = new HashMap<>();
    private Set<Integer> filteredIndices = new HashSet<>();

    public TreeMap<String, Integer> eventLabels2Index = new TreeMap<>();
    private ArrayList<String> eventLabels = new ArrayList<String>();
    TfLiteClassifier genderClassifier;

    public FaceAttrWrapper(final Context context) throws IOException {
        genderClassifier = new TfLiteClassifier(context, GENDER_MODEL_FILENAME);

        BufferedReader br = null;

        try {
            br = new BufferedReader(new InputStreamReader(context.getAssets().open(GENDER_ATTR_FILENAME)));
            String line;
            int line_ind = 0;
            while ((line = br.readLine()) != null) {
                ++line_ind;
                line = line.split("#")[0].trim(); // skip comment
                String[] categoryInfo = line.split("=");
                String category = categoryInfo[0]; // category name (male/female)
                genderLabels.add(category);

                int highLevelCategory = Integer.parseInt(categoryInfo[1]); // category label
                labels2HighLevelCategories.put(category, highLevelCategory);
            }
            br.close();

            TreeSet<String> labelsSorted = new TreeSet<>();
            for (int i = 0; i < genderLabels.size(); ++i) {
                if (filteredIndices.contains(i))
                    continue;
                String gender = genderLabels.get(i);
                if (!labelsSorted.contains(gender))
                    labelsSorted.add(gender);
            }

            int index = 0;
            for (String label : labelsSorted) {
                genderLabels2Index.put(label, index);
                ++index;
            }
        } catch (IOException e) {
            throw new RuntimeException("Problem reading gender label file!", e);
        }
        try {
            br = new BufferedReader(new InputStreamReader(context.getAssets().open(ETHNICITY_ATTR_FILENAME)));
            String line;
            int line_ind = 0;
            while ((line = br.readLine()) != null) {
                ++line_ind;
                line = line.split("#")[0].trim(); // skip comment
                String[] categoryInfo = line.split("=");
                String category = categoryInfo[0]; // category name (male/female)
                ethnicityLabels.add(category);

                int highLevelCategory = Integer.parseInt(categoryInfo[1]); // category label
                labels2HighLevelCategories.put(category, highLevelCategory);
            }
            br.close();

            TreeSet<String> labelsSorted = new TreeSet<>();
            for (int i = 0; i < ethnicityLabels.size(); ++i) {
                if (filteredIndices.contains(i))
                    continue;
                String ethnicity = ethnicityLabels.get(i);
                if (!labelsSorted.contains(ethnicity))
                    labelsSorted.add(ethnicity);
            }

            int index = 0;
            for (String label : labelsSorted) {
                ethnicityLabels2Index.put(label, index);
                ++index;
            }
        } catch (IOException e) {
            throw new RuntimeException("Problem reading ethnicity label file!", e);
        }
        try {
            br = new BufferedReader(new InputStreamReader(context.getAssets().open(AGE_ATTR_FILENAME)));
            String line;
            int line_ind = 0;
            while ((line = br.readLine()) != null) {
                ++line_ind;
                line = line.split("#")[0].trim(); // skip comment
                String[] categoryInfo = line.split("=");
                String category = categoryInfo[0]; // category name (male/female)
                ageLabels.add(category);

                int highLevelCategory = Integer.parseInt(categoryInfo[1]); // category label
                labels2HighLevelCategories.put(category, highLevelCategory);
            }
            br.close();

            TreeSet<String> labelsSorted = new TreeSet<>();
            for (int i = 0; i < ageLabels.size(); ++i) {
                if (filteredIndices.contains(i))
                    continue;
                String ethnicity = ageLabels.get(i);
                if (!labelsSorted.contains(ethnicity))
                    labelsSorted.add(ethnicity);
            }

            int index = 0;
            for (String label : labelsSorted) {
                ageLabels2Index.put(label, index);
                ++index;
            }
        } catch (IOException e) {
            throw new RuntimeException("Problem reading age label file!", e);
        }
    }

    private TreeMap<String, Float> getCategory2Score(float[] predictions, ArrayList<String> labels, boolean filter) {
        TreeMap<String, Float> category2Score = new TreeMap<>();
        for (int i = 0; i < predictions.length; ++i) {
            if (filter && filteredIndices.contains(i))
                continue;
            String categoryName = labels.get(i);
            float score = predictions[i];
            if (category2Score.containsKey(categoryName)) {
                score += category2Score.get(categoryName);
            }
            category2Score.put(categoryName, score);
        }
        return category2Score;
    }

    public int getHighLevelCategory(String category) {
        int res = -1;
        if (labels2HighLevelCategories.containsKey(category))
            res = labels2HighLevelCategories.get(category);
        return res;
    }

    public FaceData classifyFrame(Bitmap srcBmp) {
        int age_idx = 0;
        int gender_idx = 1;
        int ethnicity_idx = 2;
        float[][][] outputs = genderClassifier.classifyFrame(srcBmp);

        int age = 0;
        float ageScore = 0.0f;
        float[] age_outputs = outputs[age_idx][0];
        for (int i = 0; i != age_outputs.length; ++i) {
            if (age_outputs[age] < age_outputs[i]) {
                age = i;
                ageScore = age_outputs[age];
            }
        }

        float[] genderOutputs = new float[2];

        int maleIdx = 0;
        int femaleIdx = 1;
        genderOutputs[maleIdx] = outputs[gender_idx][0][0];
        genderOutputs[femaleIdx] = 1.0f - genderOutputs[maleIdx];
        String genderLabel = genderLabels.get(maleIdx);
        float genderScore = genderOutputs[maleIdx];
        if (genderOutputs[femaleIdx] > genderOutputs[maleIdx]) {
            genderLabel = genderLabels.get(femaleIdx);
            genderScore = genderOutputs[femaleIdx];
        }

        float[] ethnicityOutputs = outputs[2][0];

        float ethnicityScore = 0.0f;
        String ethnicityLabel = ethnicityLabels.get(0);
        for (int i = 0; i < ethnicityOutputs.length; ++i) {
            if (ethnicityOutputs[i] > ethnicityScore) {
                ethnicityScore = ethnicityOutputs[i];
                ethnicityLabel = ethnicityLabels.get(i);
            }
        }

        return new FaceData(genderLabel, genderScore, ethnicityLabel, ethnicityScore, age, ageScore);
    }

    public int getImageSizeX() {
        return genderClassifier.getImageSizeX();
    }
    public int getImageSizeY() {
        return genderClassifier.getImageSizeY();
    }
}
