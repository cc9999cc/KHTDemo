package com.ccc.khtdemo;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ccc.khtdemo.base.BaseChecker;
import com.ccc.khtdemo.base.BaseInput;
import com.ccc.khtdemo.base.BaseProcess;
import com.ccc.khtdemo.base.CountAddProcess;
import com.ccc.khtdemo.base.DFAController;
import com.ccc.khtdemo.base.FrameIndexInput;
import com.ccc.khtdemo.base.KNNChecker;
import com.ccc.khtdemo.base.PoseFeatureInput;
import com.ccc.khtdemo.base.RawPoseFeatureInput;
import com.ccc.khtdemo.base.TimeChecker;
import com.ccc.khtdemo.base.UpdateLastFrameEnterCount;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main activity of MediaPipe example apps.
 */
public class KHT3Activity extends AppCompatActivity {
    public static final String TAG = "Test";
    public static final String BINARY_GRAPH_NAME = "pose_tracking_gpu.binarypb";
    public static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    public static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    public static final String OUTPUT_LANDMARKS_STREAM_NAME = "pose_landmarks";

    // private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.FRONT;
    public static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.BACK;
    // Flips the camera-preview frames vertically before sending them into FrameProcessor to be
    // processed in a MediaPipe graph, and flips the processed frames back when they are displayed.
    // This is needed because OpenGL represents images assuming the image origin is at the bottom-left
    // corner, whereas MediaPipe in general assumes the image origin is at top-left.
    public static final boolean FLIP_FRAMES_VERTICALLY = true;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;
    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private FrameProcessor processor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;
    // Handles camera access via the {@link CameraX} Jetpack support library.
    private CameraXPreviewHelper cameraHelper;
    private TextView title;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutResId());

        title = findViewById(R.id.title1);
        title.bringToFront();

        // Static Params.
        float Threshold = 0.2f;
        int N = 5;
        String actionFile = "KHT.json";

        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();//设置相机预览到ui上

        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);
        processor
                .getVideoSurfaceOutput()
                .setFlipY(FLIP_FRAMES_VERTICALLY);

        PermissionHelper.checkAndRequestCameraPermissions(this);

        // Load KNNArgs.
        StringBuilder stringBuilder = new StringBuilder();
        try {
            AssetManager assetManager = this.getAssets();
            BufferedReader bf = new BufferedReader(new InputStreamReader(
                    assetManager.open("KNNArgs.json")
            ));
            String line;
            while ((line = bf.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "IO Fail: " + e);
            return;
        }
        String jsonString = stringBuilder.toString();

        JSONArray jarr = null;
        JSONArray jarr1 = null;
        try {
            jarr = new JSONArray(jsonString);
            jarr1 = jarr.getJSONArray(0);
        } catch (JSONException e) {
            Log.e(TAG, "Json Fail: " + e);
            return;
        }

        // dumplcate test.
        // float[][] KNNArgs = new float[jarr.length() * 2][jarr1.length()];
        float[][] KNNArgs = new float[jarr.length()][jarr1.length()];
        for (int i = 0; i < jarr.length(); i++) {
            try {
                JSONArray innerJsonArray = jarr.getJSONArray(i);
                for (int j = 0; j < innerJsonArray.length(); j++) {
                    KNNArgs[i][j] = (float) innerJsonArray.getDouble(j);
                }
            } catch (JSONException e) {
                Log.e(TAG, "onCreate: Json Detach error", e);
            }
        }
        // Load KNNArgs over.


        // Load Action.
        JSONArray actionArr = null;
        try {
            StringBuilder ActionBuilder = new StringBuilder();
            AssetManager assetManager = this.getAssets();
            BufferedReader bf = new BufferedReader(new InputStreamReader(
                    assetManager.open(actionFile)
            ));
            String line;
            while ((line = bf.readLine()) != null) {
                ActionBuilder.append(line);
            }
            actionArr = new JSONArray(ActionBuilder.toString());
        } catch (IOException e) {
            Log.e(TAG, "IO Fail: " + e);
            return;
        } catch (JSONException e) {
            Log.e(TAG, "Action Load Fail: " + e);
            return;
        }

        int[] action_list = new int[actionArr.length()];
        for (int i = 0; i < actionArr.length(); i++) {
            try {
                action_list[i] = actionArr.getInt(i);
            } catch (JSONException e) {
                Log.e(TAG, "onCreate: Json Detach error", e);
            }
        }

        // State Params.
        AtomicInteger state_index = new AtomicInteger();
        AtomicInteger action_count = new AtomicInteger();
        // Log.i("RESULT", "Action Count: " + action_count.get());
        Log.i("CHANGE_RESULT", "Action Count: " + action_count.get());
        title.bringToFront();
        title.setText("Count:" + action_count.get());
        RawPoseFeatureInput rawPoseFeatureInput = new RawPoseFeatureInput();
        PoseFeatureInput poseFeatureInput = new PoseFeatureInput();
        FrameIndexInput frameIndexInput = new FrameIndexInput();

        Map<String, BaseInput> inputMap = new HashMap<>();
        inputMap.put("RawPoseFeatureInput", rawPoseFeatureInput);
        inputMap.put("PoseFeatureInput", poseFeatureInput);
        inputMap.put("FrameIndexInput", frameIndexInput);
        ArrayList<String> inputOrder = new ArrayList<>();
        inputOrder.add("FrameIndexInput");
        inputOrder.add("RawPoseFeatureInput");
        inputOrder.add("PoseFeatureInput");
        Utils utils = new Utils();
        KNNChecker knnChecker = new KNNChecker(utils.loadKnnArr(getAssets()));
        TimeChecker timeChecker = new TimeChecker();
        Map<String, BaseChecker> checkerMap = new HashMap<>();
        checkerMap.put("KNNChecker", knnChecker);
        checkerMap.put("TimeChecker", timeChecker);
        CountAddProcess countAddProcess = new CountAddProcess();
        UpdateLastFrameEnterCount updateLastFrameEnterCount = new UpdateLastFrameEnterCount();
        Map<String, BaseProcess> postProcessMap = new HashMap<>();
        postProcessMap.put("CountAddProcess", countAddProcess);
        postProcessMap.put("UpdateLastFrameEnterCount", updateLastFrameEnterCount);
        DFAController dfa = new DFAController(inputMap, inputOrder, checkerMap, postProcessMap, "kht/DFADescriptionKHT.json");
        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME,
                (packet) -> {
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    try {
                        NormalizedLandmarkList landmarks = NormalizedLandmarkList.parseFrom(landmarksRaw);
                        if (landmarks == null) {
                            // Log.v(TAG, "[TS:" + packet.getTimestamp() + "] No iris landmarks.");
                            return;
                        }
                        rawPoseFeatureInput.setDetect(landmarks.getLandmarkList());
                        dfa.step();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
        );
    }

    protected int getContentViewLayoutResId() {
        return R.layout.activity_kht2;
    }

    @Override
    protected void onResume() {
        super.onResume();
        converter =
                new ExternalTextureConverter(
                        eglManager.getContext(), 2);
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();

        previewDisplayView.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        previewFrameTexture = surfaceTexture;
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    protected Size cameraTargetResolution() {
        return null;
    }

    public void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    onCameraStarted(surfaceTexture);
                });
        CameraHelper.CameraFacing cameraFacing = CAMERA_FACING;
        cameraHelper.startCamera(
                this, cameraFacing, /*unusedSurfaceTexture=*/ null, cameraTargetResolution());
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }
}