package com.ccc.khtdemo

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
import com.ccc.khtdemo.base.KNNChecker
import com.ccc.khtdemo.base.PoseFeatureInput
import com.ccc.khtdemo.base.RawPoseFeatureInput
import com.ccc.khtdemo.base.ReverseTimeChecker
import com.ccc.khtdemo.base.SamePeopleChecker
import com.ccc.khtdemo.base.TimeChecker
import com.ccc.khtdemo.base.UpdateLastFrameEnterCount
import com.ccc.khtdemo.face.RetinafaceONNX
import com.ccc.khtdemo.face.RetinafaceONNX2
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import org.opencv.videoio.Videoio
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceActivity : AppCompatActivity() {
    private val utils: Utils = Utils()
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(MyApplication.appContext));
        }
    }

    override fun onResume() {
        super.onResume()
    }

    fun playVideo(v: View) {
        val videoView = findViewById<VideoView>(R.id.video_view);
        videoView.setMediaController(MediaController(this))
        val uri = File(cacheDir, "test1.mp4").toUri()
        videoView.setVideoURI(uri)
        videoView.start()
    }

    fun readVideoFile(v: View) {
        // 创建 VideoCapture 对象并打开视频文件
//        val outputFile = File(cacheDir, "test1.mp4")
//        val videoPath = outputFile.path
//        ALog.d("videoPath:${videoPath}")
//        if (!outputFile.exists()) {
//            ALog.e("文件不存在")
//            return
//        }
        val capture = VideoCapture(0, Videoio.CAP_ANDROID)
//        capture.open(videoPath, Videoio.CAP_ANDROID)
        // 检查视频是否成功打开
        if (!capture.isOpened()) {
            println("Error: 无法打开")
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
            capture.release()
        }
    }

    fun copyVideoFile(v: View) {
//        utils.copyFileFromAssets(applicationContext, "kht/test1.mp4", "test1.mp4")
        utils.copyFileFromAssets(applicationContext, "face/man1.png", "man1.png")
    }

    fun detectFace(v: View) {
        val py = Python.getInstance()
        val module = py.getModule("numpyUtil")
        backgroundExecutor.execute {
            val retinaface = RetinafaceONNX2(module)
            val imageFile = File(cacheDir, "man1.png")
            retinaface.getEncoding(imageFile.path)
//            val rgbImage = Mat()
//            Imgproc.cvtColor(Imgcodecs.imread(imageFile.path), rgbImage, Imgproc.COLOR_BGR2RGB)
//            val name = retinaface.detectImage(rgbImage)
//            ALog.d("人脸检测：$name")
        }

    }
}