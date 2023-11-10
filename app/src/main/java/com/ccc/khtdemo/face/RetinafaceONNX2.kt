package com.ccc.khtdemo.face

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.SystemClock
import com.ccc.khtdemo.ALog
import com.ccc.khtdemo.MyApplication
import com.ccc.khtdemo.preProcess
import com.chaquo.python.PyObject
import com.google.protobuf.Mixin
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import java.lang.Math.exp
import java.util.Collections


class RetinafaceONNX2(private val np: PyObject) {
    private val confidence = 0.5
    private val nms_iou = 0.3
    private val retinaface_input_shape = intArrayOf(640, 640, 3)
    private val facenet_input_shape = intArrayOf(160, 160, 3)
    private val facenet_threhold = 0.9
    private val variance = floatArrayOf(0.1f, 0.2f)
    private var anchors: Array<Array<Float>>? = null
    private var have_faces = false

    private val net: OrtSession?

    //    private val facenet: OrtSession?
    private var known_face_encodings: Array<Array<Float>>? = null
    private var known_face_names: Array<String>? = null

    init {
        anchors = np.callAttr("loadAnchors").toJava(Array<Array<Float>>::class.java)
        net = loadOnnxFile("RetinaFaceMobileNet025.onnx")
//        facenet = loadOnnxFile("FaceNetMobileNet.onnx")
//        loadFaces()
    }

    private fun loadOnnxFile(finaName: String): OrtSession? {
        try {
            val inputStream = MyApplication.appContext.assets.open("face/$finaName");
            val buffer = ByteArrayOutputStream();
            var nRead = 0
            val data = ByteArray(1024)
            while (inputStream.read(data, 0, data.size).also { nRead = it } !== -1) {
                buffer.write(data, 0, nRead)
            }
            buffer.flush()
            val module: ByteArray = buffer.toByteArray()
            return OrtEnvironment.getEnvironment()
                .createSession(module, OrtSession.SessionOptions())
        } catch (e: Exception) {
            e.printStackTrace();
        }
        return null
    }

    private fun loadFaces() {
        have_faces = try {
            known_face_encodings = np.callAttr("loadFaceEncoding")
                .toJava(Array<Array<Float>>::class.java)
            known_face_names = np.callAttr("loadFaceName").toJava(Array<String>::class.java)
            ALog.d("模型加载成功")
            true
        } catch (e: Exception) {
            ALog.d("未找到已有人脸，只能进行编码不能进行检测")
            e.printStackTrace()
            false
        }
    }

    fun getEncoding(path: String) {
//        val imageArr3d = mat2Arr(imageMat)
//        letterbox_image(imageArr3d)
//        val imgWidth = imageMat.width()
//        val imgHeight = imageMat.height()
//        val imageChannel = imageMat.channels()
//        val scale = arrayOf(imgWidth, imgHeight, imgWidth, imgHeight)
//        val scale_for_landmarks = arrayOf(
//            imgWidth, imgHeight, imgWidth, imgHeight,
//            imgWidth, imgHeight, imgWidth, imgHeight,
//            imgWidth, imgHeight
//        )
        //resize图像，rgb量化
        val imgData = preProcess(path)
        //跑模型
        val inputName = net?.inputNames?.iterator()?.next()
        val shape = longArrayOf(
            1,
            3,
            retinaface_input_shape[1].toLong(),
            retinaface_input_shape[0].toLong()
        )
        val env = OrtEnvironment.getEnvironment()
        env.use {
            val tensor = OnnxTensor.createTensor(env, imgData, shape)
            val startTime = SystemClock.uptimeMillis()
            tensor.use {
                val output = net?.run(Collections.singletonMap(inputName, tensor))
                output?.use {
                    ALog.d("net.run 执行时间:${SystemClock.uptimeMillis() - startTime}")
                    val locTensor = (it[0] as OnnxTensor).info// 假设定位数据在结果列表的第一个张量
                    val confTensor = (it[1] as OnnxTensor).info // 假设置信度数据在结果列表的第二个张量
                    val landmsTensor = (it[2] as OnnxTensor).info // 假设关键点数据在结果列表的第三个张量
                    val numBoxes = locTensor.shape // 根据定位数据的形状确定边界框数量
                    val loc = locTensor.makeCarrier() as Array<Array<Array<Float>>>
                    val boxes = decode(loc[0])

                    //conf = conf[0, :, 1:2]
                    val conf = confTensor.makeCarrier() as Array<Array<Array<Float>>>
                    val confArr = Array(conf[0].size) { i -> arrayOf(conf[0][i][0], conf[0][i][1]) }
//                    val confList = mutableListOf<Array<Float>>()
//                    for (i in conf[0].indices) {
//                        val newBox = mutableListOf<Float>()
//                        newBox.add(conf[0][i][0])
//                        newBox.add(conf[0][i][1])
//                        confList.add(newBox.toTypedArray())
//                    }
//                    val confArr = confList.toTypedArray()

                    //landms = decode_landm(landms[0], anchors, self.variance)
                    val landms = landmsTensor.makeCarrier() as Array<Array<Array<Float>>>
//                    decode_landm(landms[0])

                }
            }
        }
    }

    /**
     * 辅助函数，解码人脸区域
     */
    private fun decode(loc: Array<Array<Float>>): Array<Array<Float>> {
        val priors = anchors!!
        val variances = variance
        val boxes = mutableListOf<Array<Float>>()

        for (i in loc.indices) {
            val prior = priors[i]
            val newBox = mutableListOf<Float>()
            for (j in 0 until 2) {
                newBox.add(prior[j] + loc[i][j] * variances[0] * prior[j + 2])
            }
            for (j in priors.indices) {
                newBox.add(priors[i][j + 2] * kotlin.math.exp(loc[i][j + 2] * variances[1]))
            }

            newBox[0] -= newBox[1] / 2.0f
            newBox[1] += newBox[0]
            boxes.add(newBox.toTypedArray())
        }
        return boxes.toTypedArray()
    }

    /**
     *
     */
//    private fun decode_landm(pre: Array<Array<Float>>): Array<Array<Float>> {
//        val priors = anchors!!
//        val variances = variance
//
//    }

    /**
     * # 辅助函数，做resize的
     * # c:是用来调整图像的大小并将其填充到特定大小的盒子中的。这里的图像大小调整和填充方式可以使图像保持原有的长宽比，并使其适应新的大小。
     */
    private fun resizeImage(inputPath: String, width: Int, height: Int): Bitmap {
        val bitmap = BitmapFactory.decodeFile(inputPath)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /**
     * # 辅助函数，对RGB值域做量化的
     * # c:图像预处理的一个例子，主要用于减去图像的平均值，使图像的亮度得到调整。
     * # c:这种预处理方式通常用于一些深度学习模型，如VGG16，在输入图像数据集前，将RGB三个通道的均值（这里分别是104，117，123）从原始图像中减去，
     * # c:这样做可以减少数据的分布范围，提高模型的收敛速度。同时，也使得不同亮度的图片经过模型处理后输出的结果更为一致
     */
    private fun preprocessInput(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height * 3)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in 0 until pixels.size) {
            pixels[i] -= 104 // Red channel
            pixels[i + width * height] -= 117 // Green channel
            pixels[i + width * height * 2] -= 123 // Blue channel
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.RGB_565)
    }

    private fun mat2Arr(imageMat: Mat): Array<Array<IntArray>> {
        // 创建三维数组
        val array3D =
            Array(imageMat.rows()) { Array(imageMat.cols()) { IntArray(imageMat.channels()) } }
        val startTime = System.currentTimeMillis()
        // 遍历Mat并填充三维数组
        for (row in 0 until imageMat.rows()) {
            for (col in 0 until imageMat.cols()) {
                val pixel = ByteArray(imageMat.channels())
                imageMat.get(row, col, pixel)
                for (c in 0 until imageMat.channels()) {
                    array3D[row][col][c] = pixel[c].toInt() and 0xFF
                }
            }
        }
        ALog.d("mat转三维数组用时： ${System.currentTimeMillis() - startTime}")
        return array3D
    }

    fun detectImage(image: Mat): String {
//        if (!have_faces) return "Unkown, No faces in lib"
//        //人脸特征比对
//        val face_encoding = getEncoding(image)
//        if (face_encoding) return "Unknown, No face"
        return "test"
    }
}