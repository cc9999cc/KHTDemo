package com.ccc.khtdemo.base

import android.util.Log
import com.ccc.khtdemo.ALog
import com.ccc.khtdemo.MyApplication
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class DFAController(
    val inputMap: MutableMap<String, BaseInput>,
    val inputFreshOrder: ArrayList<String>,
    val checkerMap: MutableMap<String, BaseChecker>,
    val postProcessMap: MutableMap<String, BaseProcess>,
    val dfaDesPath: String,
) {
    private var startState = ""
    private var endStates = ""
    private var transition = JSONObject()
    val dFAParameters: DFAParameters = DFAParameters()
    private var shortTermParameters = ShortTermParameters()
    private var longTermParameters = LongTermParameters()
    private var totalParameters: MutableMap<String, BaseParameters>
    private var nowState = ""
    var run: Boolean = false

    init {
        loadDFA()
        nowState = startState
        totalParameters = mutableMapOf(
            "DFAParameters" to dFAParameters,
            "ShortTermParameters" to shortTermParameters,
            "LongTermParameters" to longTermParameters,
        )
    }

    private fun loadDFA() {
        val stringBuilder = StringBuilder()
        try {
            val assetManager = MyApplication.appContext.assets
            val bf = BufferedReader(
                InputStreamReader(
                    assetManager.open(dfaDesPath)
                )
            )
            var line: String?
            while (bf.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val jsonArray = JSONArray(stringBuilder.toString())
        if (jsonArray.length() < 2) {
            Log.e("ccccccc", "配置文件${dfaDesPath}不符合标准")
            return
        }
        val jsonArray1 = jsonArray.optJSONArray(0)
        if (jsonArray1 == null) {
            Log.e("ccccccc", "配置文件第一项类型错误")
            return
        }
        when (jsonArray[1].toString().substring(0, 1)) {
            "{" -> {//详细模式
                startState = jsonArray1.optString(0) //开始节点
                endStates = jsonArray1.optJSONArray(1).optString(0) //结束节点
                transition = jsonArray.optJSONObject(1) //过程
                Log.d("ccccccc", "start:$startState")
                Log.d("ccccccc", "end:$endStates")
                Log.d("ccccccc", "center:$transition")
            }

            "[" -> {//简略模式 后面再写吧

            }

            else -> {
                Log.e("ccccccc", "配置文件第二项类型错误")
                return
            }
        }
    }

    fun run(): Boolean {
        run = true
        while (!step() && run) {
            Log.d("ccccccc", "run")
            Thread.sleep(50)
            continue
        }
        return run
    }

    fun step(): Boolean {
        Log.d("ccccccc", "controller step start")
        //刷新所有输入
        shortTermParameters = ShortTermParameters()
        freshAllInput()
        // 检查下一个状态序号
        val nextState = getNextState()
        Log.d("ccccccc", "nextState:$nextState")
        Log.d("ccccccc", "nowState:$nowState")
        // 执行后处理操作
        doPostProcess(nextState)
        // 更新状态，并判定是否到达结束节点
        nowState = nextState
        Log.d("ccccccc", "controller step end")
        if (nowState == endStates) {
            reset() //重置当前判定链, 并记录
            return true
        }
        return false
    }

    private fun freshAllInput() {
        for (inputName in inputFreshOrder) {
            val inputInst = inputMap[inputName]
            try {
                inputInst?.fresh(
                    totalParameters,
                    inputMap
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getNextState(): String {
        val nowStateJson = transition.optJSONObject(nowState)
        //获取判定进入下一个状态的所有检查函数
        for (nextState in nowStateJson.keys()) {
            //获取判定进入下一个状态的所有检查函数
            val checkerList = nowStateJson!!.optJSONArray(nextState)!!.optJSONArray(0)
            //依次执行该判定
            var okOrNot = true
            //注：如果没有任何判定条件，则自动认为True
            for (index in 0 until checkerList.length()) {
                val checkerOne = checkerList.optJSONArray(index)
                var result: Boolean
                try {
                    result = checkerMap[checkerOne.optString(0)]?.check(
                        totalParameters, inputMap, checkerOne
                    ) ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    result = false
                    Log.e("ccccccc", "[Error] check {${checkerOne.optString(0)}}, error: {${e.message}}")
                }
                Log.d("ccccccc", "check:${checkerOne.optString(0)},result:$result")
                okOrNot = okOrNot and result
                Log.d("ccccccc", "okOrNot:${okOrNot}")
                //创建跳出循环的快照
                if (!okOrNot)
                    break
            }
            if (okOrNot)
                return nextState
        }
        return startState
    }

    private fun doPostProcess(nextState: String) {
        Log.d("ccccccc", "controller doPostProcess start")
        Log.d("ccccccc", "doPostProcess:$nextState,nowState:$nowState")
        val nowStateJson = transition.optJSONObject(nowState)
        val nextStateArr = nowStateJson.optJSONArray(nextState)
        Log.d("ccccccc", "nowStateJson:$nowStateJson")
        Log.d("ccccccc", "nextStateArr:$nextStateArr")
        if (nextStateArr == null || nextState == startState) {
            Log.d("ccccccc", "controller doPostProcess end1")
            return
        }
        val postProcessList = nextStateArr.optJSONArray(1)
        for (index in 0 until postProcessList.length()) {
            val postProcessOne = postProcessList.optJSONArray(index).optString(0)
            try {
                postProcessMap[postProcessOne]?.process(
                    totalParameters,
                    inputMap = inputMap
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("ccccccc", "[Error] postProcess {${postProcessOne}}, error: {${e.message}}")
            }

        }
        Log.d("ccccccc", "controller doPostProcess end2")

    }

    private fun reset() {
        dFAParameters.longTermParametersList.add(longTermParameters)
        dFAParameters.doReset++
        longTermParameters = LongTermParameters()
        nowState = startState
    }
}