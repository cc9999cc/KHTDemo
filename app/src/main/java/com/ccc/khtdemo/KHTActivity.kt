package com.ccc.khtdemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
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
import org.json.JSONArray
import org.json.JSONException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KHTActivity : ComponentActivity() {
    private val videoPath = "kht/test1.mp4"
    private val knnPath = "kht/KHT_KNNArgs.json"
    private val dfaDesPath = "kht/DFADescriptionKHT.json"
    private var backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(
                    this,
                    "Permission request granted",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Permission request denied",
                    Toast.LENGTH_LONG
                ).show()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kht)

        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) -> {
            }

            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }
        }

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            startDfaRun()
        }
        findViewById<Button>(R.id.btn_start_camera).setOnClickListener {
            openCamera()
        }
    }

    val cameraInput = CameraInput()
    var dfa: DFAController? = null

    private fun startDfaRun() {

        val rawPoseFeatureInput = RawPoseFeatureInput()
        val poseFeatureInput = PoseFeatureInput()
        val frameIndexInput = FrameIndexInput()
        val inputMap = mutableMapOf(
            "CameraInput" to cameraInput,
            "RawPoseFeatureInput" to rawPoseFeatureInput,
            "PoseFeatureInput" to poseFeatureInput,
            "FrameIndexInput" to frameIndexInput,
        )
        val inputOrder = arrayListOf(
            "FrameIndexInput",
            "CameraInput",
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
            "ReverseTimeChecker" to rTimeChecker,
            "SamePeopleChecker" to samePeopleChecker
        )
        val countAddProcess = CountAddProcess()
        val updateLastFrameEnterCount = UpdateLastFrameEnterCount()
        val postProcessMap = mutableMapOf(
            "CountAddProcess" to countAddProcess,
            "UpdateLastFrameEnterCount" to updateLastFrameEnterCount,
        )

        dfa = DFAController(inputMap, inputOrder, checkerMap, postProcessMap, dfaDesPath)
        val startTime = System.currentTimeMillis()
        backgroundExecutor.execute {
            while (dfa!!.run()) {
                try {
                    Log.d("KHTActivity", dfa!!.dFAParameters.count.toString())
                    val endTime = System.currentTimeMillis()
                    Log.d(
                        "KHTActivity",
                        "FPS: ${poseFeatureInput.freshCounts} / (${endTime - startTime})"
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }
        }
    }

    private var previewView: PreviewView? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private fun openCamera() {
        previewView = findViewById(R.id.preview)

        previewView?.post {
            val cameraProviderFuture =
                ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val cameraSelector =
                        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build()
                    preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setTargetRotation(previewView!!.display.rotation)
                        .build()
                    imageAnalyzer =
                        ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .setTargetRotation(previewView!!.display.rotation)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                            .also {
                                it.setAnalyzer(imageExecutor) {
//                                    Log.d("ccccccc", "imageAnalyzer imageProxy")
                                    cameraInput.imageProxy = it
                                }
                            }
                    cameraProvider.unbindAll()
                    try {
                        val camera = cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview, imageAnalyzer
                        )
                        preview?.setSurfaceProvider(previewView!!.surfaceProvider)
                    } catch (exc: Exception) {
                        exc.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(this)
            )
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
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
        imageExecutor.shutdown()
        imageExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }
}