package com.ccc.khtdemo

import android.app.Application
import android.content.Context
import android.util.Log
import org.opencv.android.InstallCallbackInterface
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader


class MyApplication : Application() {
    companion object {
        lateinit var instance: MyApplication
        lateinit var appContext: Context
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        appContext = applicationContext

        if (!OpenCVLoader.initDebug()) {
            Log.d(
                "OpenCV",
                "Internal OpenCV library not found. Using OpenCV Manager for initialization"
            );
            OpenCVLoader.initAsync(
                OpenCVLoader.OPENCV_VERSION_3_0_0,
                this,
                object : LoaderCallbackInterface {
                    override fun onManagerConnected(status: Int) {
                        when (status) {
                            LoaderCallbackInterface.SUCCESS -> {
                                //这里加载完成才可以去调用OpenCV的相关方法。
                                Log.i("OpenCV", "OpenCV loaded successfully")
                            }

                            else -> {
                                Log.i("OpenCV", "OpenCV loaded failed")
                            }
                        }
                    }

                    override fun onPackageInstall(
                        operation: Int,
                        callback: InstallCallbackInterface?
                    ) {

                    }

                })
        }
    }
}