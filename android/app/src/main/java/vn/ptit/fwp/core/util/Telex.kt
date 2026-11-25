package vn.ptit.fwp.core.util

/**
 * Bộ gõ Telex tối giản cho ô nhập khi dùng bàn phím vật lý (emulator / bàn phím rời): emulator
 * đánh rơi ký tự có dấu do Unikey gửi, nên gõ chữ thường và để app tự ghép dấu.
 * Chỉ xử lý trường hợp gõ thêm đúng một ký tự vào cuối (luồng gõ bình thường).
 */
object Telex {
    private const val TONES = "sfrxj"
    private val BASES = "aăâeêioôơuưy"
    // Mỗi nguyên âm gốc → [không dấu, sắc, huyền, hỏi, ngã, nặng]
    private val TABLE = mapOf(
        'a' to "aáàảãạ", 'ă' to "ăắằẳẵặ", 'â' to "âấầẩẫậ", 'e' to "eéèẻẽẹ", 'ê' to "êếềểễệ",
        'i' to "iíìỉĩị", 'o' to "oóòỏõọ", 'ô' to "ôốồổỗộ", 'ơ' to "ơớờởỡợ", 'u' to "uúùủũụ",
        'ư' to "ưứừửữự", 'y' to "yýỳỷỹỵ"
    )
    private val TO_BASE: Map<Char, Pair<Char, Int>> = TABLE.flatMap { (base, forms) ->
        forms.mapIndexed { tone, ch -> ch to (base to tone) } + forms.uppercase().mapIndexed { tone, ch -> ch to (base.uppercaseChar() to tone) }
    }.toMap()

    /** Văn bản mới sau khi áp Telex ; trả nguyên `new` nếu không phải gõ thêm 1 ký tự ở cuối. */
    fun apply(old: String, new: String): String {
        if (new.length != old.length + 1 || !new.startsWith(old)) return new
        val key = new.last()
        val start = old.indexOfLast { !it.isLetter() } + 1
        val word = old.substring(start)
        if (word.isEmpty()) return new
        val out = transform(word, key) ?: return new
        return old.substring(0, start) + out
    }

    private fun base(c: Char) = TO_BASE[c]?.first ?: c
    private fun tone(c: Char) = TO_BASE[c]?.second ?: 0
    private fun withTone(c: Char, t: Int): Char {
        val lower = c.lowercaseChar()
        val forms = TABLE[base(lower).lowercaseChar()] ?: return c
        val r = forms[t]
        return if (c.isUpperCase()) r.uppercaseChar() else r
    }

    private fun transform(word: String, key: Char): String? {
        val k = key.lowercaseChar()
        val last = word.last()
        val lastBase = base(last).lowercaseChar()
        // dd → đ
        if (k == 'd' && last.lowercaseChar() == 'd' && word.length == 1) return if (last.isUpperCase()) "Đ" else "đ"
        // aa → â, ee → ê, oo → ô (giữ dấu thanh)
        if (k in "aeo" && lastBase == k) {
            val target = when (k) { 'a' -> 'â'; 'e' -> 'ê'; else -> 'ô' }
            return word.dropLast(1) + withTone(if (last.isUpperCase()) target.uppercaseChar() else target, tone(last))
        }
        // w: a → ă, o → ơ, u → ư, uo → ươ
        if (k == 'w') {
            val i = word.indexOfLast { base(it).lowercaseChar() in "aou" }
            if (i < 0) return null
            val chars = word.toCharArray()
            fun hook(j: Int) {
                val c = chars[j]
                val b = when (base(c).lowercaseChar()) { 'a' -> 'ă'; 'o' -> 'ơ'; 'u' -> 'ư'; else -> return }
                chars[j] = withTone(if (c.isUpperCase()) b.uppercaseChar() else b, tone(c))
            }
            hook(i)
            if (i > 0 && base(chars[i]).lowercaseChar() == 'ơ' && base(chars[i - 1]).lowercaseChar() == 'u') hook(i - 1)
            return String(chars)
        }
        // dấu thanh s f r x j, z = xóa dấu
        if (k in TONES || k == 'z') {
            val vowels = word.indices.filter { base(word[it]).lowercaseChar() in BASES }
            if (vowels.isEmpty()) return null
            val t = if (k == 'z') 0 else TONES.indexOf(k) + 1
            val pos = tonePosition(word, vowels)
            if (t != 0 && tone(word[pos]) == t) return word.map { withTone(it, 0) }.joinToString("") + key // gõ lại = bỏ dấu
            return word.mapIndexed { i, c -> if (i in vowels) withTone(c, if (i == pos) t else 0) else c }.joinToString("")
        }
        return null
    }

    /** Vị trí đặt dấu: ưu tiên nguyên âm có mũ/móc, rồi theo quy tắc kiểu cũ đơn giản. */
    private fun tonePosition(word: String, vowels: List<Int>): Int {
        vowels.lastOrNull { base(word[it]).lowercaseChar() in "ăâêôơư" }?.let { return it }
        // gi / qu: âm đầu, không tính là nguyên âm chính
        val v = vowels.filterNot { i -> i == 1 && word[0].lowercaseChar() in "gq" && base(word[i]).lowercaseChar() in "iu" && vowels.size > 1 }
        if (v.size == 1) return v[0]
        val endsWithVowel = v.last() == word.lastIndex
        val pair = v.takeLast(2).joinToString("") { base(word[it]).lowercaseChar().toString() }
        return when {
            v.size >= 3 -> v[1]
            !endsWithVowel -> v.last()
            pair in setOf("oa", "oe", "uy") -> v.last()
            else -> v.first()
        }
    }
}

/**
 * Bộ lọc cho onValueChange: bật Telex khi máy có bàn phím vật lý (emulator, bàn phím rời) ;
 * điện thoại thường dùng bộ gõ của hệ thống nên giữ nguyên.
 */
@androidx.compose.runtime.Composable
fun rememberTelexFilter(): (String, String) -> String {
    val hardware = androidx.compose.ui.platform.LocalConfiguration.current.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY
    return androidx.compose.runtime.remember(hardware) { if (hardware) Telex::apply else { _, new -> new } }
}

/**
 * Khi đang gõ Telex bằng bàn phím vật lý: khai báo ô kiểu Password (không có chữ đang soạn/gợi ý)
 * để Gboard gửi từng ký tự, không ghi đè chữ app vừa ghép dấu. Không đổi gì trên điện thoại thường.
 */
@androidx.compose.runtime.Composable
fun telexKeyboardOptions(base: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default): androidx.compose.foundation.text.KeyboardOptions {
    val hardware = androidx.compose.ui.platform.LocalConfiguration.current.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY
    return if (hardware) base.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password) else base
}

/**
 * onValueChange có Telex, nhớ chữ vừa ghép để phím gõ nhanh liên tiếp (trước khi màn hình kịp
 * cập nhật) vẫn so với đúng chữ hiện tại.
 */
@androidx.compose.runtime.Composable
fun telexOnValueChange(value: String, enabled: Boolean = true, onValueChange: (String) -> Unit): (String) -> Unit {
    val filter = rememberTelexFilter()
    val last = androidx.compose.runtime.remember { arrayOf(value) }
    last[0] = value
    return { proposed ->
        val out = if (enabled) filter(last[0], proposed) else proposed
        last[0] = out
        onValueChange(out)
    }
}
