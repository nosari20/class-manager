package edu.fnosari.classmanager.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

object PhotoUtil {
    fun importPhoto(context: Context, source: Uri, photosDir: File): String {
        val bytes = context.contentResolver.openInputStream(source)!!.use { it.readBytes() }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= 512) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            ?: throw IOException("cannot decode image")
        val scale = 512f / maxOf(bitmap.width, bitmap.height)
        val finalBmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
            )
        } else bitmap
        val name = "${UUID.randomUUID()}.jpg"
        File(photosDir, name).outputStream().use {
            finalBmp.compress(Bitmap.CompressFormat.JPEG, 85, it)
        }
        return "photos/$name"
    }

    fun photoFile(context: Context, relPath: String): File = File(context.filesDir, relPath)

    fun newCaptureUri(context: Context): Pair<Uri, File> {
        val dir = File(context.cacheDir, "capture").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return uri to file
    }
}
