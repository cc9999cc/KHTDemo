package com.ccc.khtdemo.base

import android.graphics.Bitmap
import android.util.Log
import com.ccc.khtdemo.MyApplication
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture

class FreshError(error: String) : Exception() {

}

/**
 * 刷新输入基类
 */
abstract class BaseInput {

    /**
     * 功能是刷新新的输入，刷新当前Input内置数据。
     * @param dfaParameters 横跨整个生命周期的函数，不会随着到达结束状态而重置。
     * @param longTermParameters 从一次初始状态到一次结束状态之间共享的参数。
     * @param shortTermParameters: 在一个状态内共享的参数。
     */
    abstract fun fresh(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    )

    /**
     * 获取最新的数据，返回为对应的数据，不做限制。
     */
    abstract fun get(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ): Any?

    fun easyGet(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ): Any? {
        return get(parameterMap, inputMap)
    }
}

class CameraInput(val capture: VideoCapture) :
    BaseInput() {
    //    private var rotatedBitmap: Bitmap? = null
//    var imageProxy: ImageProxy? = null
    private var rgbImage: Mat? = null

    override fun fresh(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ) {
        Log.d("ccccccc", "CameraInput start")
//        val frame = Mat()
//        capture.read(frame)
        val frame = Mat()
        capture.retrieve(frame)
        if (frame.empty()) {
            throw FreshError("视频结束")
        }
        rgbImage = Mat()
        Imgproc.cvtColor(frame, rgbImage, Imgproc.COLOR_BGR2RGB)
//        imageAnalyzer.setAnalyzer(backgroundExecutor) {
//            imageProxy = it
//            Log.d("ccccccc", "imageProxy:$imageProxy")
//        }
//        Log.d("ccccccc", "imageProxy:${imageProxy?.width}")
//        if (imageProxy == null) return
//        val bitmapBuffer =
//            Bitmap.createBitmap(
//                imageProxy!!.width,
//                imageProxy!!.height,
//                Bitmap.Config.ARGB_8888
//            )
//        imageProxy!!.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy!!.planes[0].buffer) }
//        val matrix = Matrix().apply {
//            postRotate(imageProxy!!.imageInfo.rotationDegrees.toFloat())
//        }
//        rotatedBitmap = Bitmap.createBitmap(
//            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
//            matrix, true
//        )
//        Log.d("ccccccc", "rotatedBitmap:$rotatedBitmap")
//        imageProxy!!.close()
//        imageProxy = null
        Log.d("ccccccc", "CameraInput end")
    }

    override fun get(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ): Mat? = rgbImage

}

class FrameIndexInput : BaseInput() {
    private var index = 1

    override fun fresh(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ) {
        index++
        Log.d("ccccc", "frame index:$index")
    }

    override fun get(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ): Any = index

}

/**
 * 原始骨骼点输出
 */
class RawPoseFeatureInput : BaseInput() {
    private var detector: PoseLandmarker
    private var detect: List<NormalizedLandmark>? = null
    private var freshCounts = 0

    init {
        val baseOptionBuilder = BaseOptions.builder()
        baseOptionBuilder.setDelegate(Delegate.CPU)
        baseOptionBuilder.setModelAssetPath("pose_landmarker_full.task")
        val baseOptions = baseOptionBuilder.build()
        val optionsBuilder =
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setRunningMode(RunningMode.IMAGE)
//                .setRunningMode(RunningMode.LIVE_STREAM)
//        optionsBuilder
//            .setResultListener(this::returnLivestreamResult)
//            .setErrorListener(this::returnLivestreamError)
        val options = optionsBuilder.build()
        detector =
            PoseLandmarker.createFromOptions(MyApplication.appContext, options)

    }

    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: MPImage
    ) {
        Log.d("cccccc", "原始骨骼点输出 returnLivestreamResult")

//        val finishTimeMs = SystemClock.uptimeMillis()
//        val inferenceTime = finishTimeMs - result.timestampMs()
//        detect = result.landmarks()

        if (result.landmarks().size > 0) {
            detect = result.landmarks()[0]
        }
        if (detect != null && detect!!.size > 0) {
            Log.d("ccccccc", "原始骨骼点输出: detect:${detect!!.size}")
        }
    }

    private fun returnLivestreamError(error: RuntimeException) {
        Log.e("cccccc", "原始骨骼点输出 error:${error.message}")
    }

    //原始骨骼点输出
    override fun fresh(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ) {
        Log.d("ccccccc", "原始骨骼点输出 start")
        freshCounts++


//        val rotatedBitmap = inputMap["CameraInput"]!!.easyGet(parameterMap, inputMap) as Bitmap
//        Log.d("ccccccc", "原始骨骼点输出: rotatedBitmap:${rotatedBitmap}")
//        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        val mat = inputMap["CameraInput"]!!.easyGet(parameterMap, inputMap) as Mat
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        val mpImage = BitmapImageBuilder(bitmap).build()
//        detector.detectAsync(mpImage,System.currentTimeMillis())
        val result = detector.detect(mpImage)
        if (result.landmarks().size > 0) {
            detect = result.landmarks()[0]
        }
        Log.d("ccccccc", "原始骨骼点输出: detect:${detect?.size}")
        Log.d("ccccccc", "原始骨骼点输出 end")
    }

    override fun get(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ): List<NormalizedLandmark>? = detect

}

class PoseFeatureInput : BaseInput() {
    private var feature = FloatArray(24)
    var freshCounts = 0

    //归一化骨骼点输出
    override fun fresh(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ) {
        Log.d("ccccccc", "归一化骨骼点输出 start")
        if (inputMap["RawPoseFeatureInput"]?.easyGet(
                parameterMap,
                inputMap
            ) == null
        ) return
        val detect: List<NormalizedLandmark> =
            inputMap["RawPoseFeatureInput"]?.easyGet(
                parameterMap,
                inputMap
            ) as List<NormalizedLandmark>
//        Log.d("ccccccc", "归一化骨骼点输出 detect：$detect")
        if (detect.isEmpty()) return
        feature = FloatArray(24)
        //肩膀、胯、膝盖、脚踝、手肘、手腕
        val selectIndex = intArrayOf(11, 12, 23, 24, 25, 26, 27, 28, 13, 14, 15, 16)
        for (i in 0..23) {
            feature[i] = 0.0f
        }
        val landmarks: List<NormalizedLandmark> = detect
        if (landmarks.size < 32) return
        for (i in 0..11) { //以此拿到各部位的坐标数据，偶数下标是x,奇数下标是y
            feature[i * 2] = landmarks[selectIndex[i]].x()
            feature[i * 2 + 1] = landmarks[selectIndex[i]].y()
        }
        var minx = feature[0]
        var maxx = feature[0]
        var miny = feature[1]
        var maxy = feature[1]
        run {
            var i = 2
            while (i < 24) {
                //获取到数据包最大和最小的x y坐标
                maxx = java.lang.Float.max(maxx, feature[i])
                minx = java.lang.Float.min(minx, feature[i])
                maxy = java.lang.Float.max(maxy, feature[i + 1])
                miny = java.lang.Float.min(miny, feature[i + 1])
                i += 2
            }
        }
        for (i in 0..11) { //这个用来干嘛呢？？？
            feature[i * 2] = (feature[i * 2] - minx) / (maxx - minx)
            feature[i * 2 + 1] = (feature[i * 2 + 1] - miny) / (maxy - miny)
        }
        Log.d("ccccccc", "归一化骨骼点输出 feature:${feature}")
        Log.d("ccccccc", "归一化骨骼点输出 end")
    }

    override fun get(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ) = feature

}
