package vn.ptit.fwp.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ptit.fwp.core.model.ChatMessage
import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.Gender
import vn.ptit.fwp.core.model.GpaTier
import vn.ptit.fwp.core.model.HealthLevel
import vn.ptit.fwp.core.model.Profile

class HealthEngineTest {

    private fun dummyProfile(id: String) = Profile(
        id = id,
        nickname = id,
        fullName = "Test User $id",
        age = 20,
        gender = Gender.OTHER,
        school = "PTIT",
        campus = "Hà Nội",
        faculty = "CNTT",
        major = "CNTT",
        academicYear = "D21",
        alumni = false,
        gpa = 3.5,
        gpaTier = GpaTier.EXCELLENT,
        bio = "",
        interests = listOf("Công nghệ", "Âm nhạc"),
        avatarSeed = 1,
        distanceKm = 1.0,
        verified = true,
        isDemo = false,
        showGpa = true,
        showLocation = true,
        mode = ConnectionMode.FRIENDS,
        blocked = false
    )

    @Test
    fun testHeadAThresholdConstants() {
        assertEquals(30, HealthEngine.TAU_1)
        assertEquals(60, HealthEngine.TAU_2)
    }

    @Test
    fun testClassifyLevelThresholds() {
        // HEALTHY: score < 30
        assertEquals(HealthLevel.HEALTHY, HealthEngine.classifyLevel(0))
        assertEquals(HealthLevel.HEALTHY, HealthEngine.classifyLevel(15))
        assertEquals(HealthLevel.HEALTHY, HealthEngine.classifyLevel(29))

        // FADING: 30 <= score < 60
        assertEquals(HealthLevel.FADING, HealthEngine.classifyLevel(30))
        assertEquals(HealthLevel.FADING, HealthEngine.classifyLevel(45))
        assertEquals(HealthLevel.FADING, HealthEngine.classifyLevel(59))

        // AT_RISK: score >= 60
        assertEquals(HealthLevel.AT_RISK, HealthEngine.classifyLevel(60))
        assertEquals(HealthLevel.AT_RISK, HealthEngine.classifyLevel(75))
        assertEquals(HealthLevel.AT_RISK, HealthEngine.classifyLevel(100))
    }

    @Test
    fun testAnalyzeInsufficientMessages() {
        val me = dummyProfile("me")
        val other = dummyProfile("partner")
        val messages = listOf(
            ChatMessage("1", "m", "me", "Chào cậu", createdAt = 1000L),
            ChatMessage("2", "m", "partner", "Chào bạn", createdAt = 2000L)
        )
        val report = HealthEngine.analyze(messages, me, other, now = 3000L)
        assertEquals(HealthLevel.FADING, report.level)
        assertEquals(38, report.score)
        assertEquals(2, report.messageCount)
    }

    @Test
    fun testAnalyzeHealthyConversation() {
        val me = dummyProfile("me")
        val other = dummyProfile("partner")
        val now = 100_000_000L
        val messages = listOf(
            ChatMessage("1", "m", "me", "Hôm nay cậu học môn gì thế?", createdAt = now - 50_000L),
            ChatMessage("2", "m", "partner", "Mình vừa học xong giải thuật, khá là thú vị!", createdAt = now - 40_000L),
            ChatMessage("3", "m", "me", "Cậu có muốn chiều nay học nhóm chung ở thư viện không?", createdAt = now - 30_000L),
            ChatMessage("4", "m", "partner", "Ý kiến hay đó, mấy giờ cậu qua thư viện được?", createdAt = now - 20_000L),
            ChatMessage("5", "m", "me", "Tầm 2h chiều nhé, mình giữ chỗ trước.", createdAt = now - 10_000L),
            ChatMessage("6", "m", "partner", "Okie chốt nhé, hẹn gặp cậu lúc 2h!", createdAt = now - 5_000L)
        )
        val report = HealthEngine.analyze(messages, me, other, now = now)
        assertEquals(HealthLevel.HEALTHY, report.level)
        assertTrue("Score should be < TAU_1 (30)", report.score < HealthEngine.TAU_1)
    }

    @Test
    fun testAnalyzeAtRiskConversation() {
        val me = dummyProfile("me")
        val other = dummyProfile("partner")
        val now = 100_000_000L
        // Gap > 48h (48 * 3600 * 1000 = 172_800_000)
        val lastTime = now - 200_000_000L
        val messages = listOf(
            ChatMessage("1", "m", "me", "ừ", createdAt = lastTime - 50_000L),
            ChatMessage("2", "m", "me", "ok", createdAt = lastTime - 40_000L),
            ChatMessage("3", "m", "me", "chán", createdAt = lastTime - 30_000L),
            ChatMessage("4", "m", "partner", "mệt", createdAt = lastTime)
        )
        val report = HealthEngine.analyze(messages, me, other, now = now)
        assertEquals(HealthLevel.AT_RISK, report.level)
        assertTrue("Score should be >= TAU_2 (60)", report.score >= HealthEngine.TAU_2)
    }
}
