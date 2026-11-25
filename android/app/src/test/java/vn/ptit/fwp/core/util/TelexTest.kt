package vn.ptit.fwp.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TelexTest {
    /** Gõ lần lượt từng ký tự qua Telex.apply như ô nhập làm. */
    private fun type(keys: String) = keys.fold("") { acc, c -> Telex.apply(acc, acc + c) }

    @Test fun basicWords() {
        assertEquals("bạn", type("banj"))
        assertEquals("đông", type("ddoong"))
        assertEquals("Việt Nam", type("Vieejt Nam"))
        assertEquals("người", type("nguwowif"))
        assertEquals("hoà", type("hoaf"))
        assertEquals("tiếng", type("tieesng"))
        assertEquals("quá", type("quas"))
        assertEquals("giờ", type("giowf"))
    }

    @Test fun retypingToneLetterEscapes() {
        assertEquals("bas", type("bass")) // như Unikey: gõ s lần hai bỏ dấu sắc
    }

    @Test fun nonAppendEditsPassThrough() {
        assertEquals("ban", Telex.apply("bạn", "ban"))
    }
}
