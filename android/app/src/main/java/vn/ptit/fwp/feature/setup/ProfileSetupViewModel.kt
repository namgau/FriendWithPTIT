package vn.ptit.fwp.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.model.ConnectionMode
import vn.ptit.fwp.core.model.GpaTier
import vn.ptit.fwp.core.model.MyProfileDraft
import vn.ptit.fwp.core.model.Profile
import vn.ptit.fwp.core.model.University
import vn.ptit.fwp.core.model.UniversityCatalog

data class SetupUiState(
    val step: Int = 0,
    val draft: MyProfileDraft = MyProfileDraft(),
    val schoolQuery: String = "",
    val schoolResults: List<University> = UniversityCatalog.universities,
    val email: String = "",
    val error: String? = null,
    val saving: Boolean = false,
    val done: Boolean = false,
    val isEdit: Boolean = false,
    val showSchoolList: Boolean = false,
    val loaded: Boolean = false
) {
    val totalSteps: Int get() = 4
    val progress: Float get() = (step + 1) / totalSteps.toFloat()
    val gpaTier: GpaTier get() = GpaTier.fromGpa(draft.gpa)
}

class ProfileSetupViewModel(
    private val container: AppContainer,
    private val editMode: Boolean
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState(isEdit = editMode))
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = container.settingsStore.settings.first()
            val existing = container.repository.myProfile.first()
            val university = UniversityCatalog.universityForEmail(settings.email) ?: UniversityCatalog.ptit
            val draft = existing?.toDraft(settings.email) ?: MyProfileDraft(
                email = settings.email,
                school = university.name,
                campus = university.campuses.first()
            )
            if (editMode && existing == null) {
                _state.update { it.copy(error = "Chưa tìm thấy hồ sơ để chỉnh sửa.", loaded = true) }
            } else {
                _state.update {
                    it.copy(
                        draft = draft,
                        email = settings.email,
                        schoolResults = UniversityCatalog.search(""),
                        loaded = true
                    )
                }
            }
        }
    }

    fun setNickname(value: String) = updateDraft { copy(nickname = value.take(24)) }
    fun setBio(value: String) = updateDraft { copy(bio = value.take(300)) }
    fun setGender(gender: vn.ptit.fwp.core.model.Gender) = updateDraft { copy(gender = gender) }
    fun setAge(age: Int) = updateDraft { copy(age = age.coerceIn(18, 35)) }
    fun setFaculty(value: String) = updateDraft { copy(faculty = value.take(80)) }
    fun setMajor(value: String) = updateDraft { copy(major = value.take(80)) }
    fun setAcademicYear(value: String) = updateDraft { copy(academicYear = value.take(12)) }
    fun setAlumni(value: Boolean) = updateDraft { copy(alumni = value) }
    fun setGpaText(value: String) {
        val normalized = value.replace(',', '.')
        val parsed = normalized.toDoubleOrNull()
        if (normalized.isEmpty()) return
        if (parsed != null) updateDraft { copy(gpa = parsed.coerceIn(0.0, 4.0)) }
    }
    fun setMode(mode: ConnectionMode) = updateDraft { copy(mode = mode) }
    fun setShowGpa(value: Boolean) = updateDraft { copy(showGpa = value) }
    fun setShowLocation(value: Boolean) = updateDraft { copy(showLocation = value) }

    fun setPhotoUri(uri: String?) = updateDraft { copy(photoUrl = uri) }

    fun toggleInterest(interest: String) = updateDraft {
        val next = if (interests.contains(interest)) interests - interest else interests + interest
        copy(interests = next.take(8).toSet())
    }

    fun setAvatarSeed(seed: Int) = updateDraft { copy(avatarSeed = seed) }

    fun onSchoolQueryChange(query: String) {
        _state.update {
            it.copy(
                schoolQuery = query,
                schoolResults = UniversityCatalog.search(query),
                showSchoolList = true,
                error = null
            )
        }
    }

    fun toggleSchoolList(show: Boolean) = _state.update { it.copy(showSchoolList = show) }

    fun selectSchool(university: University) = _state.update {
        it.copy(
            draft = it.draft.copy(
                school = university.name,
                campus = university.campuses.firstOrNull() ?: it.draft.campus
            ),
            schoolQuery = university.shortName,
            showSchoolList = false,
            error = null
        )
    }

    fun selectCampus(campus: String) = updateDraft { copy(campus = campus) }

    fun next() {
        val s = _state.value
        val error = validate(s.step, s.draft)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        if (s.step < s.totalSteps - 1) {
            _state.update { it.copy(step = s.step + 1, error = null) }
        } else {
            save()
        }
    }

    fun back() {
        _state.update { if (it.step > 0) it.copy(step = it.step - 1, error = null) else it }
    }

    fun save() {
        val s = _state.value
        for (step in 0 until s.totalSteps) {
            val error = validate(step, s.draft)
            if (error != null) {
                _state.update { it.copy(error = error, step = step) }
                return
            }
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val draft = s.draft.copy(email = s.email)
            container.repository.saveMyProfile(draft)
            container.settingsStore.setShowGpa(draft.showGpa)
            container.settingsStore.setShowLocation(draft.showLocation)
            container.settingsStore.setLocationEnabled(draft.showLocation)
            container.settingsStore.completeProfile()
            _state.update { it.copy(saving = false, done = true) }
        }
    }

    private fun validate(step: Int, draft: MyProfileDraft): String? = when (step) {
        0 -> when {
            draft.nickname.trim().length < 2 -> "Nickname cần ít nhất 2 ký tự."
            draft.bio.trim().length < 10 -> "Hãy viết vài dòng giới thiệu (ít nhất 10 ký tự) để mọi người hiểu bạn hơn."
            else -> null
        }
        1 -> when {
            draft.faculty.isBlank() -> "Bạn chưa nhập khoa."
            draft.major.isBlank() -> "Bạn chưa nhập ngành học."
            draft.academicYear.isBlank() -> "Bạn chưa nhập khóa học."
            else -> null
        }
        2 -> when {
            draft.interests.size < 3 -> "Chọn ít nhất 3 sở thích để FwP hiểu gu của bạn."
            else -> null
        }
        else -> null
    }

    private fun updateDraft(block: MyProfileDraft.() -> MyProfileDraft) =
        _state.update { it.copy(draft = it.draft.block(), error = null) }

    private fun Profile.toDraft(email: String) = MyProfileDraft(
        nickname = nickname,
        age = age,
        gender = gender,
        school = school,
        campus = campus,
        faculty = faculty,
        major = major,
        academicYear = academicYear,
        alumni = alumni,
        gpa = gpa,
        bio = bio,
        interests = interests.toSet(),
        avatarSeed = avatarSeed,
        showGpa = showGpa,
        showLocation = showLocation,
        mode = mode,
        email = email,
        photoUrl = photoUrl
    )
}
