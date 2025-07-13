package com.valhalla.bolt

import android.app.Application
import com.valhalla.bolt.di.initKoin

class Bolt: Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin()
    }

}