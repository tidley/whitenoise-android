package dev.ipf.whitenoise.android.ui.conversation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import dev.ipf.marmotkit.AccountSummaryFfi
import dev.ipf.whitenoise.android.audio.tts.EngineTrust
import dev.ipf.whitenoise.android.audio.tts.FakeSessionEngine
import dev.ipf.whitenoise.android.audio.tts.TtsEngineInfo
import dev.ipf.whitenoise.android.audio.tts.TtsResolutionResult
import dev.ipf.whitenoise.android.core.EMPTY_MARKDOWN_DOCUMENT
import dev.ipf.whitenoise.android.state.ConversationController
import dev.ipf.whitenoise.android.state.MessageStatus
import dev.ipf.whitenoise.android.state.TimelineMessage
import dev.ipf.whitenoise.android.state.TtsAutoReadOverride
import dev.ipf.whitenoise.android.state.TtsAutoReadPreferences
import dev.ipf.whitenoise.android.state.WhiteNoiseAppState
import dev.ipf.whitenoise.android.state.conversationTimelineTestGroup
import dev.ipf.whitenoise.android.state.timelineAppMessage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConversationAutoReadArrivalTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val engine = FakeSessionEngine()
    private lateinit var app: WhiteNoiseAppState
    private lateinit var controller: ConversationController

    @Before
    fun prepareEmptyConversation() {
        context
            .getSharedPreferences(TtsAutoReadPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        app =
            WhiteNoiseAppState(
                context = context,
                accountIdHexResolver = { null },
                accounts = listOf(AccountSummaryFfi("account", "aa", true, false, false, true)),
                activeAccountRef = "account",
            )
        app.ttsController.attachEngine(engine)
        val resolutionField = WhiteNoiseAppState::class.java.getDeclaredField("ttsResolution\$delegate")
        resolutionField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val resolution = resolutionField.get(app) as androidx.compose.runtime.MutableState<TtsResolutionResult?>
        resolution.value =
            TtsResolutionResult(
                status = 0,
                engines = listOf(TtsEngineInfo("test", "Test", EngineTrust.Local)),
                defaultEnginePackage = "test",
                handle = null,
            )
        controller =
            ConversationController(
                appState = app,
                initialGroup = conversationTimelineTestGroup(),
                accountRefOverride = "account",
                startOnConstruction = false,
                markdownParser = { EMPTY_MARKDOWN_DOCUMENT },
            )
        composeRule.setContent {
            ConversationTtsAutoReadEffects(app, controller, "chat", 0, null, true)
        }
        composeRule.waitForIdle()
    }

    @After
    fun releaseSpeech() {
        if (::app.isInitialized) app.stopSpeaking()
        if (::controller.isInitialized) controller.onCleared()
    }

    @Test
    fun enablingInsideAnEmptyChatReadsTheNextReplyWithoutReopening() {
        composeRule.runOnIdle {
            app.setConversationAutoReadOverride(controller.group.groupIdHex, TtsAutoReadOverride.ON)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { controller.timeline = listOf(incoming("first", 1uL)) }
        composeRule.waitUntil(10_000) { engine.spoken.any { "Reply first" in it.text } }
    }

    @Test
    fun backgroundArrivalsCannotRestartAnIdleReader() {
        composeRule.runOnIdle {
            app.setConversationAutoReadOverride(controller.group.groupIdHex, TtsAutoReadOverride.ON)
        }
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.runOnIdle { controller.timeline = listOf(incoming("private", 1uL)) }
        composeRule.waitForIdle()
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun disablingInsideAnEmptyChatDoesNotReadTheNextReply() {
        composeRule.runOnIdle {
            app.setConversationAutoReadOverride(controller.group.groupIdHex, TtsAutoReadOverride.ON)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            app.setConversationAutoReadOverride(controller.group.groupIdHex, TtsAutoReadOverride.OFF)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { controller.timeline = listOf(incoming("silent", 1uL)) }
        composeRule.waitForIdle()
        assertTrue(engine.spoken.isEmpty())
    }

    private fun incoming(
        id: String,
        order: ULong,
    ) = TimelineMessage(
        id = "msg:$id",
        record =
            timelineAppMessage(id).copy(
                direction = "received",
                plaintext = "Reply $id",
                groupIdHex = controller.group.groupIdHex,
            ),
        status = MessageStatus.Received,
        timelineOrder = order,
    )
}
