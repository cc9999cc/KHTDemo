package com.ccc.khtdemo.face

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.SystemClock
import android.util.Log
import com.ccc.khtdemo.ALog
import com.ccc.khtdemo.MyApplication
import com.chaquo.python.PyObject
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.exp


class RetinafaceONNX(private val np: PyObject) {
    private val confidence = 0.5
    private val nms_iou = 0.3
    private val retinaface_input_shape = intArrayOf(640, 640, 3)
    private val facenet_input_shape = intArrayOf(160, 160, 3)
    private val facenet_threhold = 0.9
    private val variance = doubleArrayOf(0.1, 0.2)
    private var anchors: Array<Array<Float>>? = null
    private var have_faces = false

    private val net: OrtSession?
    private val facenet: OrtSession?
    private var known_face_encodings: Array<Array<Float>>? = null
    private var known_face_names: Array<String>? = null

    init {
        anchors = np.callAttr("loadAnchors").toJava(Array<Array<Float>>::class.java)
        net = loadOnnxFile("RetinaFaceMobileNet025.onnx")
        facenet = loadOnnxFile("FaceNetMobileNet.onnx")
        loadFaces()
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
//            session.inputInfo.forEach { n ->
//                val inputName: String = n.key
//                val inputInfo: NodeInfo = n.value
//                val shape =
//                    (inputInfo.info as TensorInfo).shape
//                val javaType =
//                    (inputInfo.info as TensorInfo).type.toString()
//                println(
//                    "模型输入:  $inputName -> " + Arrays.toString(
//                        shape
//                    ) + " -> " + javaType
//                )
//            }
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

    private fun getEncoding(imageMat: Mat) {
        // 创建三维数组
        val array3D =
            Array(imageMat.rows()) { Array(imageMat.cols()) { IntArray(imageMat.channels()) } }
//
//        val startTime = System.currentTimeMillis()
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
//        ALog.d("mat转三维数组用时： ${System.currentTimeMillis() - startTime}")

        val imgWidth = imageMat.width()
        val imgHeight = imageMat.height()
        val imageChannel = imageMat.channels()
        val scale = arrayOf(imgWidth, imgHeight, imgWidth, imgHeight)
        val scale_for_landmarks = arrayOf(
            imgWidth, imgHeight, imgWidth, imgHeight,
            imgWidth, imgHeight, imgWidth, imgHeight,
            imgWidth, imgHeight
        )


        var result = Result()
        val totalPixels = imageMat.rows() * imageMat.cols()
        val data = FloatArray(totalPixels * imageChannel)
        ALog.d("开始执行py")
        val imageArray = np.callAttr(
            "formatImage",
            array3D,
            retinaface_input_shape
        ).toJava(Array<Array<Array<Array<Float>>>>::class.java)
//        imageMat.get(0, 0, data);
        ALog.d("开始计算totalSize")
        val totalSize =
            imageArray.size * imageArray[0].size * imageArray[0][0].size * imageArray[0][0][0].size
        ALog.d("totalSize:$totalSize")
        val byteBuffer = ByteBuffer.allocateDirect(totalSize * 4)  // 乘以 4 是因为每个 float 占用 4 个字节
        val floatBuffer = byteBuffer.asFloatBuffer()
        for (i in imageArray.indices) {
            for (j in imageArray[i].indices) {
                for (k in imageArray[i][j].indices) {
                    for (h in imageArray[i][j][k].indices) {
//                        Log.d("cccccc", "$i $j $k $h")
                        floatBuffer.put(imageArray[i][j][k][h])
                    }
                }
            }
        }
//        ALog.d("floatBuffer.size:${floatBuffer}")
//        val imgData = FloatBuffer.wrap(image)
        val inputName = net!!.inputNames.first()
        val shape = longArrayOf(
            imageArray.size.toLong(),
            imageArray[0].size.toLong(),
            imageArray[0][0].size.toLong(),
            imageArray[0][0][0].size.toLong()
        )
        val tensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), floatBuffer, shape)
        val startTime = SystemClock.uptimeMillis()
        ALog.d("推理开始")
        tensor.use {
            val output = net.run(Collections.singletonMap(inputName, tensor))
            output.use {
                result.processTimeMs = SystemClock.uptimeMillis() - startTime
                ALog.d("推理结束${result.processTimeMs}")
                @Suppress("UNCHECKED_CAST")
                val rawOutput = ((output?.get(0)?.value) as Array<FloatArray>)[0]
                val probabilities = softMax(rawOutput)
                result.detectedIndices = getTop3(probabilities)
                for (idx in result.detectedIndices) {
                    result.detectedScore.add(probabilities[idx])
                }
            }
        }

        //loc, conf, landms = self.net.run(None, {"input": image})
//        Log.d("ccccc", "image${image}")
    }

    // Get index of top 3 values
    // This is for demo purpose only, there are more efficient algorithms for topK problems
    private fun getTop3(labelVals: FloatArray): List<Int> {
        var indices = mutableListOf<Int>()
        for (k in 0..2) {
            var max: Float = 0.0f
            var idx: Int = 0
            for (i in 0..labelVals.size - 1) {
                val label_val = labelVals[i]
                if (label_val > max && !indices.contains(i)) {
                    max = label_val
                    idx = i
                }
            }

            indices.add(idx)
        }

        return indices.toList()
    }

    // Calculate the SoftMax for the input array
    private fun softMax(modelResult: FloatArray): FloatArray {
        val labelVals = modelResult.copyOf()
        val max = labelVals.max()
        var sum = 0.0f

        // Get the reduced sum
        for (i in labelVals.indices) {
            labelVals[i] = exp(labelVals[i] - max)
            sum += labelVals[i]
        }

        if (sum != 0.0f) {
            for (i in labelVals.indices) {
                labelVals[i] /= sum
            }
        }

        return labelVals
    }

    fun detectImage(image: Mat): String {
//        if (!have_faces) return "Unkown, No faces in lib"
//        //人脸特征比对
        val face_encoding = getEncoding(image)
//        if (face_encoding) return "Unknown, No face"
        return "test"
    }
}

