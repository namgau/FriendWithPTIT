package vn.ptit.fwp.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatNudgeStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun persistsAcrossDataStoreRecreationAndIsolatesChatAccountAndSource() = runBlocking {
        val file = temporary.newFolder().resolve("nudges.preferences_pb")
        val job = SupervisorJob()
        val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        val store = ChatNudgeStore(data)
        val chat = store.forConversation("backend:a@example.test", "match1")
        chat.recordOverride(); chat.recordOverride()
        assertEquals(2, chat.settings.first().overrides)
        assertTrue(chat.settings.first().enabled)
        chat.recordOverride()
        assertFalse(chat.settings.first().enabled)
        assertTrue(store.forConversation("backend:a@example.test", "match2").settings.first().enabled)
        assertTrue(store.forConversation("backend:b@example.test", "match1").settings.first().enabled)
        assertTrue(store.forConversation("demo", "match1").settings.first().enabled)
        job.cancel(); job.join()

        val restoredJob = SupervisorJob()
        try {
            val restored = ChatNudgeStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + restoredJob)) { file })
            val reopened = restored.forConversation("backend:a@example.test", "match1")
            assertEquals(3, reopened.settings.first().overrides)
            assertFalse(reopened.settings.first().enabled)
            reopened.setEnabled(true)
            assertEquals(0, reopened.settings.first().overrides)
            assertTrue(reopened.settings.first().enabled)
            reopened.recordOverride()
            assertEquals(1, reopened.settings.first().overrides)
        } finally { restoredJob.cancel(); restoredJob.join() }
    }

    @Test fun concurrentConfirmationsAreAtomicAndCounterIsCapped() = runBlocking {
        val file = temporary.newFolder().resolve("nudges.preferences_pb")
        val job = SupervisorJob()
        try {
            val store = ChatNudgeStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })
            val chat = store.forConversation("demo", "match1")
            listOf(launch { chat.recordOverride() }, launch { chat.recordOverride() }).joinAll()
            assertEquals(2, chat.settings.first().overrides)
            repeat(5) { chat.recordOverride() }
            assertEquals(3, chat.settings.first().overrides)
            // A delimiter inside an identifier must not collide with a different pair.
            store.forConversation("a", "bc").setEnabled(false)
            assertTrue(store.forConversation("ab", "c").settings.first().enabled)
        } finally { job.cancel(); job.join() }
    }
}
