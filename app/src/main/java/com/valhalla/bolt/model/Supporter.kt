package com.valhalla.bolt.model

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


private val supporter: Supporter = Supporter.getInstance()


class Supporter {

    companion object {
        var INSTANCE: Supporter? = null

        fun getInstance(): Supporter {
            return INSTANCE ?: synchronized(this) {
                val instance = Supporter()
                INSTANCE = instance
                instance
            }
        }
    }

    @Throws(IOException::class)
    fun exportAssets(context: Context, src: String, out: String) {
        val fileNames = context.assets.list(src)
        if (fileNames!!.size > 0) {
            val file = File(out)
            if (file.exists() || file.mkdirs())
                for (fileName in fileNames) {
                    exportAssets(context, "$src/$fileName", "$out/$fileName")
                }
        } else {
            val asset = context.assets.open(src)
            val fos = FileOutputStream(File(out))
            val buffer = ByteArray(1024)
            var byteCount = 0
            while ((asset.read(buffer).also { byteCount = it }) != -1) {
                fos.write(buffer, 0, byteCount)
            }
            fos.flush()
            asset.close()
            fos.close()
        }
    }


}