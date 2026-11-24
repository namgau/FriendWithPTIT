package vn.ptit.fwp.feature.social

import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ptit.fwp.AppContainer
import vn.ptit.fwp.core.data.PresentationIdentity
import vn.ptit.fwp.core.data.remote.FunQuestionDto
import vn.ptit.fwp.core.design.FwpChip
import vn.ptit.fwp.core.design.FwpTopBar
import vn.ptit.fwp.core.util.appContainer
import vn.ptit.fwp.core.util.fwpViewModel

data class QuestionsState(
    val loading: Boolean = true,
    val saving: String? = null,
    val error: String? = null,
    val items: List<FunQuestionDto> = emptyList()
) {
    val answered: Int get() = items.count { it.myAnswer != null }
}

class BackendQuestionsViewModel(container: AppContainer, identity: PresentationIdentity) : BackendScopedViewModel(container, identity) {
    private val mutable = MutableStateFlow(QuestionsState())
    val state = mutable.asStateFlow()
    init { observeIdentity(container); load() }
    override fun invalidate() { mutable.value = QuestionsState(loading = false) }

    fun load() = run(null) { api.questions() }

    /** Chọn câu trả lời của mình ; tập chấp nhận giữ nguyên (server luôn thêm chính câu của mình). */
    fun answer(question: FunQuestionDto, option: Int) =
        run(question.id) { api.answer(question.id, option, question.myAcceptable.orEmpty().ifEmpty { listOf(option) }) }

    /** Câu trả lời tự viết (answer = -1). */
    fun answerText(question: FunQuestionDto, text: String) {
        if (text.isBlank()) return
        run(question.id) { api.answer(question.id, -1, emptyList(), text.trim()) }
    }

    /** Bật/tắt một đáp án mình chấp nhận ở người kia. */
    fun toggleAcceptable(question: FunQuestionDto, option: Int) {
        val mine = question.myAnswer?.takeIf { it >= 0 } ?: return
        val current = question.myAcceptable.orEmpty().toSet()
        val next = if (option in current) current - option else current + option
        run(question.id) { api.answer(question.id, mine, (next + mine).sorted()) }
    }

    private fun run(id: String?, block: suspend () -> vn.ptit.fwp.core.data.remote.FunQuestionsDto) {
        if (!valid || mutable.value.saving != null) return
        mutable.update { it.copy(saving = id, error = null, loading = id == null) }
        viewModelScope.launch {
            try { val result = block(); if (valid) mutable.update { it.copy(loading = false, saving = null, items = result.items) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid) mutable.update { it.copy(loading = false, saving = null, error = socialError(e)) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackendQuestionsScreen(identity: PresentationIdentity, onBack: () -> Unit) {
    val container = appContainer()
    val vm = fwpViewModel(key = "questions:$identity") { BackendQuestionsViewModel(container, identity) }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        FwpTopBar("Câu hỏi hợp gu", subtitle = "Đã trả lời ${state.answered}/${state.items.size} · cần 3 câu chung để tính % hợp", onBack = onBack)
        if (state.loading || state.saving != null) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.items, key = { it.id }) { q ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(q.text, style = MaterialTheme.typography.titleSmall)
                        Text("Bạn chọn", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            q.options.forEachIndexed { i, option ->
                                FwpChip(text = option, selected = q.myAnswer == i, onClick = { vm.answer(q, i) })
                            }
                        }
                        var custom by androidx.compose.runtime.saveable.rememberSaveable(q.id) { androidx.compose.runtime.mutableStateOf(q.myText.orEmpty()) }
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            androidx.compose.material3.OutlinedTextField(custom, { custom = it.take(200) }, singleLine = true,
                                placeholder = { Text("Hoặc tự viết câu trả lời…") }, modifier = Modifier.weight(1f))
                            androidx.compose.material3.TextButton(onClick = { vm.answerText(q, custom) }, enabled = custom.isNotBlank() && custom != q.myText) { Text("Lưu") }
                        }
                        if (q.myAnswer == -1) Text("Đang dùng câu tự viết (không tính vào % hợp gu)", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (q.myAnswer != null && q.myAnswer >= 0) {
                            Text("Bạn chấp nhận người kia trả lời", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                q.options.forEachIndexed { i, option ->
                                    FwpChip(text = option, selected = i in q.myAcceptable.orEmpty(), onClick = { vm.toggleAcceptable(q, i) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
