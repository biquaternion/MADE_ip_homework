package com.dk.imgprochw.db;

import java.io.Serializable;

/**
 * Created by avsavchenko.
 */
public class ImageAnalysisResults implements Serializable {
    public String filename = null;
    public FaceData faceData = null;
    public EXIFData locations = null;

    public ImageAnalysisResults() {
    }

    public ImageAnalysisResults(String filename, FaceData faceData, EXIFData locations) {
        this.filename = filename;
        this.faceData = faceData;
        this.locations = locations;
    }

    public ImageAnalysisResults(FaceData faceData) {
        this.faceData = faceData;
    }
}
