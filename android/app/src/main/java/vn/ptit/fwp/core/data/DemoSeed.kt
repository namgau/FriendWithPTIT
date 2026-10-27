package vn.ptit.fwp.core.data

import vn.ptit.fwp.core.data.local.MatchEntity
import vn.ptit.fwp.core.data.local.MessageEntity
import vn.ptit.fwp.core.data.local.NotificationEntity
import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.Gender
import vn.ptit.fwp.core.model.GpaTier
import vn.ptit.fwp.core.model.MessageType
import vn.ptit.fwp.core.model.NotificationType
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.UniversityCatalog

object DemoSeed {

    val profiles: List<Profile> = listOf(
        demo(
            id = "p01", nickname = "Mèo Lười", fullName = "Nguyễn Minh Anh", age = 20, gender = Gender.FEMALE,
            faculty = "Công nghệ Thông tin", major = "Kỹ thuật Phần mềm", year = "K66", gpa = 3.72,
            bio = "Mình mê code, cà phê và những buổi chiều nhiều nắng. Đang học cách làm một app tử tế và nuôi thêm một chú mèo nữa.",
            interests = listOf("Cà phê", "Lập trình", "Indie", "Mèo", "Xem phim"),
            seed = 1, distance = 1.2, mode = ConnectionMode.DATING, likedYou = true
        ),
        demo(
            id = "p02", nickname = "Huy Bóng Rổ", fullName = "Trần Gia Huy", age = 21, gender = Gender.MALE,
            faculty = "An toàn Thông tin", major = "An toàn Thông tin", year = "K65", gpa = 3.41,
            bio = "Chơi bóng rổ mỗi tối, thích tìm hiểu bảo mật và đi cà phê với bạn bè. Người mới quen sẽ thấy mình ít nói, quen rồi thì rất nhiều chuyện.",
            interests = listOf("Bóng rổ", "Cà phê", "Bảo mật", "Du lịch"),
            seed = 2, distance = 2.4, mode = ConnectionMode.FRIENDS
        ),
        demo(
            id = "p03", nickname = "Nắng Sớm", fullName = "Lê Thảo Linh", age = 19, gender = Gender.FEMALE,
            faculty = "Đa phương tiện", major = "Thiết kế Đồ họa", year = "K67", gpa = 3.18,
            bio = "Chụp ảnh, nghe nhạc và sưu tầm board game. Mình tin một cuộc trò chuyện hay bắt đầu từ một câu hỏi thật lòng.",
            interests = listOf("Chụp ảnh", "Âm nhạc", "Mèo", "Board game"),
            seed = 3, distance = 3.1, mode = ConnectionMode.DATING, likedYou = true
        ),
        demo(
            id = "p04", nickname = "Long Guitar", fullName = "Phạm Đức Long", age = 23, gender = Gender.MALE,
            faculty = "Viễn thông", major = "Kỹ thuật Viễn thông", year = "K63", gpa = 3.55,
            bio = "Đã tốt nghiệp, đang làm về hạ tầng mạng. Cuối tuần thường chạy bộ ở Hồ Tây rồi ôm guitar đến tối.",
            interests = listOf("Chạy bộ", "Guitar", "Cà phê", "Khởi nghiệp"),
            seed = 4, distance = 4.8, mode = ConnectionMode.STUDY, alumni = true
        ),
        demo(
            id = "p05", nickname = "Mai Sách", fullName = "Hoàng Ngọc Mai", age = 20, gender = Gender.FEMALE,
            faculty = "Kinh tế", major = "Marketing", year = "K66", gpa = 3.66,
            bio = "Đọc sách, tập yoga và hay ghi chú mọi thứ. Mình thích những người có gu riêng nhưng vẫn biết lắng nghe.",
            interests = listOf("Sách", "Cà phê", "Yoga", "Nhiếp ảnh"),
            seed = 5, distance = 0.8, mode = ConnectionMode.DATING
        ),
        demo(
            id = "p06", nickname = "Nam Game", fullName = "Đỗ Hoàng Nam", age = 22, gender = Gender.MALE,
            faculty = "Công nghệ Thông tin", major = "Hệ thống Thông tin", year = "K64", gpa = 2.98,
            bio = "Game thủ cuối tuần, fan bóng đá và phim khoa học viễn tưởng. Đang tìm người cùng leo rank hoặc cùng xem phim.",
            interests = listOf("Game", "Bóng đá", "Xem phim", "Công nghệ"),
            seed = 6, distance = 8.6, mode = ConnectionMode.FRIENDS
        ),
        demo(
            id = "p07", nickname = "Vy Vẽ", fullName = "Nguyễn Khánh Vy", age = 21, gender = Gender.FEMALE,
            faculty = "Đa phương tiện", major = "Truyền thông Đa phương tiện", year = "K65", gpa = 3.83,
            bio = "Vẽ linh tinh, nghe indie và đi triển lãm. Mình thích những buổi hẹn không cần kịch bản, chỉ cần thấy vui.",
            interests = listOf("Thiết kế", "Vẽ", "Indie", "Mèo"),
            seed = 7, distance = 1.9, mode = ConnectionMode.DATING, likedYou = true
        ),
        demo(
            id = "p08", nickname = "Kiệt CTF", fullName = "Bùi Tuấn Kiệt", age = 20, gender = Gender.MALE,
            faculty = "An toàn Thông tin", major = "An toàn Thông tin", year = "K66", gpa = 3.29,
            bio = "Mê CTF, thích đọc write-up và uống cà phê sữa đá. Có thể ngồi im cả buổi nếu đang debug.",
            interests = listOf("CTF", "Lập trình", "Cà phê", "Board game"),
            seed = 8, distance = 2.2, mode = ConnectionMode.STUDY
        ),
        demo(
            id = "p09", nickname = "Chi Lang Thang", fullName = "Phan Lan Chi", age = 22, gender = Gender.FEMALE,
            faculty = "Viễn thông", major = "Truyền thông và Mạng máy tính", year = "K64", gpa = 3.05,
            bio = "Thích đi đây đó, chụp ảnh và nấu ăn cho bạn bè. Mình tin mỗi người đều có một câu chuyện hay để kể.",
            interests = listOf("Du lịch", "Chụp ảnh", "Âm nhạc", "Nấu ăn"),
            seed = 9, distance = 5.4, mode = ConnectionMode.DATING
        ),
        demo(
            id = "p10", nickname = "Dũng Bóng Rổ", fullName = "Ngô Quang Dũng", age = 19, gender = Gender.MALE,
            faculty = "Kinh tế", major = "Thương mại Điện tử", year = "K67", gpa = 2.72,
            bio = "Bóng rổ, game và đồ ăn ngon. Học không giỏi nhất nhưng luôn cố gắng. Rất vui được làm quen.",
            interests = listOf("Bóng rổ", "Game", "Cà phê", "Xem phim"),
            seed = 10, distance = 12.3, mode = ConnectionMode.FRIENDS
        ),
        demo(
            id = "p11", nickname = "My Code", fullName = "Trần Hà My", age = 20, gender = Gender.FEMALE,
            faculty = "Công nghệ Thông tin", major = "Khoa học Máy tính", year = "K66", gpa = 3.62,
            bio = "Học AI, chạy bộ và đọc sách. Đang tìm một người bạn học nghiêm túc nhưng vẫn biết đùa.",
            interests = listOf("Lập trình", "Sách", "Cà phê", "Chạy bộ"),
            seed = 11, distance = 1.6, mode = ConnectionMode.STUDY, likedYou = true
        ),
        demo(
            id = "p12", nickname = "Trâm Màu", fullName = "Lý Bảo Trâm", age = 21, gender = Gender.FEMALE,
            faculty = "Đa phương tiện", major = "Thiết kế Đồ họa", year = "K65", gpa = 3.37,
            bio = "Làm visual, vẽ minh họa và nuôi mèo. Thích board game và những cuộc trò chuyện dài không mục đích.",
            interests = listOf("Vẽ", "Thiết kế", "Board game", "Mèo"),
            seed = 12, distance = 7.9, mode = ConnectionMode.DATING
        ),
        demo(
            id = "p13", nickname = "Anh Bảo Mật", fullName = "Vũ Việt Anh", age = 23, gender = Gender.MALE,
            faculty = "An toàn Thông tin", major = "An toàn Thông tin", year = "K63", gpa = 3.91,
            bio = "Alumni, làm bảo mật. Thích guitar và cà phê, sẵn sàng chia sẻ kinh nghiệm nghề cho các bạn sinh viên.",
            interests = listOf("Bảo mật", "CTF", "Guitar", "Cà phê"),
            seed = 13, distance = 3.6, mode = ConnectionMode.STUDY, alumni = true
        ),
        demo(
            id = "p14", nickname = "Trang Kpop", fullName = "Đỗ Thu Trang", age = 19, gender = Gender.FEMALE,
            faculty = "Kinh tế", major = "Kế toán", year = "K67", gpa = 2.45,
            bio = "K-pop, nhảy và trà sữa. Học chưa giỏi nhưng đang cố mỗi ngày. Tìm bạn cùng đi nhảy hoặc đi ăn.",
            interests = listOf("K-pop", "Nhảy", "Chụp ảnh", "Trà sữa"),
            seed = 14, distance = 0.9, mode = ConnectionMode.FRIENDS
        ),
        demo(
            id = "p15", nickname = "Anh Chạy Bộ", fullName = "Nguyễn Đức Anh", age = 22, gender = Gender.MALE,
            faculty = "Viễn thông", major = "Kỹ thuật Điện tử", year = "K64", gpa = 3.48,
            bio = "Chạy bộ mỗi sáng, thích bóng đá và du lịch bụi. Đang học cách chụp ảnh đẹp hơn.",
            interests = listOf("Chạy bộ", "Bóng đá", "Du lịch", "Nhiếp ảnh"),
            seed = 15, distance = 9.2, mode = ConnectionMode.DATING
        ),
        demo(
            id = "p16", nickname = "Thảo AI", fullName = "Lê Phương Thảo", age = 20, gender = Gender.FEMALE,
            faculty = "Công nghệ Thông tin", major = "Khoa học Máy tính", year = "K66", gpa = 3.78,
            bio = "Làm AI, đọc sách và uống cà phê. Thích thảo luận về mô hình học máy và những ý tưởng kỳ lạ.",
            interests = listOf("AI", "Lập trình", "Sách", "Cà phê"),
            seed = 16, distance = 2.1, mode = ConnectionMode.STUDY, likedYou = true
        ),
        demo(
            id = "p17", nickname = "Bảo Phim", fullName = "Trần Quốc Bảo", age = 21, gender = Gender.MALE,
            faculty = "Đa phương tiện", major = "Truyền thông Đa phương tiện", year = "K65", gpa = 2.89,
            bio = "Mê phim, nghe nhạc và làm thiết kế. Có một danh sách phim dài hơn cả học kỳ, cần người xem cùng.",
            interests = listOf("Xem phim", "Âm nhạc", "Thiết kế", "Board game"),
            seed = 17, distance = 3.5, mode = ConnectionMode.FRIENDS
        ),
        demo(
            id = "p18", nickname = "Linh Nấu Ăn", fullName = "Phạm Diệu Linh", age = 22, gender = Gender.FEMALE,
            faculty = "Kinh tế", major = "Quản trị Kinh doanh", year = "K64", gpa = 3.24,
            bio = "Yoga, nấu ăn và đi cà phê. Thích những buổi tối ấm áp với bạn bè và một nồi lẩu nhỏ.",
            interests = listOf("Yoga", "Nấu ăn", "Cà phê", "Du lịch"),
            seed = 18, distance = 6.3, mode = ConnectionMode.DATING
        ),
        demo(
            id = "p19", nickname = "Hiếu Code", fullName = "Lâm Trung Hiếu", age = 20, gender = Gender.MALE,
            faculty = "Công nghệ Thông tin", major = "Kỹ thuật Phần mềm", year = "K66", gpa = 3.12,
            bio = "Code, game và cà phê. Đang tập chạy bộ để bớt thức khuya. Rất thích làm quen với người mới.",
            interests = listOf("Lập trình", "Game", "Cà phê", "Bóng rổ"),
            seed = 19, distance = 1.8, mode = ConnectionMode.FRIENDS
        ),
        demo(
            id = "p20", nickname = "Ngọc Guitar", fullName = "Nguyễn Bích Ngọc", age = 21, gender = Gender.FEMALE,
            faculty = "Viễn thông", major = "Kỹ thuật Viễn thông", year = "K65", gpa = 3.57,
            bio = "Guitar, sách và chạy bộ. Mình thích những người kiên nhẫn và biết giữ lời hứa.",
            interests = listOf("Âm nhạc", "Guitar", "Sách", "Chạy bộ"),
            seed = 20, distance = 4.1, mode = ConnectionMode.STUDY
        )
    )

    private fun demo(
        id: String,
        nickname: String,
        fullName: String,
        age: Int,
        gender: Gender,
        faculty: String,
        major: String,
        year: String,
        gpa: Double,
        bio: String,
        interests: List<String>,
        seed: Int,
        distance: Double,
        mode: ConnectionMode,
        likedYou: Boolean = false,
        alumni: Boolean = false
    ) = Profile(
        id = id,
        nickname = nickname,
        fullName = fullName,
        age = age,
        gender = gender,
        school = UniversityCatalog.ptit.name,
        campus = if (id in setOf("p06", "p10", "p15")) UniversityCatalog.ptit.campuses[1] else UniversityCatalog.ptit.campuses[0],
        faculty = faculty,
        major = major,
        academicYear = year,
        alumni = alumni,
        gpa = gpa,
        gpaTier = GpaTier.fromGpa(gpa),
        bio = bio,
        interests = interests,
        avatarSeed = seed,
        distanceKm = distance,
        verified = true,
        isDemo = true,
        showGpa = true,
        showLocation = true,
        mode = mode,
        likedYou = likedYou
    )

    fun matches(now: Long): List<MatchEntity> = listOf(
        MatchEntity(
            id = "match-minhanh",
            profileId = "p01",
            mode = ConnectionMode.DATING.name,
            matchedAt = now - 2 * 86_400_000L,
            lastMessage = "Cậu cũng thích indie à? Có nghe Vũ. chưa?",
            lastMessageAt = now - 5 * 60_000L,
            unreadCount = 1,
            compatibility = 68,
            revealOptInMe = false,
            revealOptInThem = true
        ),
        MatchEntity(
            id = "match-thaolinh",
            profileId = "p03",
            mode = ConnectionMode.DATING.name,
            matchedAt = now - 6 * 86_400_000L,
            lastMessage = "Ừm, cũng được...",
            lastMessageAt = now - 26 * 3_600_000L,
            unreadCount = 0,
            compatibility = 42,
            revealOptInMe = false,
            revealOptInThem = false
        ),
        MatchEntity(
            id = "match-ngocmai",
            profileId = "p05",
            mode = ConnectionMode.STUDY.name,
            matchedAt = now - 9 * 86_400_000L,
            lastMessage = "Mai gửi tài liệu sau nhé",
            lastMessageAt = now - 3 * 86_400_000L,
            unreadCount = 2,
            compatibility = 31,
            revealOptInMe = false,
            revealOptInThem = false
        )
    )

    fun messages(now: Long): List<MessageEntity> {
        val list = mutableListOf<MessageEntity>()
        var index = 0
        fun msg(matchId: String, sender: String, text: String, minutesAgo: Long, type: MessageType = MessageType.TEXT, read: Boolean = true) {
            index += 1
            list += MessageEntity(
                id = "seed-msg-$index",
                matchId = matchId,
                senderId = sender,
                text = text,
                type = type.name,
                createdAt = now - minutesAgo * 60_000L,
                isRead = read
            )
        }
        msg("match-minhanh", "me", "Chào cậu, thấy cậu cũng thích indie. Cậu hay nghe ai?", 2 * 1440L)
        msg("match-minhanh", "p01", "Chào cậu! Mình nghe Vũ., Chillies với cả Cá Hồi Hoang nữa.", 2 * 1440L - 45, read = true)
        msg("match-minhanh", "me", "Ồ, gu giống mình thật. Cậu có đi show nào gần đây không?", 2 * 1440L - 60, read = true)
        msg("match-minhanh", "p01", "Có, mình mới đi một show nhỏ ở Hà Nội. Không khí dễ chịu lắm.", 2 * 1440L - 70, read = true)
        msg("match-minhanh", "me", "Nghe hay đấy. Mình thì hay ngồi cà phê nghe nhạc thôi.", 2 * 1440L - 85, read = true)
        msg("match-minhanh", "p01", "Cà phê cũng là một loại show mà, miễn là có tai nghe tốt.", 2 * 1440L - 90, read = true)
        msg("match-minhanh", "me", "Chuẩn. Cậu học KTPM đúng không? Có môn nào vui không?", 1440L, read = true)
        msg("match-minhanh", "p01", "Có môn thiết kế hệ thống, vừa khó vừa vui. Cậu bên ATTT à?", 1440L - 10, read = true)
        msg("match-minhanh", "me", "Không, mình cũng CNTT. Đang tập làm app và học thêm về AI.", 1440L - 20, read = true)
        msg("match-minhanh", "p01", "Vậy là cùng ngành rồi. Cậu có đang làm project gì không?", 1440L - 30, read = true)
        msg("match-minhanh", "me", "Có, một app kết nối sinh viên. Nghe hơi tham vọng nhưng vui.", 1440L - 45, read = true)
        msg("match-minhanh", "p01", "Cậu cũng thích indie à? Có nghe Vũ. chưa?", 5, read = false)

        msg("match-thaolinh", "me", "Chào Nắng Sớm, ảnh cậu chụp đẹp thật.", 4 * 1440L)
        msg("match-thaolinh", "p03", "Cảm ơn cậu, mình chụp lúc đi cà phê.", 4 * 1440L - 30)
        msg("match-thaolinh", "me", "Cậu thích chụp thể loại gì?", 4 * 1440L - 45)
        msg("match-thaolinh", "p03", "Mình thích chân dung với cảnh đời thường.", 4 * 1440L - 60)
        msg("match-thaolinh", "me", "Nghe thú vị. Hôm nào cà phê không?", 30L * 60L)
        msg("match-thaolinh", "p03", "Ừm, cũng được...", 26L * 60L, read = false)

        msg("match-ngocmai", "me", "Chào Mai, cậu có tài liệu môn Marketing không?", 6 * 1440L)
        msg("match-ngocmai", "p05", "Có, mình gửi sau nhé.", 6 * 1440L - 20)
        msg("match-ngocmai", "me", "Cảm ơn cậu nhiều.", 6 * 1440L - 30)
        msg("match-ngocmai", "p05", "Không có gì đâu.", 3 * 1440L)
        msg("match-ngocmai", "p05", "Mai gửi tài liệu sau nhé", 3 * 1440L - 1, read = false)
        return list
    }

    fun notifications(now: Long): List<NotificationEntity> = listOf(
        NotificationEntity(
            id = "notif-1",
            type = NotificationType.MATCH.name,
            title = "Bạn có match mới",
            body = "Mèo Lười đã thích bạn. Ảnh vẫn được ẩn danh nhé!",
            createdAt = now - 2 * 86_400_000L,
            read = false
        ),
        NotificationEntity(
            id = "notif-2",
            type = NotificationType.MESSAGE.name,
            title = "Tin nhắn mới từ Mèo Lười",
            body = "Cậu cũng thích indie à? Có nghe Vũ. chưa?",
            createdAt = now - 5 * 60_000L,
            read = false
        ),
        NotificationEntity(
            id = "notif-3",
            type = NotificationType.SAFETY.name,
            title = "FwP bảo vệ bạn",
            body = "Chỉ bạn mới thấy số điện thoại và vị trí chính xác. Hãy báo cáo nếu có dấu hiệu không an toàn.",
            createdAt = now - 36 * 3_600_000L,
            read = true
        ),
        NotificationEntity(
            id = "notif-4",
            type = NotificationType.ACHIEVEMENT.name,
            title = "Huy hiệu mới: Người mở lời",
            body = "Bạn đã gửi tin nhắn đầu tiên trong một cuộc trò chuyện. Tiếp tục giữ nhịp nhé!",
            createdAt = now - 48 * 3_600_000L,
            read = true
        )
    )
}
