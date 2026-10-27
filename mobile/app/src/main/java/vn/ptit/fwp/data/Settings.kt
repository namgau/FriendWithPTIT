package vn.ptit.fwp.data

import android.content.Context
import androidx.core.content.edit

/** Cài đặt người dùng lưu bằng SharedPreferences (giáo trình mục 2.4.1). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("fwp_settings", Context.MODE_PRIVATE)

    /** true = gọi server FastAPI ; false = dữ liệu giả trong máy. */
    var useServer: Boolean
        get() = prefs.getBoolean(KEY_USE_SERVER, false)
        set(value) = prefs.edit { putBoolean(KEY_USE_SERVER, value) }

    /** Gợi ý cứu hội thoại (Module 3 Head A) — người dùng tắt được. */
    var aiSuggestions: Boolean
        get() = prefs.getBoolean(KEY_AI_SUGGESTIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_AI_SUGGESTIONS, value) }

    /** Nhắc nhẹ trước khi gửi tin có thể gây khó chịu (Module 3 Head B). */
    var safetyNudge: Boolean
        get() = prefs.getBoolean(KEY_SAFETY_NUDGE, true)
        set(value) = prefs.edit { putBoolean(KEY_SAFETY_NUDGE, value) }

    private companion object {
        const val KEY_USE_SERVER = "use_server"
        const val KEY_AI_SUGGESTIONS = "ai_suggestions"
        const val KEY_SAFETY_NUDGE = "safety_nudge"
    }
}
