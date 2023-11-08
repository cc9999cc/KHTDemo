package com.ccc.khtdemo

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.ccc.khtdemo.base.CameraInput
import com.ccc.khtdemo.base.CountAddProcess
import com.ccc.khtdemo.base.DFAController
import com.ccc.khtdemo.base.FrameIndexInput
import com.ccc.khtdemo.base.FreshError
import com.ccc.khtdemo.base.KNNChecker
import com.ccc.khtdemo.base.PoseFeatureInput
import com.ccc.khtdemo.base.RawPoseFeatureInput
import com.ccc.khtdemo.base.ReverseTimeChecker
import com.ccc.khtdemo.base.SamePeopleChecker
import com.ccc.khtdemo.base.TimeChecker
import com.ccc.khtdemo.base.UpdateLastFrameEnterCount
import org.opencv.android.InstallCallbackInterface
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.videoio.VideoCapture
import org.opencv.videoio.Videoio
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceActivity : AppCompatActivity() {
    private val utils: Utils
    private val backgroundExecutor: ExecutorService

    init {
        utils = Utils()
        backgroundExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face)
    }

    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            Log.d(
                "OpenCV",
                "Internal OpenCV library not found. Using OpenCV Manager for initialization"
            );
            OpenCVLoader.initAsync(
                OpenCVLoader.OPENCV_VERSION_3_0_0,
                this,
                object : LoaderCallbackInterface {
                    override fun onManagerConnected(status: Int) {
                        when (status) {
                            LoaderCallbackInterface.SUCCESS -> {
                                //这里加载完成才可以去调用OpenCV的相关方法。
                                Log.i("OpenCV", "OpenCV loaded successfully")
                            }

                            else -> {
                                Log.i("OpenCV", "OpenCV loaded failed")
                            }
                        }
                    }

                    override fun onPackageInstall(
                        operation: Int,
                        callback: InstallCallbackInterface?
                    ) {

                    }

                })
        }
    }

    fun playVideo(v: View) {
        val videoView = findViewById<VideoView>(R.id.video_view);
        videoView.setMediaController(MediaController(this));
        val uri = File(cacheDir, "test1.mp4").toUri()
        videoView.setVideoURI(uri);
        videoView.start();
//        val outputFile = File(cacheDir, "test1.mp4")
//        var mediaPlayer: MediaPlayer? =
//            MediaPlayer.create(this, outputFile.toUri()) // R.raw.video_file 是你的视频文件资源ID
//        mediaPlayer?.start() // 开始播放视频
    }

    fun readVideoFile(v: View) {
        // 创建 VideoCapture 对象并打开视频文件
        val outputFile = File(cacheDir, "test1.mp4")
        val videoPath = outputFile.path
        ALog.d("videoPath:${videoPath}")
        if (!outputFile.exists()) {
            ALog.e("文件不存在")
            return
        }
        val capture = VideoCapture()
        capture.open(videoPath, Videoio.CAP_ANDROID)
        // 检查视频是否成功打开
        if (!capture.isOpened()) {
            println("Error: 无法打开视频文件")
            return
        }
        backgroundExecutor.execute {
            val cameraInput = CameraInput(capture)
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
            val knnArgs = utils.loadKnnArr(assets)
            if (knnArgs == null) {
                Log.d("ccccc", "knn文件读取失败")
                return@execute
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
            val dfa = DFAController(
                inputMap,
                inputOrder,
                checkerMap,
                postProcessMap,
                "kht/DFADescriptionKHT.json"
            )
            val startTime = System.currentTimeMillis()
            while (true) {
                try {
                    dfa.run()
                    Log.d("KHTActivity", dfa.dFAParameters.count.toString())
                    val endTime = System.currentTimeMillis()
                    Log.d(
                        "FaceActivity",
                        "FPS: ${poseFeatureInput.freshCounts} / (${endTime - startTime})"
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }
//            val frame = Mat()
//            var count = 0
//            while (capture.read(frame)) {
//                Log.d("ccccc", "Count:$count")
//                if (frame.empty()) {
//                    break
//                }
//                Thread.sleep(50)
//                count++
//            }
            // 释放 VideoCapture 对象和相关资源
            capture.release()
        }
    }

    fun copyVideoFile(v: View) {
        utils.copyFileFromAssets(applicationContext, "kht/test1.mp4", "test1.mp4")
    }
}