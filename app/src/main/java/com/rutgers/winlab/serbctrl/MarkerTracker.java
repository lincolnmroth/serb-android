package com.rutgers.winlab.serbctrl;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.objdetect.CascadeClassifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;


import es.ava.aruco.CameraParameters;
import es.ava.aruco.Marker;
import es.ava.aruco.MarkerDetector;

public class MarkerTracker extends Activity implements CvCameraViewListener2 {

    private static final String TAG = "MarkerTracker";
    private static final float MARKER_SIZE = (float) 0.025;

    private Thread frameThread = null;
    private CameraBridgeViewBase mOpenCvCameraView;
    public static boolean helmetOn = false;
    public static int tagID = 530;

    private Mat                    mRgba;
    private Mat                    mGray;


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    //grayscaleImage=new Mat();
                    Log.i(TAG,"got through mcascadefile2");
                    System.loadLibrary("opencv_java");
                    Log.i(TAG,"got through mcascadefile");

                        mOpenCvCameraView.enableView();
                    Log.i(TAG, "got through mOpenCvCameraView.enableView()");

                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public MarkerTracker() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.tutorial1_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.tutorial1_activity_java_surface_view);
        mOpenCvCameraView.setCameraIndex(1);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setMaxFrameSize(400, 400);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_11, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        Log.v(TAG, width + "," + height);
        mRgba = new Mat(height,width, CvType.CV_8UC4);
        mGray = new Mat(height,width, CvType.CV_8UC4);
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        final Mat rgba = inputFrame.rgba();
        Core.flip(rgba, rgba, 1);

        if (!helmetOn && (frameThread == null || !frameThread.isAlive())) {
            frameThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    //Setup required parameters for detect method
                    Log.v(TAG, "Helmet thread");
                    MarkerDetector mDetector = new MarkerDetector();
                    Vector<Marker> detectedMarkers = new Vector<>();
                    CameraParameters camParams = new CameraParameters();
                    //camParams.readFromFile(Environment.getExternalStorageDirectory().toString() + DATA_FILEPATH);
                    camParams.loadConstandCalibration();

                    //Populate detectedMarkers
                    mDetector.detect(rgba, detectedMarkers, camParams, MARKER_SIZE);

                    //Draw Axis for each marker detected
                    //Log.i(TAG,"DRAWING AXIS OPENCV");
                    Log.v(TAG, "size " + detectedMarkers.size());
                    if (detectedMarkers.size() != 0) {
                        //Log.i(TAG,"DRAWING AXIS OPENCV");
                        for (int i = 0; i < detectedMarkers.size(); i++) {
                            Marker marker = detectedMarkers.get(i);
                            //Setup
                            int idValue = marker.getMarkerId();
                            Log.v(TAG, new Integer(idValue).toString());
                            if (idValue == tagID) {
                                helmetOn = true;
                                Log.v(TAG, "Helmet detected");
                                if (getParent() == null) {
                                    MarkerTracker.this.setResult(Activity.RESULT_OK);
                                } else {
                                    MarkerTracker.this.getParent().setResult(Activity.RESULT_OK);
                                }
                                MarkerTracker.this.finish();
                            }
                        }
                    }
                }
            });
            frameThread.start();
        }

        List<Mat> src = new ArrayList<>();
        src.add(rgba);
        List<Mat> dest = new ArrayList<>();
        Mat nrgba = new Mat(rgba.rows(), rgba.cols(), CvType.CV_8UC4);
        dest.add(nrgba);
        Core.mixChannels(src, dest, new MatOfInt(new int[]{2, 0, 0, 2, 1, 1, 3, 3}));
        Log.v(TAG, "lol");
        return nrgba;
    }
}
