package com.ccc.khtdemo.base

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.formats.proto.LandmarkProto

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

class CameraInput :
    BaseInput() {
    private var rotatedBitmap: Bitmap? = null
    var imageProxy: ImageProxy? = null

    override fun fresh(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ) {
//        imageAnalyzer.setAnalyzer(backgroundExecutor) {
//            imageProxy = it
//            Log.d("ccccccc", "imageProxy:$imageProxy")
//        }
        Log.d("ccccccc", "CameraInput start")
        Log.d("ccccccc", "imageProxy:${imageProxy?.width}")
        if (imageProxy == null) return
        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy!!.width,
                imageProxy!!.height,
                Bitmap.Config.ARGB_8888
            )
        imageProxy!!.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy!!.planes[0].buffer) }
        val matrix = Matrix().apply {
            postRotate(imageProxy!!.imageInfo.rotationDegrees.toFloat())
        }
        rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )
        Log.d("ccccccc", "rotatedBitmap:$rotatedBitmap")
        imageProxy!!.close()
        imageProxy = null
        Log.d("ccccccc", "CameraInput end")
    }

    override fun get(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ): Bitmap? = rotatedBitmap

}

class FrameIndexInput : BaseInput() {
    private var index = 1

    override fun fresh(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ) {
        index++
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
//    private var detector: PoseLandmarker
//    private var detect: List<List<NormalizedLandmark>>? = null
    var detect: List<com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark>? = null
    private var freshCounts = 0

//    init {
//        val baseOptionBuilder = BaseOptions.builder()
//        baseOptionBuilder.setDelegate(Delegate.CPU)
//        baseOptionBuilder.setModelAssetPath("pose_landmarker_full.task")
//        val baseOptions = baseOptionBuilder.build()
//        val optionsBuilder =
//            PoseLandmarker.PoseLandmarkerOptions.builder()
//                .setBaseOptions(baseOptions)
//                .setMinPoseDetectionConfidence(0.5f)
//                .setMinTrackingConfidence(0.5f)
//                .setMinPosePresenceConfidence(0.5f)
//                .setRunningMode(RunningMode.LIVE_STREAM)
//        optionsBuilder
//            .setResultListener(this::returnLivestreamResult)
//            .setErrorListener(this::returnLivestreamError)
//        val options = optionsBuilder.build()
//        detector =
//            PoseLandmarker.createFromOptions(MyApplication.appContext, options)
//    }

//    private fun returnLivestreamResult(
//        result: PoseLandmarkerResult,
//        input: MPImage
//    ) {
//        Log.d("cccccc","原始骨骼点输出 returnLivestreamResult")
//        val finishTimeMs = SystemClock.uptimeMillis()
//        val inferenceTime = finishTimeMs - result.timestampMs()
//        detect = result.landmarks()
//        if (detect!=null && detect!!.size >0){
//            Log.d("ccccccc", "原始骨骼点输出: detect:${detect!![0].size}")
//        }
//    }
//
//    private fun returnLivestreamError(error: RuntimeException) {
//        Log.e("cccccc","原始骨骼点输出 error:${error.message}")
//    }

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
//        detector.detectAsync(mpImage,System.currentTimeMillis())
//        val result = detector.detect(mpImage)
//        detect = result.landmarks()
//        Log.d("ccccccc", "原始骨骼点输出: detect:${detect?.size}")
        Log.d("ccccccc", "原始骨骼点输出 end")
    }

    override fun get(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>
    ): List<com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark>? = detect

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
//        val detect: List<List<NormalizedLandmark>> = inputMap["RawPoseFeatureInput"]?.easyGet(
//            parameterMap,
//            inputMap
//        ) as List<List<NormalizedLandmark>>
        val detect: List<LandmarkProto.NormalizedLandmark> = inputMap["RawPoseFeatureInput"]?.easyGet(
            parameterMap,
            inputMap
        ) as List<LandmarkProto.NormalizedLandmark>
        Log.d("ccccccc", "归一化骨骼点输出 detect：$detect")
        if (detect.isEmpty()) return
        feature = FloatArray(24)
        //肩膀、胯、膝盖、脚踝、手肘、手腕
        val selectIndex = intArrayOf(11, 12, 23, 24, 25, 26, 27, 28, 13, 14, 15, 16)
        for (i in 0..23) {
            feature[i] = 0.0f
        }
//        val landmarks: List<NormalizedLandmark> = detect[0]
        val landmarks: List<LandmarkProto.NormalizedLandmark> = detect
        if (landmarks.size < 32) return
        for (i in 0..11) { //以此拿到各部位的坐标数据，偶数下标是x,奇数下标是y
            feature[i * 2] = landmarks[selectIndex[i]].x
            feature[i * 2 + 1] = landmarks[selectIndex[i]].y
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
