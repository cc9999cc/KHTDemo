package com.ccc.khtdemo

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ccc.khtdemo.base.CameraInput
import com.ccc.khtdemo.base.CountAddProcess
import com.ccc.khtdemo.base.DFAController
import com.ccc.khtdemo.base.FrameIndexInput
import com.ccc.khtdemo.base.KNNChecker
import com.ccc.khtdemo.base.PoseFeatureInput
import com.ccc.khtdemo.base.RawPoseFeatureInput
import com.ccc.khtdemo.base.ReverseTimeChecker
import com.ccc.khtdemo.base.SamePeopleChecker
import com.ccc.khtdemo.base.TimeChecker
import com.ccc.khtdemo.base.UpdateLastFrameEnterCount
import com.google.mediapipe.components.CameraHelper
import com.google.mediapipe.components.CameraXPreviewHelper
import com.google.mediapipe.components.ExternalTextureConverter
import com.google.mediapipe.components.FrameProcessor
import com.google.mediapipe.components.PermissionHelper
import com.google.mediapipe.formats.proto.LandmarkProto
import com.google.mediapipe.framework.AndroidAssetUtil
import com.google.mediapipe.framework.Packet
import com.google.mediapipe.framework.PacketGetter
import com.google.mediapipe.glutil.EglManager
import org.json.JSONArray
import org.json.JSONException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

open class KHT2Activity : AppCompatActivity() {
    //    private val videoPath = "kht/test1.mp4"
    private val knnPath = "kht/KHT_KNNArgs.json"
    private val dfaDesPath = "kht/DFADescriptionKHT.json"

    val BINARY_GRAPH_NAME = "pose_tracking_gpu.binarypb"
    val INPUT_VIDEO_STREAM_NAME = "input_video"
    val OUTPUT_VIDEO_STREAM_NAME = "output_video"
    val OUTPUT_LANDMARKS_STREAM_NAME = "pose_landmarks"
    val CAMERA_FACING: CameraHelper.CameraFacing = CameraHelper.CameraFacing.BACK
    val FLIP_FRAMES_VERTICALLY = true
    private var previewFrameTexture: SurfaceTexture? = null
    private var previewDisplayView: SurfaceView? = null
    private var eglManager: EglManager? = null
    private var processor: FrameProcessor? = null
    private var converter: ExternalTextureConverter? = null
    private var cameraHelper: CameraXPreviewHelper? = null
    private var title: TextView? = null

    init {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kht2)

        title = findViewById(R.id.title1)
        title!!.bringToFront()

        previewDisplayView = SurfaceView(this)
        setupPreviewDisplayView() //设置相机预览到ui上
        AndroidAssetUtil.initializeNativeAssetManager(this)
        eglManager = EglManager(null)
        processor = FrameProcessor(
            this,
            eglManager!!.nativeContext,
            BINARY_GRAPH_NAME,
            INPUT_VIDEO_STREAM_NAME,
            OUTPUT_VIDEO_STREAM_NAME
        )
        processor!!
            .videoSurfaceOutput
            .setFlipY(FLIP_FRAMES_VERTICALLY)
        PermissionHelper.checkAndRequestCameraPermissions(this)
        startDfaRun()
    }

    val cameraInput = CameraInput()
    var dfa: DFAController? = null

    private fun startDfaRun() {
        val rawPoseFeatureInput = RawPoseFeatureInput()
        val poseFeatureInput = PoseFeatureInput()
        val frameIndexInput = FrameIndexInput()
        val inputMap = mutableMapOf(
            "RawPoseFeatureInput" to rawPoseFeatureInput,
            "PoseFeatureInput" to poseFeatureInput,
            "FrameIndexInput" to frameIndexInput,
        )
        val inputOrder = arrayListOf(
            "FrameIndexInput",
            "RawPoseFeatureInput",
            "PoseFeatureInput"
        )
        val knnArgs = loadKNNJson()
        if (knnArgs == null) {
            Log.d("ccccc", "knn文件读取失败")
            return
        }
        val knnChecker = KNNChecker(knnArgs)
        val timeChecker = TimeChecker()
        val rTimeChecker = ReverseTimeChecker()
        val samePeopleChecker = SamePeopleChecker()
        val checkerMap = mutableMapOf(
            "KNNChecker" to knnChecker,
            "TimeChecker" to timeChecker,
        )
        val countAddProcess = CountAddProcess()
        val updateLastFrameEnterCount = UpdateLastFrameEnterCount()
        val postProcessMap = mutableMapOf(
            "CountAddProcess" to countAddProcess,
            "UpdateLastFrameEnterCount" to updateLastFrameEnterCount,
        )

        dfa = DFAController(inputMap, inputOrder, checkerMap, postProcessMap, dfaDesPath)
        val startTime = System.currentTimeMillis()
        processor?.addPacketCallback(
            OUTPUT_LANDMARKS_STREAM_NAME
        ) { packet: Packet ->
            val landmarksRaw = PacketGetter.getProtoBytes(packet)
            try {
                val landmarks = LandmarkProto.NormalizedLandmarkList.parseFrom(landmarksRaw)
                    ?: return@addPacketCallback
                rawPoseFeatureInput.detect = landmarks.landmarkList
                dfa!!.step()
                title!!.text = "Count:" + dfa?.dFAParameters?.count
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadKNNJson(): Array<FloatArray>? {
        // Load KNNArgs.
        val stringBuilder = StringBuilder()
        try {
            val assetManager = this.assets
            val bf = BufferedReader(
                InputStreamReader(
                    assetManager.open(knnPath)
                )
            )
            var line: String?
            while (bf.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
        val jsonString = stringBuilder.toString()
        var jarr: JSONArray? = null
        var jarr1: JSONArray? = null
        try {
            jarr = JSONArray(jsonString)
            jarr1 = jarr.getJSONArray(0)
        } catch (e: JSONException) {
            e.printStackTrace()
            return null
        }
        val KNNArgs = Array(jarr.length()) { FloatArray(jarr1.length()) }
        for (i in 0 until jarr.length()) {
            try {
                val innerJsonArray = jarr.getJSONArray(i)
                for (j in 0 until innerJsonArray.length()) {
                    KNNArgs[i][j] = innerJsonArray.getDouble(j).toFloat()
                    // dumplcate test.
                    // KNNArgs[i*2][j] = (float) innerJsonArray.getDouble(j);
                    // KNNArgs[i*2+1][j] = (float) innerJsonArray.getDouble(j);
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        // Load KNNArgs over.
        return KNNArgs
    }

    override fun onDestroy() {
        super.onDestroy()
        dfa?.run = false
    }


    override fun onResume() {
        super.onResume()
        converter = ExternalTextureConverter(
            eglManager?.context, 2
        )
        converter!!.setFlipY(FLIP_FRAMES_VERTICALLY)
        converter!!.setConsumer(processor)
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        converter?.close()

        // Hide preview display until we re-open the camera again.
        previewDisplayView!!.visibility = View.GONE
    }

    private fun onCameraStarted(surfaceTexture: SurfaceTexture) {
        previewFrameTexture = surfaceTexture
        previewDisplayView!!.visibility = View.VISIBLE
    }

    private fun cameraTargetResolution(): Size? {
        return null
    }

    private fun startCamera() {
        cameraHelper = CameraXPreviewHelper()
        cameraHelper?.setOnCameraStartedListener { surfaceTexture: SurfaceTexture? ->
            onCameraStarted(surfaceTexture!!)
        }
        val cameraFacing: CameraHelper.CameraFacing = CAMERA_FACING
        cameraHelper!!.startCamera(
            this, cameraFacing, null, cameraTargetResolution()
        )
    }

    private fun computeViewSize(width: Int, height: Int): Size {
        return Size(width, height)
    }

    fun onPreviewDisplaySurfaceChanged(
        holder: SurfaceHolder?, format: Int, width: Int, height: Int
    ) {
        val viewSize = computeViewSize(width, height)
        val displaySize = cameraHelper!!.computeDisplaySizeFromViewSize(viewSize)
        val isCameraRotated = cameraHelper!!.isCameraRotated
        converter?.setSurfaceTextureAndAttachToGLContext(
            previewFrameTexture,
            if (isCameraRotated) displaySize.height else displaySize.width,
            if (isCameraRotated) displaySize.width else displaySize.height
        )
    }

    private fun setupPreviewDisplayView() {
        previewDisplayView?.visibility = View.GONE
        val viewGroup = findViewById<ViewGroup>(R.id.preview_display_layout)
        viewGroup.addView(previewDisplayView, 0)
        previewDisplayView!!
            .holder
            .addCallback(
                object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        processor?.videoSurfaceOutput?.setSurface(holder.surface)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder, format: Int, width: Int, height: Int
                    ) {
                        onPreviewDisplaySurfaceChanged(holder, format, width, height)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        processor?.videoSurfaceOutput?.setSurface(null)
                    }
                })
    }
}