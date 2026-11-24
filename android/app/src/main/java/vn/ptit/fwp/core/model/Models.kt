package vn.ptit.fwp.core.model

enum class Gender(val label: String) {
    FEMALE("Nữ"),
    MALE("Nam"),
    OTHER("Khác"),
    ANY("Mọi giới")
}

enum class ConnectionMode(val label: String, val headline: String, val description: String) {
    DATING("Hẹn hò", "Tìm người đồng điệu", "Một kết nối nhẹ nhàng, chân thành và đủ thú vị để bắt đầu."),
    FRIENDS("Kết bạn", "Mở rộng vòng tròn", "Tìm người cùng gu, cùng câu lạc bộ hoặc cùng những buổi cà phê."),
    STUDY("Bạn học", "Cùng tiến bộ", "Học nhóm, chia sẻ tài liệu và cùng nhau chinh phục học kỳ.")
}

enum class GpaTier(val label: String, val range: String, val min: Double, val max: Double) {
    EXCELLENT("Giỏi", "3.60 – 4.00", 3.60, 4.00),
    GOOD("Khá", "3.20 – 3.59", 3.20, 3.599),
    FAIR("Trung bình khá", "2.50 – 3.19", 2.50, 3.199),
    AVERAGE("Trung bình", "0.00 – 2.49", 0.00, 2.499);

    companion object {
        fun fromGpa(gpa: Double): GpaTier = when {
            gpa >= EXCELLENT.min -> EXCELLENT
            gpa >= GOOD.min -> GOOD
            gpa >= FAIR.min -> FAIR
            else -> AVERAGE
        }
    }
}

data class University(
    val name: String,
    val shortName: String,
    val emailDomains: List<String>,
    val campuses: List<String>,
    val isPtit: Boolean = false
)

object UniversityCatalog {
    val universities = listOf(
        University(
            name = "Học viện Công nghệ Bưu chính Viễn thông",
            shortName = "PTIT",
            emailDomains = listOf("ptit.edu.vn", "sv.ptit.edu.vn", "stu.ptit.edu.vn"),
            campuses = listOf("Hà Nội – Hà Đông", "TP. Hồ Chí Minh", "Đà Nẵng"),
            isPtit = true
        ),
        University("Đại học Bách khoa Hà Nội", "BKHN", listOf("hust.edu.vn", "sis.hust.edu.vn"), listOf("Hai Bà Trưng", "Hà Nội")),
        University("Đại học Quốc gia Hà Nội", "VNU", listOf("vnu.edu.vn", "st.vnu.edu.vn"), listOf("Xuân Thủy", "Hòa Lạc")),
        University("Trường Đại học Kinh tế Quốc dân", "NEU", listOf("neu.edu.vn", "st.neu.edu.vn"), listOf("Hai Bà Trưng")),
        University("Trường Đại học Ngoại thương", "FTU", listOf("ftu.edu.vn", "st.ftu.edu.vn"), listOf("Chùa Láng", "Quảng Ninh")),
        University("Trường Đại học FPT", "FPT", listOf("fpt.edu.vn", "st.fpt.edu.vn"), listOf("Hòa Lạc", "TP. Hồ Chí Minh", "Đà Nẵng")),
        University("Trường Đại học Công nghệ – ĐHQGHN", "UET", listOf("uet.vnu.edu.vn", "vnu.edu.vn"), listOf("Xuân Thủy", "Hòa Lạc")),
        University("Trường Đại học RMIT Việt Nam", "RMIT", listOf("rmit.edu.vn", "student.rmit.edu.vn"), listOf("Hà Nội", "TP. Hồ Chí Minh")),
        University("Trường Đại học Sư phạm Hà Nội", "HNUE", listOf("hnue.edu.vn", "student.hnue.edu.vn"), listOf("Cầu Giấy")),
        University("Trường Đại học Y Hà Nội", "HMU", listOf("hmu.edu.vn", "sv.hmu.edu.vn"), listOf("Đống Đa"))
    )

    val ptit: University = universities.first()

    fun search(query: String): List<University> {
        if (query.isBlank()) return universities
        val q = query.trim().lowercase()
        return universities.filter {
            it.name.lowercase().contains(q) || it.shortName.lowercase().contains(q)
        }
    }

    fun isVerifiedEmail(email: String): Boolean {
        val domain = email.substringAfter("@", "").lowercase()
        return universities.any { uni -> uni.emailDomains.any { it.equals(domain, ignoreCase = true) } }
    }

    fun universityForEmail(email: String): University? {
        val domain = email.substringAfter("@", "").lowercase()
        return universities.firstOrNull { uni -> uni.emailDomains.any { it.equals(domain, ignoreCase = true) } }
    }
}

val INTEREST_CATALOG = listOf(
    "Cà phê", "Lập trình", "Âm nhạc", "Indie", "Mèo", "Chó", "Xem phim",
    "Sách", "Chạy bộ", "Bóng rổ", "Bóng đá", "Game", "Board game", "Du lịch",
    "Chụp ảnh", "Nhiếp ảnh", "Thiết kế", "Vẽ", "Yoga", "Nấu ăn", "K-pop",
    "Nhảy", "Trà sữa", "Khởi nghiệp", "Công nghệ", "AI", "Bảo mật", "CTF",
    "Guitar", "Học nhóm", "CLB", "Tình nguyện"
)

data class Profile(
    val id: String,
    val nickname: String,
    val fullName: String,
    val age: Int,
    val gender: Gender,
    val school: String,
    val campus: String,
    val faculty: String,
    val major: String,
    val academicYear: String,
    val alumni: Boolean,
    val gpa: Double,
    val gpaTier: GpaTier,
    val bio: String,
    val interests: List<String>,
    val avatarSeed: Int,
    val distanceKm: Double,
    val verified: Boolean,
    val isDemo: Boolean,
    val showGpa: Boolean,
    val showLocation: Boolean,
    val mode: ConnectionMode,
    val likedYou: Boolean = false,
    val blocked: Boolean = false,
    val photoUrl: String? = null,
    /** Tất cả ảnh (URL tuyệt đối) ; photoUrl là ảnh bìa = photos.first(). */
    val photos: List<String> = listOfNotNull(photoUrl),
    /** % hợp gu từ câu hỏi ; null khi chưa đủ 3 câu chung. */
    val questionMatch: Int? = null,
    val commonQuestions: Int = 0,
    /** Ảnh bìa (banner), URL tuyệt đối. */
    val coverPhoto: String? = null,
    /** Câu hỏi hợp gu người này đã trả lời: (câu hỏi, câu trả lời). */
    val answers: List<Pair<String, String>> = emptyList(),
    /** "Thêm về tôi": khóa trong ABOUT_FIELDS (+ heightCm) → giá trị. */
    val about: Map<String, String> = emptyMap(),
    val languages: List<String> = emptyList()
) {
    val displayGpa: String
        get() = if (showGpa) String.format(java.util.Locale.US, "%.2f", gpa) else "Tầng ${gpaTier.label}"
}

enum class GraduationFilter { ALL, STUDYING, ALUMNI }

data class DiscoveryFilters(
    val modes: Set<ConnectionMode> = ConnectionMode.entries.toSet(),
    val ageRange: IntRange = 18..30,
    val gpaTiers: Set<GpaTier> = GpaTier.entries.toSet(),
    val sameSchoolOnly: Boolean = false,
    val maxDistanceKm: Int = 50,
    val onlyVerified: Boolean = true,
    val useApproximateLocation: Boolean = true,
    val graduation: GraduationFilter = GraduationFilter.ALL
) {
    val activeCount: Int
        get() = listOf(
            modes.size < ConnectionMode.entries.size,
            ageRange != 18..30,
            gpaTiers.size < GpaTier.entries.size,
            sameSchoolOnly,
            maxDistanceKm < 50,
            !onlyVerified,
            graduation != GraduationFilter.ALL
        ).count { it }
}

enum class SwipeAction { LIKE, PASS, SUPER_LIKE }

data class Swipe(
    val targetId: String,
    val action: SwipeAction,
    val createdAt: Long
)

data class RankedProfile(
    val profile: Profile,
    val score: Int,
    val reasons: List<String>,
    val source: String = "Quy tắc FwP (fallback)"
)

data class SwipeOutcome(
    val matched: Boolean,
    val matchId: String? = null,
    val profile: Profile,
    val action: SwipeAction
)

data class FwpMatch(
    val id: String,
    val profileId: String,
    val mode: ConnectionMode,
    val matchedAt: Long,
    val lastMessage: String? = null,
    val lastMessageAt: Long? = null,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val groupTitle: String? = null,
    val compatibility: Int = 0,
    val revealOptInMe: Boolean = false,
    val revealOptInThem: Boolean = false,
    val blocked: Boolean = false
)

enum class MessageType { TEXT, IMAGE, SYSTEM }

data class ChatMessage(
    val id: String,
    val matchId: String,
    val senderId: String,
    val text: String,
    val type: MessageType = MessageType.TEXT,
    val createdAt: Long,
    val isRead: Boolean = false
) {
    val isMine: Boolean get() = senderId == "me"
}

enum class NotificationType { MATCH, MESSAGE, SAFETY, SYSTEM, ACHIEVEMENT }

data class FwpNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val createdAt: Long,
    val read: Boolean = false
)

enum class HealthLevel(val label: String, val shortLabel: String) {
    HEALTHY("Đang khỏe", "Khỏe"),
    FADING("Hơi nhạt dần", "Hơi nhạt"),
    AT_RISK("Nguy cơ toang", "Nguy cơ")
}

data class HealthReport(
    val level: HealthLevel,
    val score: Int,
    val reasons: List<String>,
    val suggestions: List<String>,
    val messageCount: Int,
    val source: String = "Quy tắc FwP (fallback)"
)

data class CompatibilityReport(
    val score: Int,
    val blurLevel: Float,
    val personality: Int,
    val emotion: Int,
    val interest: Int,
    val messageCount: Int,
    val revealEligible: Boolean,
    val revealed: Boolean,
    val source: String = "Quy tắc FwP (fallback)"
)

data class MyProfileDraft(
    val fullName: String = "",
    val nickname: String = "",
    val age: Int = 20,
    val gender: Gender = Gender.FEMALE,
    val school: String = UniversityCatalog.ptit.name,
    val campus: String = UniversityCatalog.ptit.campuses.first(),
    val faculty: String = "",
    val major: String = "",
    val academicYear: String = "2024",
    val alumni: Boolean = false,
    val gpa: Double = 3.2,
    val bio: String = "",
    val interests: Set<String> = emptySet(),
    val avatarSeed: Int = 1,
    val showGpa: Boolean = true,
    val showLocation: Boolean = true,
    val mode: ConnectionMode = ConnectionMode.DATING,
    val email: String = "",
    val photoUrl: String? = null,
    /** Đường dẫn ảnh thô: `content://` mới chọn, hoặc đường dẫn/URL backend đã có. */
    val photos: List<String> = listOfNotNull(photoUrl),
    /** Ảnh bìa thô (`content://` mới chọn hoặc đường dẫn backend) ; null = không có. */
    val coverPhoto: String? = null,
    val about: Map<String, String> = emptyMap(),
    val languages: List<String> = emptyList()
)

/** Một mục "Thêm về tôi" chọn từ danh sách (khóa khớp backend ABOUT_KEYS). */
data class AboutField(val key: String, val label: String, val options: List<String>)

val ABOUT_FIELDS = listOf(
    AboutField("smoking", "Hút thuốc", listOf("Không hút thuốc", "Thỉnh thoảng", "Có hút")),
    AboutField("drinking", "Bia rượu", listOf("Không uống", "Uống xã giao", "Thỉnh thoảng", "Thường xuyên")),
    AboutField("pets", "Thú cưng", listOf("Nuôi mèo", "Nuôi chó", "Chưa nuôi", "Muốn nuôi", "Dị ứng lông")),
    AboutField("religion", "Tôn giáo", listOf("Không theo tôn giáo", "Phật giáo", "Công giáo", "Tin lành", "Khác")),
    AboutField("exercise", "Tập thể dục", listOf("Tập thường xuyên", "Thỉnh thoảng", "Hầu như không")),
    AboutField("zodiac", "Cung hoàng đạo", listOf("Bạch Dương", "Kim Ngưu", "Song Tử", "Cự Giải", "Sư Tử", "Xử Nữ",
        "Thiên Bình", "Bọ Cạp", "Nhân Mã", "Ma Kết", "Bảo Bình", "Song Ngư"))
)

val LANGUAGE_CATALOG = listOf("Tiếng Việt", "Tiếng Anh", "Tiếng Nhật", "Tiếng Hàn", "Tiếng Trung", "Tiếng Pháp", "Tiếng Đức")

/** Khóa = năm nhập học ; PTIT mới có tới khóa 2026. */
const val LATEST_PTIT_COHORT = 2026
fun cohortError(academicYear: String): String? {
    val year = academicYear.trim().toIntOrNull()
        ?: return "Khóa ghi theo năm nhập học, ví dụ 2024."
    return when {
        year < 2000 -> "Khóa ghi theo năm nhập học, ví dụ 2024."
        year > LATEST_PTIT_COHORT -> "PTIT mới có tới khóa $LATEST_PTIT_COHORT."
        else -> null
    }
}

/** "22 - khóa 2024" ; đọc được cả dữ liệu cũ dạng D24. */
fun ageCohortLabel(age: Int, academicYear: String): String {
    val raw = academicYear.trim()
    val year = Regex("^[DdKk]([0-9]{2})$").find(raw)?.groupValues?.get(1)?.let { "20$it" } ?: raw
    return if (year.isBlank()) "$age" else "$age - khóa $year"
}

/** Số ảnh tối thiểu / tối đa của một hồ sơ (khớp backend). */
const val MIN_PROFILE_PHOTOS = 3
const val MAX_PROFILE_PHOTOS = 6
