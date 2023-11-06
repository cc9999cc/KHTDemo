package com.ccc.khtdemo.base

import android.util.Log
import android.util.Pair
import com.ccc.khtdemo.ALog
import org.json.JSONArray
import kotlin.math.pow

abstract class BaseChecker() {
    abstract fun check(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>,
        vararg params: Any
    ): Boolean
}

class KNNChecker(private val knnArr: Array<FloatArray>) : BaseChecker() {
    override fun check(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>,
        vararg params: Any
    ): Boolean {
        Log.d("ccccccc", "KNNChecker start")
        val checkerOne: JSONArray = params[0] as JSONArray
        val kNNSamples = checkerOne.optInt(1)
        val threshold = checkerOne.optDouble(2)
        val keyFrameIndex = checkerOne.optInt(3)
        val feature = inputMap["PoseFeatureInput"]!!.easyGet(parameterMap, inputMap) as FloatArray

        // Calculate the Nearest Pose.
        val distances = ArrayList<Pair<Double, Int>>() //距离
        val min_dis = 1000000
        for (knnArg in knnArr) {
            var distance = 0.0
            for (j in feature.indices) {
                distance += (feature[j] - knnArg[j]).toDouble().pow(2.0) //平方累加
            }
//            Log.d("ccccccc","distanceSum:${distance},threshold${threshold}")
            if (distance <= threshold) { //算出来的值如果小于阀值
                distances.add(Pair(distance, knnArg[knnArg.size - 1].toInt()))
            }
        }
        //排序
        distances.sortWith(Comparator.comparingDouble { o: Pair<Double, Int> -> o.first!! })

        var labels_count = HashMap<Int?, Int>()
        var max_label = Pair(-1, 0)
        for (i in 0 until kNNSamples.coerceAtMost(distances.size)) { //循环距离集合，最少循环N次
            val label = distances[i].second
            labels_count[label] =
                labels_count.getOrDefault(label, 0) + 1
            if (labels_count[label]!! > max_label.second!!) {
                max_label = Pair(label, labels_count[label])
            }
        }
        val result = max_label.first == keyFrameIndex
        (parameterMap["ShortTermParameters"] as ShortTermParameters).kNNChecker = result

        Log.d("ccccccc", "KNNChecker:${result}")
        Log.d("ccccccc", "KNNChecker end")
        return result
    }
}


class TimeChecker() : BaseChecker() {
    override fun check(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>,
        vararg params: Any
    ): Boolean {
        Log.d("ccccccc", "TimeChecker start")
        val jsonArr: JSONArray = params[0] as JSONArray
        val time = jsonArr.optInt(1)

        val lastFrameEnterCount =
            (parameterMap["LongTermParameters"] as LongTermParameters).lastFrameEnterCount
        val nowFrameIndexCount = inputMap["FrameIndexInput"]!!.easyGet(
            parameterMap, inputMap
        ) as Int
        val result = nowFrameIndexCount - lastFrameEnterCount <= time
        (parameterMap["ShortTermParameters"] as ShortTermParameters).timeChecker = result
        Log.d("ccccccc", "TimeChecker:${result}")
        Log.d("ccccccc", "TimeChecker end")
        return result
    }
}

class ReverseTimeChecker : BaseChecker() {
    override fun check(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>,
        vararg params: Any
    ): Boolean = !(parameterMap["ShortTermParameters"] as ShortTermParameters).timeChecker

}

class SamePeopleChecker : BaseChecker() {
    override fun check(
        parameterMap: Map<String, BaseParameters>,
        inputMap: Map<String, BaseInput>,
        vararg params: Any
    ): Boolean = true
}