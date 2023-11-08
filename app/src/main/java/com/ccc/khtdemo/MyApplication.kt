package com.ccc.khtdemo

import android.app.Application
import android.content.Context


class MyApplication : Application() {
    companion object {
        lateinit var instance: MyApplication
        lateinit var appContext: Context
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        appContext = applicationContext
            }
}