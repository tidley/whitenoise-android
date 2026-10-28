package dev.ipf.whitenoise.android.ui.conversation

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.ipf.marmotkit.AccountSummaryFfi
import dev.ipf.whitenoise.android.audio.tts.EngineTrust
import dev.ipf.whitenoise.android.audio.tts.FakeSessionEngine
import dev.ipf.whitenoise.android.audio.tts.TtsEngineInfo
import dev.ipf.whitenoise.android.audio.tts.TtsResolutionResult
import dev.ipf.whitenoise.android.audio.tts.TtsSpeakableEntry
import dev.ipf.whitenoise.android.audio.tts.TtsState
import dev.ipf.whitenoise.android.core.EMPTY_MARKDOWN_DOCUMENT
import dev.ipf.whitenoise.android.state.ConversationController
import dev.ipf.whitenoise.android.state.ConversationLiveSubscriptions
import dev.ipf.whitenoise.android.state.DraftPersistence
import dev.ipf.whitenoise.android.state.DraftStore
import dev.ipf.whitenoise.android.state.MessageStatus
import dev.ipf.whitenoise.android.state.ScriptedConversationTimelineSubscription
import dev.ipf.whitenoise.android.state.TimelineMessage
import dev.ipf.whitenoise.android.state.TtsAutoReadOverride
import dev.ipf.whitenoise.android.state.TtsAutoReadPreferences
import dev.ipf.whitenoise.android.state.WhiteNoiseAppState
import dev.ipf.whitenoise.android.state.conversationTimelineTestGroup
import dev.ipf.whitenoise.android.state.timelineAppMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConversationAutoReadReadinessTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val engine = FakeSessionEngine()
    private lateinit var app: WhiteNoiseAppState
    private lateinit var controller: ConversationController

    @Before
    fun prepareUnreadConversation() {
        context
            .getSharedPreferences(TtsAutoReadPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        app =
            WhiteNoiseAppState(
                context = context,
                draftStore = DraftStore(NoOpDraftPersistence),
                accountIdHexResolver = { null },
                accounts = listOf(AccountSummaryFfi("account", "aa", true, false, false, true)),
                activeAccountRef = "account",
            )
        app.ttsController.attachEngine(engine)
        // Keep native continuation alive without loading JNI or racing a failed native open.
        app.liveSubscriptionOverrides.conversation =
            ConversationLiveSubscriptions(
                openTimeline = { _, _, _ -> ScriptedConversationTimelineSubscription(snapshotPage = null) },
                openGroupState = { _, _ -> error("No group subscription expected") },
            )
        controller =
            ConversationController(
                appState = app,
                initialGroup = conversationTimelineTestGroup(),
                accountRefOverride = "account",
                startOnConstruction = false,
                markdownParser = { EMPTY_MARKDOWN_DOCUMENT },
            )
        val field = ConversationController::class.java.getDeclaredField("timeline\$delegate")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val timeline = field.get(controller) as androidx.compose.runtime.MutableState<List<TimelineMessage>>
        timeline.value =
            listOf(
                TimelineMessage(
                    id = "msg:unread",
                    record =
                        timelineAppMessage("unread").copy(
                            direction = "received",
                            plaintext = "Unread reply.",
                            groupIdHex = controller.group.groupIdHex,
                        ),
                    status = MessageStatus.Received,
                    timelineOrder = 1uL,
                ),
            )
        app.setConversationAutoReadOverride(controller.group.groupIdHex, TtsAutoReadOverride.ON)
        setEngineReady(false)
    }

    @After
    fun releaseSpeech() {
        if (::app.isInitialized) app.stopSpeaking()
        if (::controller.isInitialized) controller.onCleared()
    }

    @Test
    fun delayedEngineDiscoveryReadsEntryBacklogWithoutReopeningAndOnlyOnce() {
        openConversation()
        assertTrue(engine.spoken.isEmpty())

        composeRule.runOnIdle { setEngineReady(true) }
        awaitUnreadSpeech()
        val spokenCount = engine.spoken.size
        composeRule.runOnIdle { setEngineReady(false) }
        composeRule.runOnIdle { setEngineReady(true) }
        composeRule.waitForIdle()
        assertEquals(spokenCount, engine.spoken.size)
    }

    @Test
    fun delayedEngineDiscoveryPreservesManualSpeech() = assertManualSpeechRetained(paused = false)

    @Test
    fun delayedEngineDiscoveryPreservesPausedManualSpeech() = assertManualSpeechRetained(paused = true)

    @Test
    fun naturallyCompletedAutoReadCannotSuppressBacklogOnReopen() {
        composeRule.runOnIdle {
            startPreviousAutoRead()
            engine.complete(engine.spoken.lastIndex)
            assertTrue(app.ttsController.state.value is TtsState.Idle)
            assertFalse(app.ownsTtsAutoReadSession(controller.group.groupIdHex))
        }
        openConversation()
        awaitUnreadSpeech()
    }

    @Test
    fun recoverableSynthesisErrorKeepsPausedAutoReadOwnershipOnReopen() {
        composeRule.runOnIdle {
            startPreviousAutoRead()
            engine.fail(engine.spoken.lastIndex, TextToSpeech.ERROR_SYNTHESIS)
            assertTrue(app.ttsController.state.value is TtsState.Paused)
            assertTrue(app.ownsTtsAutoReadSession(controller.group.groupIdHex))
        }
        val previous = app.ttsController.state.value
        openConversation()
        assertEquals(previous, app.ttsController.state.value)
        assertFalse(engine.spoken.any { "Unread reply" in it.text })
    }

    private fun assertManualSpeechRetained(paused: Boolean) {
        openConversation()
        composeRule.runOnIdle {
            assertTrue(app.speakAloud(listOf(TtsSpeakableEntry("manual", "Sender", "Manual speech.")), Locale.US))
            if (paused) app.ttsController.pause()
        }
        val previous = app.ttsController.state.value
        composeRule.runOnIdle { setEngineReady(true) }
        composeRule.waitForIdle()
        val current = app.ttsController.state.value
        assertEquals(previous.sessionId, current.sessionId)
        assertEquals(previous.javaClass, current.javaClass)
        assertFalse(engine.spoken.any { "Unread reply" in it.text })
    }

    private fun startPreviousAutoRead() {
        setEngineReady(true)
        assertTrue(
            app.speakAloudAutoRead(
                controller.group.groupIdHex,
                listOf(TtsSpeakableEntry("previous", "Sender", "Finished.")),
                Locale.US,
            ),
        )
        assertTrue(app.ownsTtsAutoReadSession(controller.group.groupIdHex))
        app.ttsController.pause()
        assertTrue(app.ownsTtsAutoReadSession(controller.group.groupIdHex))
        app.ttsController.resume()
    }

    private fun openConversation() {
        composeRule.setContent {
            ConversationTtsAutoReadEffects(app, controller, "chat", 1, "unread", true)
        }
        composeRule.waitForIdle()
    }

    private fun awaitUnreadSpeech() {
        composeRule.waitUntil(10_000) {
            composeRule.waitForIdle()
            engine.spoken.any { "Unread reply" in it.text }
        }
    }

    private fun setEngineReady(ready: Boolean) {
        val field = WhiteNoiseAppState::class.java.getDeclaredField("ttsResolution\$delegate")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val resolution = field.get(app) as androidx.compose.runtime.MutableState<TtsResolutionResult?>
        resolution.value =
            if (ready) {
                TtsResolutionResult(
                    status = 0,
                    engines = listOf(TtsEngineInfo("test", "Test", EngineTrust.Local)),
                    defaultEnginePackage = "test",
                    handle = null,
                )
            } else {
                null
            }
    }

    private object NoOpDraftPersistence : DraftPersistence {
        override fun read(): Map<String, String> = emptyMap()

        override fun write(
            key: String,
            value: String?,
        ) = Unit
    }
}
