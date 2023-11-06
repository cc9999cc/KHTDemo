package com.ccc.khtdemo

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class Utils {
    fun loadKnnArr(assets:AssetManager):Array<FloatArray>?
    {
        // Load KNNArgs.
        val stringBuilder = StringBuilder()
        try {
            val assetManager = assets
            val bf = BufferedReader(
                InputStreamReader(
                    assetManager.open("kht/KHT_KNNArgs.json")
                )
            )
            var line: String?
            while (bf.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
        val jsonString = stringBuilder.toString()
        var jarr: JSONArray? = null
        var jarr1: JSONArray? = null
        try {
            jarr = JSONArray(jsonString)
            jarr1 = jarr.getJSONArray(0)
        } catch (e: JSONException) {
            e.printStackTrace()
            return null
        }
        val KNNArgs = Array(jarr.length()) { FloatArray(jarr1.length()) }
        for (i in 0 until jarr.length()) {
            try {
                val innerJsonArray = jarr.getJSONArray(i)
                for (j in 0 until innerJsonArray.length()) {
                    KNNArgs[i][j] = innerJsonArray.getDouble(j).toFloat()
                    // dumplcate test.
                    // KNNArgs[i*2][j] = (float) innerJsonArray.getDouble(j);
                    // KNNArgs[i*2+1][j] = (float) innerJsonArray.getDouble(j);
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        // Load KNNArgs over.
        return KNNArgs
    }
}