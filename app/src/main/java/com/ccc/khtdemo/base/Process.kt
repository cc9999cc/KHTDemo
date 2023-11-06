package com.ccc.khtdemo.base

import android.util.Log
import com.ccc.khtdemo.ALog

abstract class BaseProcess {

    abstract fun process(
        parameterMap: Map<String, BaseParameters>,
        inputMap: MutableMap<String, BaseInput>
    )
}

class CountAddProcess : BaseProcess() {
    override fun process(
        parameterMap: Map<String, BaseParameters>,
        inputMap: MutableMap<String, BaseInput>
    ) {
        (parameterMap["DFAParameters"] as DFAParameters).count++
        Log.d("ccccccc", "数量:${ (parameterMap["DFAParameters"] as DFAParameters).count}")
    }
}

class UpdateLastFrameEnterCount : BaseProcess() {
    override fun process(
        parameterMap: Map<String, BaseParameters>,
        inputMap: MutableMap<String, BaseInput>
    ) {
        (parameterMap["LongTermParameters"] as LongTermParameters).lastFrameEnterCount =
            inputMap["FrameIndexInput"]?.easyGet(
                parameterMap, inputMap
            ) as Int
    }
}