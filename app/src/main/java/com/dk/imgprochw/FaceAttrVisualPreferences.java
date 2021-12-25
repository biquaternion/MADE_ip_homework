package com.dk.imgprochw;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FaceAttrVisualPreferences extends Fragment implements OnChartValueSelectedListener {
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "FaceAttrVisualPreferences";

    protected MainActivity mainActivity;
    protected HorizontalBarChart chart = null;

    protected int color, categoryPosition;
    private Button backButton;

    private int[] clut;
    protected String[] categoryList;

    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preferences, container, false);
    }

    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        categoryList = getResources().getStringArray(R.array.category_list);

        clut = new int[categoryList.length - 1];
        float[] hsv = {0, 1, 1};
        for (int i = 0; i < categoryList.length - 1; ++i) {
            hsv[0] = 360.0f * i / (categoryList.length - 1);
            clut[i] = Color.HSVToColor(hsv);
            //Log.i(TAG,"Init color:"+clut[i]+" from hue "+hsv[0]);
        }

//        super.onViewCreated(view, savedInstanceState);
        color = getArguments().getInt("color", Color.BLACK);
        categoryPosition = getArguments().getInt("position", 0);
        String title = getArguments().getString("title", "");

        mainActivity = (MainActivity) getActivity();

        TextView titleText = (TextView) view.findViewById(R.id.title_text);
        titleText.setText(title);

        chart = (HorizontalBarChart) view.findViewById(R.id.rating_chart);
        chart.setPinchZoom(false);
        chart.setDrawValueAboveBar(true);
        Description descr = new Description();
        descr.setText("");
        chart.setDescription(descr);
        XAxis xAxis = chart.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setEnabled(true);
        xAxis.setDrawAxisLine(false);

        YAxis yRight = chart.getAxisRight();
        yRight.setDrawAxisLine(true);
        yRight.setDrawGridLines(false);
        yRight.setEnabled(false);

        chart.getAxisLeft().setAxisMinimum(0);

        backButton = (Button) view.findViewById(R.id.back_hl_prefs_button);
        backButton.setVisibility((getFragmentManager().getBackStackEntryCount() > 0) ? View.VISIBLE : View.GONE);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager fm = getFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    Log.d(TAG, "popping backstack");
                    fm.popBackStackImmediate();
                }
            }
        });

        chart.setOnChartValueSelectedListener(this);
        updateChart();
    }

    protected List<Map<String, Map<String, Set<String>>>> getCategoriesHistograms() {
        return mainActivity.getCategoriesHistograms();
    }

    @Override
    public void onValueSelected(Entry entry, Highlight highlight) {
        //Log.d(TAG, "Value selected "+entry.toString());
        BarEntry barEntry = (BarEntry) entry;
        IAxisValueFormatter formatter = chart.getXAxis().getValueFormatter();
        if (formatter != null) {
            String category = formatter.getFormattedValue(entry.getX(), null);
            //Toast.makeText(getActivity(), category + " stack=" + highlight.getStackIndex(), Toast.LENGTH_SHORT).show();
            int pos = -1;
            for (int i = 0; i < categoryList.length; ++i) {
                if (categoryList[i] == category) {
                    pos = i;
                    break;
                }
            }
            if (mainActivity == null || pos == -1)
                return;
            FragmentManager fm = getFragmentManager();
            //
            List<Map<String, Map<String, Set<String>>>> categoriesHistograms = getCategoriesHistograms();
            Map<String, Set<String>> fileLists = null;
            if (pos < categoriesHistograms.size()) {
                Map<String, Map<String, Set<String>>> cat_files = categoriesHistograms.get(pos);
                if (cat_files.containsKey(category)) {
                    fileLists = cat_files.get(category);
                }
            }
            if (fileLists != null && !fileLists.isEmpty()) {
                Photos photosFragment = new Photos();
                Bundle args = new Bundle();
                String[] titles = new String[fileLists.size()];
                int i = 0;
                for (Map.Entry<String, Set<String>> category2fileList : fileLists.entrySet()) {
                    titles[i] = category2fileList.getKey();
                    if (Character.isDigit(titles[i].charAt(0)))
                        titles[i] = String.valueOf(i + 1);
                    args.putStringArrayList(titles[i], new ArrayList<String>(category2fileList.getValue()));
                    ++i;
                }
                args.putStringArray("photosTaken", titles);
                photosFragment.setArguments(args);
                FragmentTransaction fragmentTransaction = fm.beginTransaction();
                fragmentTransaction.replace(R.id.fragment_switch, photosFragment);
                fragmentTransaction.addToBackStack(null);
                fragmentTransaction.commit();
            }
        }
    }

    @Override
    public void onNothingSelected() {
        //Toast.makeText(getActivity(),"Nothing selected",Toast.LENGTH_SHORT).show();
    }

    //    @Override
    public void updateChart() {
        if (mainActivity != null) {
            List<Map<String, Map<String, Set<String>>>> categoriesHistograms = getCategoriesHistograms();
            //infoText.setText("");
            Map<String, Integer> histo = new HashMap<>();
            for (int i = 0; i < categoriesHistograms.size(); ++i) {
                int count = 0;
                for (Map<String, Set<String>> id2Files : categoriesHistograms.get(i).values())
                    count += getFilesCount(id2Files);

                if (count > 0) {
                    histo.put(categoryList[i], count);
                }
            }

            Map<String, Integer> sortedHisto = sortBySize(histo);
            final ArrayList<String> xLabel = new ArrayList<>();
            final List<BarEntry> entries = new ArrayList<BarEntry>();
            int index = 0;
            List<String> keys = new ArrayList<>();
            for (String key : sortedHisto.keySet()) {
                if (sortedHisto.get(key) > 0)
                    keys.add(key);
            }
            Collections.reverse(keys);
            for (String key : keys) {
                xLabel.add(key);
                int value = (int) Math.round(sortedHisto.get(key));
                entries.add(new BarEntry(index, value));
                ++index;
            }
            if (!entries.isEmpty())
                chart.getAxisLeft().setAxisMaximum(entries.get(entries.size() - 1).getY() + 2);

            XAxis xAxis = chart.getXAxis();
            xAxis.setLabelCount(xLabel.size());
            xAxis.setValueFormatter(new IAxisValueFormatter() {
                @Override
                public String getFormattedValue(float value, AxisBase axis) {
                    //value=-value;
                    if (value >= 0 && value < xLabel.size())
                        return xLabel.get((int) value);
                    else
                        return "";

                }
            });

            BarDataSet barDataSet = new BarDataSet(entries, "");
            barDataSet.setColor(color);

            BarData data = new BarData(barDataSet);
            data.setBarWidth(0.7f * xLabel.size() / categoryList.length);
            data.setValueFormatter(new IValueFormatter() {

                @Override
                public String getFormattedValue(float v, Entry entry, int i, ViewPortHandler viewPortHandler) {
                    return "" + ((int) v);
                }
            });
            chart.setData(data);
            chart.getLegend().setEnabled(false);
            chart.invalidate();
        }
    }

    private static <K, V extends Comparable<? super V>> Map<K, V> sortBySize(Map<K, V> map) {
        ArrayList<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
            @Override
            public int compare(Map.Entry<K, V> kvEntry, Map.Entry<K, V> t1) {
                return t1.getValue().compareTo(kvEntry.getValue());
            }
        });

        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    protected int getFilesCount(Map<String, Set<String>> id2Files) {
        int count = 0;
        for (Set<String> filenames : id2Files.values())
            count += filenames.size();
        return count;
    }

    private void updateCategoryChart(Map<String, Map<String, Set<String>>> histo) {
        //infoText.setText("");

        ArrayList<Map.Entry<String, Map<String, Set<String>>>> sortedHisto = new ArrayList<>(histo.entrySet());
        Collections.sort(sortedHisto, new Comparator<Map.Entry<String, Map<String, Set<String>>>>() {
            @Override
            public int compare(Map.Entry<String, Map<String, Set<String>>> kvEntry, Map.Entry<String, Map<String, Set<String>>> t1) {
                return getFilesCount(t1.getValue()) - getFilesCount(kvEntry.getValue());
            }
        });

        //Map<String,Set<String>> sortedHisto=sortByValueSize(histo);
        final ArrayList<String> xLabel = new ArrayList<>();
        final List<BarEntry> entries = new ArrayList<BarEntry>();
        int index = 0;
        int maxCount = 15;
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<String>>> entry : sortedHisto) {
            keys.add(entry.getKey());
            if (keys.size() > maxCount)
                break;
        }
        Collections.reverse(keys);
        for (String key : keys) {
            xLabel.add(key);
            int value = (int) Math.round(getFilesCount(histo.get(key)));
            entries.add(new BarEntry(index, value));
            ++index;

            if (index > maxCount)
                break;
        }
        if (!entries.isEmpty())
            chart.getAxisLeft().setAxisMaximum(entries.get(entries.size() - 1).getY() + 2);

        XAxis xAxis = chart.getXAxis();
        xAxis.setLabelCount(xLabel.size());
        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                //value=-value;
                if (value >= 0 && value < xLabel.size())
                    return xLabel.get((int) value);
                else
                    return "";

            }
        });

        BarDataSet barDataSet = new BarDataSet(entries, "");
        barDataSet.setColor(color);

        BarData data = new BarData(barDataSet);
        data.setBarWidth(0.7f * xLabel.size() / maxCount);
        data.setValueFormatter(new IValueFormatter() {

            @Override
            public String getFormattedValue(float v, Entry entry, int i, ViewPortHandler viewPortHandler) {
                return "" + ((int) v);
            }
        });
        chart.setData(data);
        chart.getLegend().setEnabled(false);
        chart.invalidate();

    }
}