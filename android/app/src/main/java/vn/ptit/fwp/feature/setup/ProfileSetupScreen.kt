package vn.ptit.fwp.feature.setup

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import vn.ptit.fwp.core.design.DemoPortrait
import vn.ptit.fwp.core.design.FwpChip
import vn.ptit.fwp.core.design.FwpTextField
import vn.ptit.fwp.core.design.FwpTopBar
import vn.ptit.fwp.core.design.GpaBadge
import vn.ptit.fwp.core.design.ModeChip
import vn.ptit.fwp.core.design.PrimaryButton
import vn.ptit.fwp.core.design.PtitGold
import vn.ptit.fwp.core.design.PtitRed
import vn.ptit.fwp.core.design.PtitRedSoft
import vn.ptit.fwp.core.design.SearchField
import vn.ptit.fwp.core.design.SecondaryButton
import vn.ptit.fwp.core.design.SectionHeader
import vn.ptit.fwp.core.design.StepProgress
import vn.ptit.fwp.core.model.Gender
import vn.ptit.fwp.core.model.INTEREST_CATALOG
import vn.ptit.fwp.core.model.UniversityCatalog
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.formatGpa
import vn.ptit.fwp.core.util.fwpViewModel

@Composable
fun ProfileSetupScreen(
    editMode: Boolean,
    onExit: () -> Unit,
    onDone: () -> Unit
) {
    val container = appContainer()
    val context = LocalContext.current
    val viewModel = fwpViewModel { ProfileSetupViewModel(container, editMode) }
    val state by viewModel.state.collectAsState()
    var gpaText by remember(state.loaded) { mutableStateOf(formatGpa(state.draft.gpa)) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setPhotoUri(uri.toString())
        }
    }

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    Column(Modifier.fillMaxSize()) {
        FwpTopBar(
            title = if (editMode) "Chỉnh sửa hồ sơ" else "Hoàn thiện hồ sơ",
            subtitle = "Bước ${state.step + 1}/${state.totalSteps} · ${stepTitle(state.step)}",
            onBack = {
                if (state.step > 0) viewModel.back() else onExit()
            }
        )
        StepProgress(current = state.step, total = state.totalSteps, modifier = Modifier.padding(horizontal = 20.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (!state.loaded) {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
                return@Column
            }
            when (state.step) {
                0 -> BasicStep(state, viewModel, onPickPhoto = { photoPicker.launch(arrayOf("image/*")) })
                1 -> {
                    AcademicStep(state, viewModel, gpaText) { value ->
                        gpaText = value
                        viewModel.setGpaText(value)
                    }
                }
                2 -> InterestsStep(state, viewModel)
                3 -> PrivacyStep(state, viewModel)
            }
            if (state.error != null) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        state.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.step > 0) {
                SecondaryButton(
                    text = "Quay lại",
                    onClick = viewModel::back,
                    modifier = Modifier.weight(0.42f)
                )
            }
            PrimaryButton(
                text = when {
                    state.step < state.totalSteps - 1 -> "Tiếp tục"
                    editMode -> "Lưu thay đổi"
                    else -> "Hoàn tất hồ sơ"
                },
                icon = if (state.step == state.totalSteps - 1) Icons.Rounded.Check else null,
                loading = state.saving,
                onClick = viewModel::next,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BasicStep(
    state: SetupUiState,
    viewModel: ProfileSetupViewModel,
    onPickPhoto: () -> Unit
) {
    SectionHeader(
        title = "Bạn xuất hiện thế nào?",
        subtitle = "Nickname và vài dòng giới thiệu là thứ đầu tiên người khác thấy."
    )
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(118.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(PtitRedSoft),
            contentAlignment = Alignment.Center
        ) {
            if (!state.draft.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = state.draft.photoUrl,
                    contentDescription = "Ảnh đại diện đã chọn",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                DemoPortrait(state.draft.avatarSeed, Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPickPhoto),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Ảnh đại diện", style = MaterialTheme.typography.titleSmall)
            Text(
                "Chọn ảnh từ thiết bị hoặc dùng chân dung minh họa. Trong khám phá, ảnh luôn được làm mờ đến khi đủ điều kiện mở.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(onClick = onPickPhoto)
            ) {
                Text(
                    "Chọn ảnh",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("Hoặc chọn phong cách minh họa", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        (1..6).forEach { seed ->
            val selected = state.draft.avatarSeed == seed && state.draft.photoUrl.isNullOrBlank()
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (selected) PtitRed else MaterialTheme.colorScheme.outline, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .clickable {
                        viewModel.setAvatarSeed(seed)
                        viewModel.setPhotoUri(null)
                    }
            ) {
                DemoPortrait(seed, Modifier.fillMaxSize())
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    FwpTextField(
        value = state.draft.nickname,
        onValueChange = viewModel::setNickname,
        label = "Nickname",
        placeholder = "Ví dụ: Mây Nhỏ, Cà Phê Đắng, Mèo Lười…",
        isError = state.draft.nickname.isNotBlank() && state.draft.nickname.length < 2
    )
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Tuổi", style = MaterialTheme.typography.titleSmall)
            Text("FwP chỉ dành cho người từ 18 tuổi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.RemoveCircleOutline,
                contentDescription = "Giảm tuổi",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { viewModel.setAge(state.draft.age - 1) }
                    .padding(5.dp)
            )
            Text(
                state.draft.age.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Icon(
                Icons.Rounded.AddCircleOutline,
                contentDescription = "Tăng tuổi",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { viewModel.setAge(state.draft.age + 1) }
                    .padding(5.dp)
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    Text("Giới tính", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Gender.FEMALE, Gender.MALE, Gender.OTHER).forEach { gender ->
            FwpChip(
                text = gender.label,
                selected = state.draft.gender == gender,
                onClick = { viewModel.setGender(gender) }
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    FwpTextField(
        value = state.draft.bio,
        onValueChange = viewModel::setBio,
        label = "Giới thiệu ngắn",
        placeholder = "Một câu về bạn, một điều bạn thích, một điều bạn đang tìm…",
        singleLine = false,
        maxLines = 4,
        supportingText = "${state.draft.bio.length}/300"
    )
}

@Composable
private fun AcademicStep(
    state: SetupUiState,
    viewModel: ProfileSetupViewModel,
    gpaText: String,
    onGpaChange: (String) -> Unit
) {
    val selectedUniversity = UniversityCatalog.universities.firstOrNull { it.name == state.draft.school }
    SectionHeader(
        title = "Hành trình học tập",
        subtitle = "Thông tin học vấn giúp FwP tìm người cùng trường, cùng khoa hoặc cùng nhịp học."
    )
    Spacer(Modifier.height(18.dp))
    if (state.email.isNotBlank()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = PtitGold.copy(alpha = 0.13f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.School, null, tint = PtitRed, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("Email đã xác thực", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.email, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    Text("Trường", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    SearchField(
        value = state.schoolQuery,
        onValueChange = viewModel::onSchoolQueryChange,
        placeholder = "Tìm trường của bạn…"
    )
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        state.schoolResults.take(5).forEach { university ->
            val selected = university.name == state.draft.school
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectSchool(university) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        university.shortName.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(university.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(
                        university.campuses.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (selected) {
                    Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
    if (selectedUniversity != null) {
        Spacer(Modifier.height(16.dp))
        Text("Cơ sở", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selectedUniversity.campuses.forEach { campus ->
                FwpChip(
                    text = campus,
                    selected = state.draft.campus == campus,
                    onClick = { viewModel.selectCampus(campus) }
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    FwpTextField(
        value = state.draft.faculty,
        onValueChange = viewModel::setFaculty,
        label = "Khoa / Viện",
        placeholder = "Ví dụ: Công nghệ Thông tin"
    )
    Spacer(Modifier.height(14.dp))
    FwpTextField(
        value = state.draft.major,
        onValueChange = viewModel::setMajor,
        label = "Ngành học",
        placeholder = "Ví dụ: Kỹ thuật Phần mềm"
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FwpTextField(
            value = state.draft.academicYear,
            onValueChange = viewModel::setAcademicYear,
            label = "Khóa",
            placeholder = "K66",
            modifier = Modifier.weight(1f)
        )
        FwpTextField(
            value = gpaText,
            onValueChange = onGpaChange,
            label = "GPA (hệ 4)",
            placeholder = "3.20",
            modifier = Modifier.weight(1f),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            )
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        GpaBadge(state.gpaTier)
        Spacer(Modifier.width(9.dp))
        Text(
            "GPA chỉ hiển thị dạng tầng nếu bạn chọn ẩn số cụ thể.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(12.dp))
    ToggleRow(
        title = "Mình đã tốt nghiệp",
        subtitle = "Hiển thị huy hiệu cựu sinh viên trên hồ sơ.",
        checked = state.draft.alumni,
        onCheckedChange = viewModel::setAlumni
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InterestsStep(state: SetupUiState, viewModel: ProfileSetupViewModel) {
    SectionHeader(
        title = "Gu của bạn",
        subtitle = "Chọn 3–8 sở thích. Đây là tín hiệu mạnh nhất để M1 và M2 tìm người đồng điệu."
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "Đã chọn ${state.draft.interests.size}/8",
        style = MaterialTheme.typography.labelLarge,
        color = if (state.draft.interests.size >= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        INTEREST_CATALOG.forEach { interest ->
            FwpChip(
                text = interest,
                selected = state.draft.interests.contains(interest),
                onClick = { viewModel.toggleInterest(interest) }
            )
        }
    }
    Spacer(Modifier.height(26.dp))
    SectionHeader(
        title = "Bạn đang tìm gì?",
        subtitle = "Chọn chế độ kết nối chính. Bạn vẫn có thể đổi trong bộ lọc khám phá."
    )
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        vn.ptit.fwp.core.model.ConnectionMode.entries.forEach { mode ->
            val selected = state.draft.mode == mode
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setMode(mode) },
                shape = RoundedCornerShape(18.dp),
                color = if (selected) PtitRed.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (selected) PtitRed else MaterialTheme.colorScheme.outline
                )
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModeChip(mode = mode, selected = true, onClick = { viewModel.setMode(mode) })
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mode.headline, style = MaterialTheme.typography.titleSmall)
                        Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (selected) PtitRed else MaterialTheme.colorScheme.surfaceVariant)
                            .then(
                                if (selected) Modifier.border(0.dp, PtitRed, CircleShape) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyStep(state: SetupUiState, viewModel: ProfileSetupViewModel) {
    SectionHeader(
        title = "Bạn kiểm soát thông tin",
        subtitle = "FwP mặc định ẩn ảnh và cho phép bạn giới hạn những gì người khác thấy."
    )
    Spacer(Modifier.height(16.dp))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PtitRedSoft.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = PtitRed, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(11.dp))
            Text(
                "Ảnh đại diện luôn được làm mờ khi người khác khám phá bạn. Chỉ mở khi cả hai đồng ý và đã trò chuyện đủ 8 tin nhắn với điểm tương thích từ 72.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    ToggleRow(
        title = "Hiển thị GPA",
        subtitle = "Tắt để chỉ hiện tầng học lực (Giỏi, Khá, Trung bình khá…).",
        checked = state.draft.showGpa,
        onCheckedChange = viewModel::setShowGpa
    )
    Spacer(Modifier.height(12.dp))
    ToggleRow(
        title = "Hiển thị khoảng cách",
        subtitle = "Chỉ hiện khoảng cách ước lượng, không chia sẻ vị trí chính xác.",
        checked = state.draft.showLocation,
        onCheckedChange = viewModel::setShowLocation
    )
    Spacer(Modifier.height(22.dp))
    SectionHeader(title = "Xem lại hồ sơ")
    Spacer(Modifier.height(12.dp))
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!state.draft.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = state.draft.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        DemoPortrait(state.draft.avatarSeed, Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.draft.nickname.ifBlank { "Nickname của bạn" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${state.draft.academicYear} · ${state.draft.major}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    GpaBadge(state.gpaTier, compact = true)
                }
                ModeChip(mode = state.draft.mode, selected = true, onClick = {})
            }
            if (state.draft.bio.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(state.draft.bio, style = MaterialTheme.typography.bodySmall)
            }
            if (state.draft.interests.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    state.draft.interests.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(
        "Hồ sơ demo dùng dữ liệu mô phỏng. Không có dữ liệu nào được gửi lên máy chủ khi bạn chưa cấu hình backend.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun stepTitle(step: Int): String = when (step) {
    0 -> "Thông tin cơ bản"
    1 -> "Học vấn"
    2 -> "Sở thích & chế độ"
    else -> "Quyền riêng tư"
}
