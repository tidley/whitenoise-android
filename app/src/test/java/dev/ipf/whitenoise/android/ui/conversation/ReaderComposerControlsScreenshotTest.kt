package dev.ipf.whitenoise.android.ui.conversation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.ipf.whitenoise.android.audio.ConversationDictationController
import dev.ipf.whitenoise.android.audio.ConversationDictationDraftSnapshot
import dev.ipf.whitenoise.android.audio.ConversationDictationPlatform
import dev.ipf.whitenoise.android.audio.ConversationDictationRecognitionListener
import dev.ipf.whitenoise.android.audio.ConversationDictationRecognitionSession
import dev.ipf.whitenoise.android.audio.ConversationDictationState
import dev.ipf.whitenoise.android.audio.VoiceRecordingController
import dev.ipf.whitenoise.android.core.MessageTextCopy
import dev.ipf.whitenoise.android.state.MessageStatus
import dev.ipf.whitenoise.android.ui.conversation.composer.ComposerBar
import dev.ipf.whitenoise.android.ui.conversation.messages.MESSAGE_FULL_SCREEN_TAG
import dev.ipf.whitenoise.android.ui.conversation.messages.MessageFullScreenView
import dev.ipf.whitenoise.android.ui.theme.WhiteNoiseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-mdpi")
class ReaderComposerControlsScreenshotTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var recorder: VoiceRecordingController? = null

    @After
    fun releaseRecorder() {
        recorder?.release()
        scope.cancel()
    }

    @Test fun lightReaderControls() = capture("reader_composer_controls_light")

    @Test fun darkReaderControls() = capture("reader_composer_controls_dark", dark = true)

    @Test fun largeRtlReaderControls() = capture("reader_composer_controls_large_rtl", rtl = true)

    private fun capture(name: String, dark: Boolean = false, rtl: Boolean = false) {
        val host = ConversationReaderComposer()
        val open = mutableStateOf(false)
        val dictation = ConversationDictationController(
            platform = Platform,
            readDraft = { _, _ -> ConversationDictationDraftSnapshot(TextFieldValue(""), 0L) },
            writeDraft = { _, _, _, _ -> 0L },
            disclosureAccepted = { true },
            markDisclosureAccepted = {},
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        recorder = VoiceRecordingController(
            context = context,
            outputDirectory = context.cacheDir,
            scope = scope,
            onPermissionRequest = { true },
            onRecordingComplete = { _, _ -> },
            onError = {},
        )
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                WhiteNoiseTheme(darkTheme = dark, fontScale = if (rtl) 2f else 1f) {
                    Column {
                        host.Host {
                            Composer(dictation)
                        }
                        if (open.value) {
                            Reader(host, open)
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle { open.value = true }
        composeRule.onNodeWithContentDescription("Dictate text").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hold to record voice message").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add attachment").assertIsDisplayed()
        composeRule.onNodeWithTag(MESSAGE_FULL_SCREEN_TAG).captureRoboImage("src/test/snapshots/$name.png")
        composeRule.onNodeWithContentDescription("Dictate text").performClick()
        composeRule.runOnIdle { assertTrue(dictation.state is ConversationDictationState.Starting) }
        composeRule.onNodeWithContentDescription("Hold to record voice message").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Cancel").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(dictation.state is ConversationDictationState.Idle) }
    }

    @Composable
    private fun Composer(dictation: ConversationDictationController) {
        ComposerBar(
            replyingTo = null,
            messageTextCopy = MessageTextCopy.Default,
            onCancelReply = {},
            onSend = { _, _ -> },
            onPickFromGallery = {},
            onCaptureFromCamera = {},
            onPickDocument = {},
            onShareLocation = {},
            onShareUser = {},
            onShareContact = {},
            onCreatePoll = {},
            voiceRecordingController = recorder,
            dictationController = dictation,
            dictationAccountRef = "reader-account",
            dictationGroupIdHex = "reader-group",
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun Reader(host: ConversationReaderComposer, open: MutableState<Boolean>) {
        MessageFullScreenView(
            senderDisplayName = "Alice",
            senderSeed = "alice",
            senderAvatarUrl = null,
            body = "Read this longer message while composing a reply.\n\n".repeat(12),
            bodyMarkdownDocument = null,
            mentionDisplayName = null,
            onNostrProfileTap = null,
            onCopyMarkdownLink = {},
            timeText = "12:34",
            showStatus = false,
            status = MessageStatus.Sent,
            canReply = true,
            canReact = true,
            canDelete = false,
            onReply = {},
            onReact = {},
            onCopy = {},
            onDelete = {},
            onDismiss = { open.value = false },
            bottomBar = { host.Reader(onAfterSend = {}, onDismiss = { open.value = false }) },
        )
    }

    private object Platform : ConversationDictationPlatform {
        override fun hasRecordAudioPermission() = true
        override fun recognitionAvailable() = true
        override fun createSession(
            listener: ConversationDictationRecognitionListener,
        ): ConversationDictationRecognitionSession =
            object : ConversationDictationRecognitionSession {
                override fun start() = Unit
                override fun stop() = Unit
                override fun cancel() = Unit
                override fun destroy() = Unit
            }
    }
}
