package com.valhalla.bolt

import android.app.Application
import com.valhalla.bolt.di.initKoin

class Bolt: Application() {

    override fun onCreate() {
        super.onCreate()
        // Imported sources are never used directly by a native writer. Writers own a
        // separate flash workspace, which must not be swept while their outcome is unknown.
        java.io.File(cacheDir, "imports").deleteRecursively()
        initKoin()
    }

}