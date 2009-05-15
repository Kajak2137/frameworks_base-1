/*
 * Copyright (C) 2008-2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gesture;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Arrays;

public class GestureUtils {

    private static final int SEQUENCE_SAMPLE_SIZE = 16;

    protected static float[] spatialFeaturize(Gesture gesture, int sampleSize) {
        float[] sample = new float[sampleSize * sampleSize];
        Arrays.fill(sample, 0);

        RectF rect = gesture.getBoundingBox();
        float sx = sampleSize / rect.width();
        float sy = sampleSize / rect.height();
        float scale = sx < sy ? sx : sy;
        android.graphics.Matrix trans = new android.graphics.Matrix();
        trans.setScale(scale, scale);
        android.graphics.Matrix translate1 = new android.graphics.Matrix();
        translate1.setTranslate(-rect.centerX(), -rect.centerY());
        trans.preConcat(translate1);
        android.graphics.Matrix translate2 = new android.graphics.Matrix();
        translate2.setTranslate(sampleSize / 2, sampleSize / 2);
        trans.postConcat(translate2);

        ArrayList<GestureStroke> strokes = gesture.getStrokes();
        int count = strokes.size();
        int size;
        for (int index = 0; index < count; index++) {
            GestureStroke stroke = strokes.get(index);
            float[] pts = sequentialFeaturize(stroke, SEQUENCE_SAMPLE_SIZE);
            trans.mapPoints(pts);

            size = pts.length;
            for (int i = 0; i < size; i += 2) {
                float x = pts[i];
                int xFloor = (int) Math.floor(x);
                int xCeiling = (int) Math.ceil(x);
                float y = pts[i + 1];
                int yFloor = (int) Math.floor(y);
                int yCeiling = (int) Math.ceil(y);

                if (yFloor >= 0 && yFloor < sampleSize && xFloor >= 0 && xFloor < sampleSize) {
                    int pos = yFloor * sampleSize + xFloor;
                    float value = (1 - x + xFloor) * (1 - y + yFloor);
                    if (sample[pos] < value) {
                        sample[pos] = value;
                    }
                }

                if (yFloor >= 0 && yFloor < sampleSize && xCeiling >= 0 && xCeiling < sampleSize) {
                    int pos = yFloor * sampleSize + xCeiling;
                    float value = (1 - xCeiling + x) * (1 - y + yFloor);
                    if (sample[pos] < value) {
                        sample[pos] = value;
                    }
                }

                if (yCeiling >= 0 && yCeiling < sampleSize && xFloor >= 0 && xFloor < sampleSize) {
                    int pos = yCeiling * sampleSize + xFloor;
                    float value = (1 - x + xFloor) * (1 - yCeiling + y);
                    if (sample[pos] < value) {
                        sample[pos] = value;
                    }

                }

                if (yCeiling >= 0 && yCeiling < sampleSize && xCeiling >= 0
                        && xCeiling < sampleSize) {
                    int pos = yCeiling * sampleSize + xCeiling;
                    float value = (1 - xCeiling + x) * (1 - yCeiling + y);
                    if (sample[pos] < value) {
                        sample[pos] = value;
                    }
                }
            }
        }

        return sample;
    }

    /**
     * Featurize a stroke into a vector of a given number of elements
     * 
     * @param stroke
     * @param sampleSize
     * @return a float array
     */
    protected static float[] sequentialFeaturize(GestureStroke stroke, int sampleSize) {
        final float increment = stroke.length / (sampleSize - 1);
        int vectorLength = sampleSize * 2;
        float[] vector = new float[vectorLength];
        float distanceSoFar = 0;
        float[] xpts = stroke.xPoints;
        float[] ypts = stroke.yPoints;
        float lstPointX = xpts[0];
        float lstPointY = ypts[0];
        int index = 0;
        float currentPointX = Float.MIN_VALUE;
        float currentPointY = Float.MIN_VALUE;
        vector[index] = lstPointX;
        index++;
        vector[index] = lstPointY;
        index++;
        int i = 0;
        int count = xpts.length;
        while (i < count) {
            if (currentPointX == Float.MIN_VALUE) {
                i++;
                if (i >= count) {
                    break;
                }
                currentPointX = xpts[i];
                currentPointY = ypts[i];
            }
            float deltaX = currentPointX - lstPointX;
            float deltaY = currentPointY - lstPointY;
            float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            if (distanceSoFar + distance >= increment) {
                float ratio = (increment - distanceSoFar) / distance;
                float nx = lstPointX + ratio * deltaX;
                float ny = lstPointY + ratio * deltaY;
                vector[index] = nx;
                index++;
                vector[index] = ny;
                index++;
                lstPointX = nx;
                lstPointY = ny;
                distanceSoFar = 0;
            } else {
                lstPointX = currentPointX;
                lstPointY = currentPointY;
                currentPointX = Float.MIN_VALUE;
                currentPointY = Float.MIN_VALUE;
                distanceSoFar += distance;
            }
        }

        for (i = index; i < vectorLength; i += 2) {
            vector[i] = lstPointX;
            vector[i + 1] = lstPointY;
        }
        return vector;
    }

    /**
     * Calculate the centroid 
     * 
     * @param points
     * @return the centroid
     */
    public static float[] computeCentroid(float[] points) {
        float centerX = 0;
        float centerY = 0;
        int count = points.length;
        for (int i = 0; i < count; i++) {
            centerX += points[i];
            i++;
            centerY += points[i];
        }
        float[] center = new float[2];
        center[0] = 2 * centerX / count;
        center[1] = 2 * centerY / count;

        return center;
    }

    /**
     * calculate the variance-covariance matrix, treat each point as a sample
     * 
     * @param points
     * @return the covariance matrix
     */
    protected static double[][] computeCoVariance(float[] points) {
        double[][] array = new double[2][2];
        array[0][0] = 0;
        array[0][1] = 0;
        array[1][0] = 0;
        array[1][1] = 0;
        int count = points.length;
        for (int i = 0; i < count; i++) {
            float x = points[i];
            i++;
            float y = points[i];
            array[0][0] += x * x;
            array[0][1] += x * y;
            array[1][0] = array[0][1];
            array[1][1] += y * y;
        }
        array[0][0] /= (count / 2);
        array[0][1] /= (count / 2);
        array[1][0] /= (count / 2);
        array[1][1] /= (count / 2);

        return array;
    }

    public static float computeTotalLength(float[] points) {
        float sum = 0;
        int count = points.length - 4;
        for (int i = 0; i < count; i += 2) {
            float dx = points[i + 2] - points[i];
            float dy = points[i + 3] - points[i + 1];
            sum += Math.sqrt(dx * dx + dy * dy);
        }
        return sum;
    }

    public static double computeStraightness(float[] points) {
        float totalLen = computeTotalLength(points);
        float dx = points[2] - points[0];
        float dy = points[3] - points[1];
        return Math.sqrt(dx * dx + dy * dy) / totalLen;
    }

    public static double computeStraightness(float[] points, float totalLen) {
        float dx = points[2] - points[0];
        float dy = points[3] - points[1];
        return Math.sqrt(dx * dx + dy * dy) / totalLen;
    }

    /**
     * Calculate the squared Euclidean distance between two vectors
     * 
     * @param vector1
     * @param vector2
     * @return the distance
     */
    protected static double euclideanDistance(float[] vector1, float[] vector2) {
        double squaredDistance = 0;
        int size = vector1.length;
        for (int i = 0; i < size; i++) {
            float difference = vector1[i] - vector2[i];
            squaredDistance += difference * difference;
        }
        return squaredDistance / size;
    }

    /**
     * Calculate the cosine distance between two instances
     * 
     * @param in1
     * @param in2
     * @return the distance between 0 and Math.PI
     */
    protected static double cosineDistance(Instance in1, Instance in2) {
        float sum = 0;
        float[] vector1 = in1.vector;
        float[] vector2 = in2.vector;
        int len = vector1.length;
        for (int i = 0; i < len; i++) {
            sum += vector1[i] * vector2[i];
        }
        return Math.acos(sum / (in1.magnitude * in2.magnitude));
    }

    public static OrientedBoundingBox computeOrientedBBX(ArrayList<GesturePoint> pts) {
        GestureStroke stroke = new GestureStroke(pts);
        float[] points = sequentialFeaturize(stroke, SEQUENCE_SAMPLE_SIZE);
        return computeOrientedBBX(points);
    }

    public static OrientedBoundingBox computeOrientedBBX(float[] points) {
        float[] meanVector = computeCentroid(points);
        return computeOrientedBBX(points, meanVector);
    }

    public static OrientedBoundingBox computeOrientedBBX(float[] points, float[] centroid) {

        android.graphics.Matrix tr = new android.graphics.Matrix();
        tr.setTranslate(-centroid[0], -centroid[1]);
        tr.mapPoints(points);

        double[][] array = computeCoVariance(points);
        double[] targetVector = computeOrientation(array);

        float angle;
        if (targetVector[0] == 0 && targetVector[1] == 0) {
            angle = -90;
        } else { // -PI<alpha<PI
            angle = (float) Math.atan2(targetVector[1], targetVector[0]);
            angle = (float) (180 * angle / Math.PI);
            android.graphics.Matrix trans = new android.graphics.Matrix();
            trans.setRotate(-angle);
            trans.mapPoints(points);
        }

        float minx = Float.MAX_VALUE;
        float miny = Float.MAX_VALUE;
        float maxx = Float.MIN_VALUE;
        float maxy = Float.MIN_VALUE;
        int count = points.length;
        for (int i = 0; i < count; i++) {
            if (points[i] < minx) {
                minx = points[i];
            }
            if (points[i] > maxx) {
                maxx = points[i];
            }
            i++;
            if (points[i] < miny) {
                miny = points[i];
            }
            if (points[i] > maxy) {
                maxy = points[i];
            }
        }

        OrientedBoundingBox bbx = new OrientedBoundingBox(angle, centroid[0], centroid[1], maxx
                - minx, maxy - miny);
        return bbx;
    }

    private static double[] computeOrientation(double[][] covarianceMatrix) {
        double[] targetVector = new double[2];
        if (covarianceMatrix[0][1] == 0 || covarianceMatrix[1][0] == 0) {
            targetVector[0] = 1;
            targetVector[1] = 0;
        }

        // lamda^2 + a * lamda + b = 0
        double a = -covarianceMatrix[0][0] - covarianceMatrix[1][1];
        double b = covarianceMatrix[0][0] * covarianceMatrix[1][1] - covarianceMatrix[0][1]
                * covarianceMatrix[1][0];
        double value = a / 2;
        double rightside = Math.sqrt(Math.pow(value, 2) - b);
        double lambda1 = -value + rightside;
        double lambda2 = -value - rightside;
        if (lambda1 == lambda2) {
            targetVector[0] = 0;
            targetVector[1] = 0;
        } else {
            double lambda = lambda1 > lambda2 ? lambda1 : lambda2;
            targetVector[0] = 1;
            targetVector[1] = (lambda - covarianceMatrix[0][0]) / covarianceMatrix[0][1];
        }
        return targetVector;
    }
}
