package vn.ptit.fwp.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Ảnh từ thiết bị → data URL JPEG, cạnh dài ≤ 1600px (dưới giới hạn 4 MB của backend). */
suspend fun encodeImageDataUrl(context: Context, uri: String, maxSide: Int = 1600): String = withContext(Dispatchers.IO) {
    val parsed = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(parsed).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bitmap = context.contentResolver.openInputStream(parsed).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: throw IllegalStateException("Không đọc được ảnh đã chọn.")
    val bytes = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
    "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}
