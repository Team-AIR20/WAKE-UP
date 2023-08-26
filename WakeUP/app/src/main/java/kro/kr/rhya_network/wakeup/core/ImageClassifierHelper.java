/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kro.kr.rhya_network.wakeup.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.io.IOException;
import java.util.List;

/** Helper class for wrapping Image Classification actions */
public class ImageClassifierHelper {
    private static final String TAG = "ImageClassifierHelper";
    private static final int DELEGATE_CPU = 0;
    private static final int DELEGATE_GPU = 1;
    private static final int DELEGATE_NNAPI = 2;
    private static final String MODEL = "model.tflite";


    private final float threshold;
    private final int numThreads;
    private final int maxResults;
    private final int currentDelegate;
    private final Context context;
    private final ClassifierListener imageClassifierListener;
    private ImageClassifier imageClassifier;


    public ImageClassifierHelper(Float threshold,
                                 int numThreads,
                                 int maxResults,
                                 int currentDelegate,
                                 Context context,
                                 ClassifierListener imageClassifierListener) {
        this.threshold = threshold;
        this.numThreads = numThreads;
        this.maxResults = maxResults;
        this.currentDelegate = currentDelegate;
        this.context = context;
        this.imageClassifierListener = imageClassifierListener;
        setupImageClassifier();
    }

    public static ImageClassifierHelper create(
            Context context,
            ClassifierListener listener
    ) {
        return new ImageClassifierHelper(
                0.5f,
                2,
                1,
                0,
                context,
                listener
        );
    }

    private void setupImageClassifier() {
        ImageClassifier.ImageClassifierOptions.Builder optionsBuilder =
                ImageClassifier.ImageClassifierOptions.builder()
                        .setScoreThreshold(threshold)
                        .setMaxResults(maxResults);

        BaseOptions.Builder baseOptionsBuilder =
                BaseOptions.builder().setNumThreads(numThreads);

        switch (currentDelegate) {
            case DELEGATE_CPU:
                // Default
                break;
            case DELEGATE_GPU:
                try (CompatibilityList compatibilityList = new CompatibilityList()){
                    if (compatibilityList.isDelegateSupportedOnThisDevice())
                        baseOptionsBuilder.useGpu();
                    else
                        imageClassifierListener.onError("GPU is not supported on this device");
                }

                break;
            case DELEGATE_NNAPI:
                baseOptionsBuilder.useNnapi();
        }

        try {
            imageClassifier =
                    ImageClassifier.createFromFileAndOptions(
                            context,
                            MODEL,
                            optionsBuilder.build());
        } catch (IOException e) {
            imageClassifierListener.onError("Image classifier failed to initialize. See error logs for details");

            Log.e(TAG, String.format("TFLite failed to load model with error: %s", e.getMessage()));
        }
    }

    public void classify(Bitmap image, int imageRotation) {
        if (imageClassifier == null) {
            setupImageClassifier();
        }

        long inferenceTime = SystemClock.uptimeMillis();

        ImageProcessor imageProcessor =
                new ImageProcessor.Builder().add(new Rot90Op(-imageRotation / 90)).build();

        TensorImage tensorImage =
                imageProcessor.process(TensorImage.fromBitmap(image));

        imageClassifier.classify(tensorImage);

        List<Classifications> result = imageClassifier.classify(tensorImage);

        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;
        imageClassifierListener.onResults(result, inferenceTime);
    }

    public void clearImageClassifier() {
        imageClassifier = null;
    }

    public interface ClassifierListener {
        void onError(String error);

        void onResults(List<Classifications> results, long inferenceTime);
    }
}
