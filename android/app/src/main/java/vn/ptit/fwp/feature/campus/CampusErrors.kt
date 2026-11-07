package vn.ptit.fwp.feature.campus

import retrofit2.HttpException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal fun campusError(error: Throwable): String = when {
    error is IllegalStateException -> error.message ?: "Campus Feed chưa được cấu hình."
    error is HttpException -> when (error.code()) {
        400 -> "Dữ liệu chưa hợp lệ. Hãy kiểm tra nội dung và ảnh."
        401 -> "Phiên backend không hợp lệ. Hãy đăng nhập lại."
        403 -> "Bạn không có quyền thực hiện thao tác này."
        404 -> "Nội dung không còn khả dụng hoặc quyền khám phá đã bị thu hồi. Hãy làm mới."
        409 -> "Thao tác xung đột với trạng thái hiện tại. Nếu đang đăng bài có thể khám phá, hãy kiểm tra cài đặt Campus."
        413 -> "Dữ liệu gửi quá lớn. Hãy chọn ảnh nhỏ hơn."
        429 -> {
            val seconds = retryAfterSeconds(error.response()?.headers()?.get("Retry-After"))
            if (seconds != null) "Bạn thao tác quá nhanh. Thử lại sau $seconds giây."
            else "Bạn thao tác quá nhanh. Hãy thử lại sau."
        }
        else -> "Backend trả lỗi ${error.code()}. Vui lòng thử lại."
    }
    else -> "Không kết nối được Campus Feed. Kiểm tra backend và FWP_BACKEND_BASE_URL."
}

internal fun retryAfterSeconds(value: String?, nowMillis: Long = System.currentTimeMillis()): Long? {
    if (value.isNullOrBlank()) return null
    value.trim().toLongOrNull()?.let { return it.coerceAtLeast(0) }
    return runCatching {
        val end = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        ((end - nowMillis + 999) / 1000).coerceAtLeast(0)
    }.getOrNull()
}
