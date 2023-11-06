package com.ccc.khtdemo.base

interface BaseParameters

data class DFAParameters(
    var count: Int = 0,
    var doReset: Int = 0,
    var longTermParametersList: ArrayList<LongTermParameters> = arrayListOf()
) : BaseParameters

data class LongTermParameters(
    var lastFrameEnterCount: Int = -1
) : BaseParameters

data class ShortTermParameters(
    var kNNChecker: Boolean = false,
    var timeChecker: Boolean = false,
) : BaseParameters
