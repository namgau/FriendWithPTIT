package vn.ptit.fwp.core.ai

import java.text.Normalizer
import java.util.Locale

object SafetyEngine {

    // Danh sách từ khóa độc hại đơn lẻ (phải khớp nguyên từ)
    val SINGLE_TOXIC_TERMS: Set<String> = setOf(
        "ngu", "đần", "đm", "dm", "đmm", "vl", "vcl", "cl", "dcm", "đcm",
        "cút", "đĩ", "phò",
        "stupid", "idiot", "fuck", "bitch", "loser", "asshole", "bastard"
    )

    // Danh sách cụm từ xúc phạm / nhạy cảm nhiều từ
    val PHRASE_TOXIC_TERMS: Set<String> = setOf(
        "óc chó", "câm mồm", "câm đi", "mất dạy", "khốn nạn", "đồ chó",
        "con điên", "thằng điên", "chó chết", "mẹ mày", "bố mày",
        "chết tiệt", "shut up"
    )

    /**
     * Kiểm tra nội dung có chứa từ ngữ xúc phạm hoặc độc hại khi offline / mất mạng.
     * Sử dụng chuẩn NFC, chuyển chữ thường và so khớp theo ranh giới từ để tránh báo nhầm.
     */
    fun isToxicOffline(text: String): Boolean {
        if (text.isBlank()) return false
        val normalized = normalize(text)
        if (normalized.isEmpty()) return false

        // Kiểm tra cụm từ trước
        for (phrase in PHRASE_TOXIC_TERMS) {
            val pattern = Regex("(?:^|\\s)${Regex.escape(phrase)}(?:\\s|$)")
            if (pattern.containsMatchIn(normalized)) {
                return true
            }
        }

        // Kiểm tra từ đơn
        val words = normalized.split(Regex("\\s+"))
        for (word in words) {
            if (SINGLE_TOXIC_TERMS.contains(word)) {
                return true
            }
        }

        return false
    }

    private fun normalize(text: String): String {
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)
        // Thay các ký tự không phải chữ/số thành khoảng trắng
        val cleaned = nfc.replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
        return cleaned.trim()
    }
}
