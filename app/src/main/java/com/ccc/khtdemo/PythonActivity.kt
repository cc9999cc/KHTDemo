package com.ccc.khtdemo

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.ByteArrayOutputStream
import java.util.Arrays

class PythonActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_python)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        //加载以下两个文件
        //FaceNetMobileNet.onnx
        //RetinaFaceMobileNet025.onnx
        // onnxruntime 环境
        var env: OrtEnvironment
        var session: OrtSession
        try {
            val inputStream = assets.open("face/FaceNetMobileNet.onnx");
            val buffer = ByteArrayOutputStream();
            var nRead = 0
            val data = ByteArray(1024)
            while (inputStream.read(data, 0, data.size).also { nRead = it } !== -1) {
                buffer.write(data, 0, nRead)
            }
            buffer.flush()
            val module: ByteArray = buffer.toByteArray()
            println("开始加载模型")
            env = OrtEnvironment.getEnvironment()
            session = env.createSession(module, OrtSession.SessionOptions())
            session.inputInfo.forEach { n ->
                val inputName: String = n.key
                val inputInfo: NodeInfo = n.value
                val shape =
                    (inputInfo.info as TensorInfo).shape
                val javaType =
                    (inputInfo.info as TensorInfo).type.toString()
                println(
                    "模型输入:  $inputName -> " + Arrays.toString(
                        shape
                    ) + " -> " + javaType
                )
            }
        } catch (e: Exception) {
            e.printStackTrace();
        }

//        val py = Python.getInstance()
//        val module = py.getModule("onnx")
//        try {
//            val result = module.callAttr("test")
//            Log.d("cccccc", "result:$result")
//        } catch (e: PyException) {
//            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
//        }
    }


}