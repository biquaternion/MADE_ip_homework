package com.dk.imgprochw;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import com.dk.imgprochw.db.ClassifierResult;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by avsavchenko.
 */
public class TfLiteClassifier {

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "TfLiteClassifier";

    /**
     * An instance of the driver class to run model inference with Tensorflow Lite.
     */
    protected Interpreter tflite;

    private static final boolean useTensorImage = true;
    /**
     * Input image TensorBuffer.
     */
    private TensorImage inputImageBuffer;
    private ImageProcessor imageProcessor;
    /* Preallocated buffers for storing image data in. */
    private int[] intValues = null;
    protected ByteBuffer imgData = null;
    /**
     * A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs.
     */
    private int imageSizeX = 224, imageSizeY = 224;
    private float[][][] outputs;
    Map<Integer, Object> outputMap = new HashMap<>();

    public TfLiteClassifier(final Context context, String model_path) throws IOException {
        Interpreter.Options options = (new Interpreter.Options()).setNumThreads(4);

        MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(context, model_path);
        tflite = new Interpreter(tfliteModel, options);
        tflite.allocateTensors();
        int[] inputShape = tflite.getInputTensor(0).shape();
        imageSizeX = inputShape[1];
        imageSizeY = inputShape[2];
        inputImageBuffer = new TensorImage(tflite.getInputTensor(0).dataType());
        intValues = new int[imageSizeX * imageSizeY];
        imgData =ByteBuffer.allocateDirect(imageSizeX*imageSizeY* inputShape[3]*getNumBytesPerChannel());
        imgData.order(ByteOrder.nativeOrder());
        // Creates processor for the TensorImage.
        imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                        .add(getPreprocessNormalizeOp())
                        .build();

        int outputCount = tflite.getOutputTensorCount();
        outputs = new float[outputCount][1][];
        for (int i = 0; i < outputCount; ++i) {
            int[] shape = tflite.getOutputTensor(i).shape();
            int numOFFeatures = shape[1];
            Log.i(TAG, "Read output layer size is " + numOFFeatures);
            outputs[i][0] = new float[numOFFeatures];
            ByteBuffer ith_output = ByteBuffer.allocateDirect(numOFFeatures * getNumBytesPerChannel());  // Float tensor, shape 3x2x4
            ith_output.order(ByteOrder.nativeOrder());
            outputMap.put(i, ith_output);
        }
    }

    /**
     * Loads input image, and applies preprocessing.
     */
    private TensorImage loadImage(final Bitmap bitmap) {
        // Loads bitmap into a TensorImage.
        inputImageBuffer.load(bitmap);
        return imageProcessor.process(inputImageBuffer);
    }
//    protected abstract void addPixelValue(int val);
//    protected abstract TensorOperator getPreprocessNormalizeOp();

    /**
     * Classifies a frame from the preview stream.
     */
    public float[][][] classifyFrame(Bitmap bitmap) {
        Object[] inputs = {null};

        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        if (imgData == null) {
            return null;
        }
        imgData.rewind();
        // Convert the image to floating point.
        int pixel = 0;
        for (int i = 0; i < imageSizeX; ++i) {
            for (int j = 0; j < imageSizeY; ++j) {
                final int val = intValues[pixel++];
                imgData.putFloat((val & 0xFF) - 103.939f);
                imgData.putFloat(((val >> 8) & 0xFF) - 116.779f);
                imgData.putFloat(((val >> 16) & 0xFF) - 123.68f);
            }
        }
        inputs[0] = imgData;
//        inputImageBuffer = loadImage(bitmap);
//        inputs[0] = inputImageBuffer.getBuffer();
        long startTime = SystemClock.uptimeMillis();
        tflite.runForMultipleInputsOutputs(inputs, outputMap);
        for (int i = 0; i < outputs.length; ++i) {
            ByteBuffer ith_output = (ByteBuffer) outputMap.get(i);
            ith_output.rewind();
            int len = outputs[i][0].length;
            for (int j = 0; j < len; ++j) {
                outputs[i][0][j] = ith_output.getFloat();
            }
            ith_output.rewind();
        }
        long endTime = SystemClock.uptimeMillis();
        Log.i(TAG, "tf lite timecost to run model inference: " + Long.toString(endTime - startTime));

        return outputs;
    }

    public void close() {
        tflite.close();
    }

    //    protected abstract ClassifierResult getResults(float[][][] outputs);
    public int getImageSizeX() {
        return imageSizeX;
    }

    public int getImageSizeY() {
        return imageSizeY;
    }

    protected int getNumBytesPerChannel() {
        return 4; // Float.SIZE / Byte.SIZE;
    }

    protected void addPixelValue(int val) {
        //'RGB'->'BGR' is not needed for all our scene recognition networks
        float std = 128.0f; //mobilenet v2
        imgData.putFloat(((val >> 16) & 0xFF) / std - 1.0f);
        imgData.putFloat(((val >> 8) & 0xFF) / std - 1.0f);
        imgData.putFloat((val & 0xFF) / std - 1.0f);
    }

    protected TensorOperator getPreprocessNormalizeOp() {
        //float std=127.5f; //mobilenet v1
        float std = 128.0f; //mobilenet v2
        return new NormalizeOp(std, std);
    }
}
