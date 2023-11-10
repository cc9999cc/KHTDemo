package com.ccc.khtdemo.face

internal data class Result(
    var detectedIndices: List<Int> = emptyList(),
    var detectedScore: MutableList<Float> = mutableListOf<Float>(),
    var processTimeMs: Long = 0
)