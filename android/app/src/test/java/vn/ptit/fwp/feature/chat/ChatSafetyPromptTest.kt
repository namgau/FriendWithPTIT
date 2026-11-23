package vn.ptit.fwp.feature.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.*
import org.junit.Test
import vn.ptit.fwp.core.data.ChatNudgePreference
import vn.ptit.fwp.core.data.ChatNudgePreferences

internal class MemoryChatNudgePreferences : ChatNudgePreferences {
    override val settings = MutableStateFlow(ChatNudgePreference())
    override suspend fun recordOverride(): ChatNudgePreference {
        settings.value = ChatNudgePreference((settings.value.overrides + 1).coerceAtMost(3))
        return settings.value
    }
    override suspend fun setEnabled(enabled: Boolean) { settings.value = ChatNudgePreference(if (enabled) 0 else 3) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSafetyPromptTest {
    @Test fun thirdOverrideDisablesAndReopeningKeepsItDisabled() = runTest {
        val prefs = MemoryChatNudgePreferences()
        val sent = mutableListOf<String>()
        var calls = 0
        fun prompt() = ChatSafetyPrompt(backgroundScope, prefs, { _, _ -> calls++; true }, { true },
            { sent.add(it) }, { throw AssertionError(it) })
        val first = prompt()
        repeat(3) { index ->
            assertTrue(first.send("draft-$index", emptyList()))
            runCurrent()
            assertEquals("draft-$index", first.state.value.pendingDraft)
            first.confirmSend()
            first.confirmSend() // double tap must not send or count twice
            runCurrent()
            assertEquals(index + 1, prefs.settings.value.overrides)
            assertEquals(index < 2, first.state.value.enabled)
        }
        assertEquals(3, sent.size)
        assertNotNull(first.state.value.notice)
        val reopened = prompt()
        // Also works before the preference collector has emitted its initial state.
        assertTrue(reopened.send("fourth", emptyList()))
        runCurrent()
        assertEquals(3, calls)
        assertEquals(listOf("draft-0", "draft-1", "draft-2", "fourth"), sent)
        assertNull(reopened.state.value.pendingDraft)
    }

    @Test fun editAndSafeSendDoNotCountAndEnablingStartsNewCycle() = runTest {
        val prefs = MemoryChatNudgePreferences()
        val sent = mutableListOf<String>()
        val prompt = ChatSafetyPrompt(backgroundScope, prefs, { text, _ -> text == "risk" }, { true },
            { sent.add(it) }, { throw AssertionError(it) })
        prompt.send("risk", emptyList()); runCurrent()
        prompt.cancelSend(); prompt.confirmSend(); runCurrent()
        assertEquals(0, prefs.settings.value.overrides)
        assertTrue(sent.isEmpty())
        prompt.send("safe", emptyList()); runCurrent()
        assertEquals(listOf("safe"), sent)
        assertEquals(0, prefs.settings.value.overrides)
        prompt.setEnabled(false); runCurrent()
        prompt.send("risk", emptyList()); runCurrent()
        assertEquals(listOf("safe", "risk"), sent)
        prompt.setEnabled(true); runCurrent()
        assertEquals(0, prefs.settings.value.overrides)
        prompt.send("risk", emptyList()); runCurrent()
        assertEquals("risk", prompt.state.value.pendingDraft)
    }

    @Test fun outstandingCheckCannotSendAfterSessionInvalidationOrOverwriteDraft() = runTest {
        val result = CompletableDeferred<Boolean>()
        val prefs = MemoryChatNudgePreferences()
        val sent = mutableListOf<String>()
        var valid = true
        val prompt = ChatSafetyPrompt(backgroundScope, prefs, { _, _ -> result.await() }, { valid },
            { sent.add(it) }, { throw AssertionError(it) })
        assertTrue(prompt.send("first", emptyList()))
        assertFalse(prompt.send("second", emptyList()))
        runCurrent()
        valid = false
        prompt.invalidate()
        result.complete(true); runCurrent()
        assertNull(prompt.state.value.pendingDraft)
        assertFalse(prompt.send("later", emptyList()))
        assertTrue(sent.isEmpty())
        assertEquals(0, prefs.settings.value.overrides)
    }

    @Test fun preferenceChangedWhileAiRunsSuppressesOldNudge() = runTest {
        val result = CompletableDeferred<Boolean>()
        val prefs = MemoryChatNudgePreferences()
        val sent = mutableListOf<String>()
        val prompt = ChatSafetyPrompt(backgroundScope, prefs, { _, _ -> result.await() }, { true },
            { sent.add(it) }, { throw AssertionError(it) })
        prompt.send("draft", emptyList()); runCurrent()
        prefs.setEnabled(false)
        result.complete(true); runCurrent()
        assertEquals(listOf("draft"), sent)
        assertNull(prompt.state.value.pendingDraft)
    }

    @Test fun failedPersistenceKeepsDraftAndDoesNotSendOrCount() = runTest {
        val prefs = object : ChatNudgePreferences {
            override val settings = MutableStateFlow(ChatNudgePreference())
            override suspend fun recordOverride(): ChatNudgePreference = throw java.io.IOException("disk full")
            override suspend fun setEnabled(enabled: Boolean) { throw java.io.IOException("disk full") }
        }
        val sent = mutableListOf<String>()
        var errors = 0
        val prompt = ChatSafetyPrompt(backgroundScope, prefs, { _, _ -> true }, { true }, { sent.add(it) }, { errors++ })
        prompt.send("draft", emptyList()); runCurrent()
        prompt.confirmSend(); runCurrent()
        assertEquals("draft", prompt.state.value.pendingDraft)
        assertEquals(0, prefs.settings.value.overrides)
        assertTrue(sent.isEmpty())
        assertEquals(1, errors)
    }

    @Test fun readFailureRestoresDraftAndCancellationCannotFallThroughToSending() = runTest {
        val sent = mutableListOf<String>()
        val prefs = object : ChatNudgePreferences {
            override val settings = kotlinx.coroutines.flow.flow<ChatNudgePreference> { throw java.io.IOException("unreadable") }
            override suspend fun recordOverride() = ChatNudgePreference()
            override suspend fun setEnabled(enabled: Boolean) {}
        }
        val prompt = ChatSafetyPrompt(backgroundScope, prefs, { _, _ -> false }, { true }, { sent.add(it) }, {})
        prompt.send("keep me", emptyList()); runCurrent()
        assertEquals("keep me", prompt.state.value.failedDraft)
        assertTrue(sent.isEmpty())

        val result = CompletableDeferred<Boolean>()
        val job = Job()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job)
        val cancelled = ChatSafetyPrompt(scope, MemoryChatNudgePreferences(), { _, _ ->
            try { result.await() } catch (_: CancellationException) { false }
        }, { true }, { sent.add(it) }, { throw AssertionError(it) })
        cancelled.send("never send", emptyList()); runCurrent()
        job.cancel(); runCurrent()
        assertTrue(sent.isEmpty())
        assertNull(cancelled.state.value.pendingDraft)
    }
}
