package com.dk.imgprochw;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dk.imgprochw.db.ImageAnalysisResults;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class MainActivity extends FragmentActivity {

    private static final String TAG = "MainActivity";
    private final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;

    private FaceAttrVisualPreferences preferencesFragment;
    private Photos photosFragment;

    private ProgressBar progressBar;
    private TextView progressBarinsideText;

    private Thread photoProcessingThread = null;
    private Map<String, Long> photosTaken;
    private ArrayList<String> photosFilenames;
    private int currentPhotoIndex = 0;
    private PhotoProcessor photoProcessor = null;
    private boolean processingDone = false;

    private String[] categoryList;

    private List<Map<String, Map<String, Set<String>>>> categoriesHistograms = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        } else {
            init();
        }
    }

    private void init() {
        //checkServerSettings();
        categoryList = getResources().getStringArray(R.array.category_list);

        for (int i = 0; i < categoryList.length; ++i) {
            categoriesHistograms.add(new HashMap<>());
        }

        photoProcessor = PhotoProcessor.getPhotoProcessor(this);
        photosTaken = photoProcessor.getCameraImages();
        photosFilenames = new ArrayList<String>(photosTaken.keySet());
        currentPhotoIndex = 0;

        progressBar = (ProgressBar) findViewById(R.id.progress);
        progressBar.setMax(photosFilenames.size());
        progressBarinsideText = (TextView) findViewById(R.id.progressBarinsideText);
        progressBarinsideText.setText("");


        photoProcessingThread = new Thread(() -> {
            processAllPhotos();
            processingDone = true;
        }, "photo-processing-thread");
        progressBar.setVisibility(View.VISIBLE);

        preferencesFragment = new FaceAttrVisualPreferences();
        Bundle prefArgs = new Bundle();
        prefArgs.putInt("color", Color.GREEN);
        prefArgs.putString("title", "High-Level topCategories");
        preferencesFragment.setArguments(prefArgs);

        photosFragment = new Photos();
        Bundle args = new Bundle();
        args.putStringArray("photosTaken", new String[]{"0"});
        args.putStringArrayList("0", new ArrayList<String>(photoProcessor.getCameraImages().keySet()));
        photosFragment.setArguments(args);
        PreferencesClick(null);

        photoProcessingThread.setPriority(Thread.MIN_PRIORITY);
        photoProcessingThread.start();
    }

    public synchronized List<Map<String, Map<String, Set<String>>>> getCategoriesHistograms() {
        return categoriesHistograms;
    }

    private void processAllPhotos() {
        if (processingDone) {
            return;
        }
        //ImageAnalysisResults previousPhotoProcessedResult=null;
        for (; currentPhotoIndex < photosTaken.size(); ++currentPhotoIndex) {
            String filename = photosFilenames.get(currentPhotoIndex);
            try {
                File file = new File(filename);

                if (file.exists()) {
                    long startTime = SystemClock.uptimeMillis();
                    ImageAnalysisResults res = photoProcessor.getImageAnalysisResults(filename);

                    long endTime = SystemClock.uptimeMillis();
                    Log.d(TAG, "!!Processed: " + filename + " in background thread:" + Long.toString(endTime - startTime));
                    processRecognitionResults(res);
                    final int progress = currentPhotoIndex + 1;
                    runOnUiThread(() -> {
                        if (progressBar != null) {
                            progressBar.setProgress(progress);
                            progressBarinsideText.setText("" + 100 * progress / photosTaken.size() + "%");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "While  processing image" + filename + " exception thrown: " + e);
            }
        }
    }

    private synchronized void processRecognitionResults(ImageAnalysisResults results) {
        String filename = results.filename;

        String location = results.locations.description;
        List<Map<String, Map<String, Set<String>>>> newCategoriesHistograms = deepCopyCategories(categoriesHistograms);

        String gender = results.faceData.genderLabel;
        int genderOffset = 0;
        updateCategory(newCategoriesHistograms, photoProcessor.getHighLevelCategory(gender) + genderOffset, gender, filename);
        String ethnicity = results.faceData.ethnicityLabel;
        int ethnicityOffset = 2;
        updateCategory(newCategoriesHistograms, photoProcessor.getHighLevelCategory(ethnicity) + ethnicityOffset, ethnicity, filename);
        String age = results.faceData.ageLabel;
        int ageOffset = 7;
        updateCategory(newCategoriesHistograms, photoProcessor.getHighLevelCategory(age) + ageOffset, age, filename);


        if (location != null)
            updateCategory(newCategoriesHistograms, newCategoriesHistograms.size() - 1, location, filename);

        categoriesHistograms = newCategoriesHistograms;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                preferencesFragment.updateChart();
            }
        });
    }

    public void PreferencesClick(View view) {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_switch, preferencesFragment);
        fragmentTransaction.commit();
    }

    private void updateCategory(List<Map<String, Map<String, Set<String>>>> histos, int highLevelCategory, String category, String filename) {
        if (category == null) {
            return;
        }
        if (highLevelCategory >= 0) {
            Map<String, Map<String, Set<String>>> histo = histos.get(highLevelCategory);
            if (!histo.containsKey(category)) {
                histo.put(category, new TreeMap<>());
                histo.get(category).put("0", new TreeSet<>());
            }
            histo.get(category).get("0").add(filename);
        }
    }

    private List<Map<String, Map<String, Set<String>>>> deepCopyCategories(List<Map<String, Map<String, Set<String>>>> categories) {
        ArrayList<Map<String, Map<String, Set<String>>>> result = new ArrayList<>(categories.size());
        for (Map<String, Map<String, Set<String>>> m : categories) {
            Map<String, Map<String, Set<String>>> m1 = new HashMap<>(m.size());
            result.add(m1);
            for (Map.Entry<String, Map<String, Set<String>>> me : m.entrySet()) {
                Map<String, Set<String>> m2 = new TreeMap<>(Collections.reverseOrder());
                m1.put(me.getKey(), m2);
                for (Map.Entry<String, Set<String>> map_files : me.getValue().entrySet()) {
                    m2.put(map_files.getKey(), new TreeSet<>(map_files.getValue()));
                }
            }
        }
        return result;
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    getPackageManager()
                            .getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            int status = ContextCompat.checkSelfPermission(this, permission);
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS:
                Map<String, Integer> perms = new HashMap<String, Integer>();
                boolean allGranted = true;
                for (int i = 0; i < permissions.length; i++) {
                    perms.put(permissions[i], grantResults[i]);
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                        allGranted = false;
                }
                // Check for ACCESS_FINE_LOCATION
                if (allGranted) {
                    // All Permissions Granted
                    init();
                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "Some Permission is Denied", Toast.LENGTH_SHORT)
                            .show();
                    finish();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}