package dev.ipf.whitenoise.android.ui.conversation

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import dev.ipf.whitenoise.android.R
import dev.ipf.whitenoise.android.audio.ConversationDictationController
import dev.ipf.whitenoise.android.audio.ConversationDictationDraftSnapshot
import dev.ipf.whitenoise.android.audio.ConversationDictationPlatform
import dev.ipf.whitenoise.android.audio.ConversationDictationRecognitionListener
import dev.ipf.whitenoise.android.audio.ConversationDictationRecognitionSession
import dev.ipf.whitenoise.android.audio.VoiceRecordingController
import dev.ipf.whitenoise.android.core.MessageTextCopy
import dev.ipf.whitenoise.android.state.MessageStatus
import dev.ipf.whitenoise.android.state.timelineAppMessage
import dev.ipf.whitenoise.android.ui.conversation.composer.ComposerBar
import dev.ipf.whitenoise.android.ui.conversation.composer.ComposerTextState
import dev.ipf.whitenoise.android.ui.conversation.messages.MessageFullScreenView
import dev.ipf.whitenoise.android.ui.theme.WhiteNoiseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private typealias RecognitionListener = ConversationDictationRecognitionListener

/** Exercises one real ComposerBar across the activity and full-screen reader windows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-w360dp-h780dp-mdpi")
class ReaderComposerHostTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val textState = ComposerTextState(TextFieldValue(""))
    private var readerOpen by mutableStateOf(false)
    private var editingId by mutableStateOf<String?>(null)
    private var galleryOpens = 0
    private var recorder: VoiceRecordingController? = null

    @After
    fun releaseAudio() {
        recorder?.release()
        scope.cancel()
    }

    @Test
    fun readerKeepsAttachmentsVoiceAndReplyBoundDictationWithOnlyOneComposer() {
        val dictation = render()
        assertControls()
        val restingSlot = composeRule.onNodeWithTag(CONVERSATION_COMPOSER_SLOT_TAG).getUnclippedBoundsInRoot()
        composeRule.runOnIdle { readerOpen = true }
        assertControls()
        val readerSlot = composeRule.onNodeWithTag(CONVERSATION_COMPOSER_SLOT_TAG).getUnclippedBoundsInRoot()
        assertEquals(
            restingSlot.bottom - restingSlot.top,
            readerSlot.bottom - readerSlot.top,
        )
        composeRule.onNodeWithContentDescription("Add attachment").performClick()
        composeRule.onNodeWithText(context.getString(R.string.attachment_photos_videos)).performClick()
        composeRule.runOnIdle { assertEquals(1, galleryOpens) }
        composeRule.onNodeWithContentDescription("Dictate text").performClick()
        composeRule.runOnIdle { assertEquals(REPLY, dictation.state.target?.replyToMessageIdHex) }
    }

    @Test
    fun readerEditsAndAcceptedSendUseTheSameDraftAndReaderSession() {
        render()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Before Read more")
        composeRule.runOnIdle { readerOpen = true }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Edited in reader")
        composeRule.runOnIdle { readerOpen = false }
        composeRule.runOnIdle { assertEquals("Edited in reader", textState.valueState.value.text) }
        composeRule.runOnIdle { readerOpen = true }
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.runOnIdle {
            assertFalse(readerOpen)
            assertEquals("", textState.valueState.value.text)
        }
        assertControls()
    }

    @Test
    fun movingBetweenChatAndReaderPreservesAnUnfinishedMessageEdit() {
        render()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Draft before editing")
        composeRule.runOnIdle { editingId = REPLY }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Unsent message edit")
        composeRule.runOnIdle { readerOpen = true }
        composeRule.onNode(hasSetTextAction()).assertTextContains("Unsent message edit")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Edited further in reader")
        composeRule.runOnIdle { readerOpen = false }
        composeRule.onNode(hasSetTextAction()).assertTextContains("Edited further in reader")
        composeRule.runOnIdle { editingId = null }
        composeRule.onNode(hasSetTextAction()).assertTextContains("Draft before editing")
    }

    @Test
    fun disposingAnOlderReaderCannotReleaseTheNewerReader() {
        val content = mutableStateOf<ReaderAwareComposer>({})
        val host = ReaderComposerHost(content)
        val old = Any()
        val replacement = Any()
        host.open(old)
        host.open(replacement)
        host.close(old)
        assertTrue(host.hasReader)
        assertTrue(host.owns(replacement))
        host.close(replacement)
        assertFalse(host.hasReader)
    }

    private fun assertControls() {
        listOf("Add attachment", "Dictate text", "Hold to record voice message").forEach { label ->
            composeRule.onAllNodesWithContentDescription(label).assertCountEquals(1)
            composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
        }
    }

    private fun render(): ConversationDictationController {
        val dictation =
            ConversationDictationController(
                platform = TestDictationPlatform,
                readDraft = { _, _ -> ConversationDictationDraftSnapshot(textState.valueState.value, 0L) },
                writeDraft = { _, _, _, _ -> 0L },
                disclosureAccepted = { true },
                markDisclosureAccepted = {},
            )
        val voice =
            VoiceRecordingController(
                context = context,
                outputDirectory = context.cacheDir,
                scope = scope,
                onPermissionRequest = { true },
                onRecordingComplete = { _, _ -> },
                onError = {},
            )
        recorder = voice
        composeRule.setContent {
            WhiteNoiseTheme {
                ReaderComposerScaffold(
                    ownerKey = ACCOUNT,
                    topNoticeSlot = false,
                    bottomBar = { reader ->
                        ComposerBar(
                            replyingTo = timelineAppMessage(REPLY),
                            messageTextCopy = MessageTextCopy.Default,
                            onCancelReply = {},
                            onSend = { _, accepted -> accepted() },
                            onAfterSend = { reader?.onAfterSend?.invoke() },
                            initialDraft = TextFieldValue(""),
                            textState = textState,
                            editingMessageId = editingId,
                            editingInitialText = "Original message text",
                            onCancelEdit = { editingId = null },
                            onPickFromGallery = { galleryOpens++ },
                            voiceRecordingController = voice,
                            dictationController = dictation,
                            dictationAccountRef = ACCOUNT,
                            dictationGroupIdHex = GROUP,
                        )
                    },
                ) {
                    Text("Conversation")
                    if (readerOpen) {
                        ExpandedReader()
                    }
                }
            }
        }
        return dictation
    }

    @Composable
    private fun ExpandedReader() {
        val host = checkNotNull(LocalReaderComposerHost.current)
        MessageFullScreenView(
            senderDisplayName = "Test sender",
            senderSeed = "test",
            senderAvatarUrl = null,
            body = "Long message. ".repeat(100),
            bodyMarkdownDocument = null,
            mentionDisplayName = null,
            onNostrProfileTap = null,
            onCopyMarkdownLink = {},
            timeText = "12:00",
            showStatus = false,
            status = MessageStatus.Sent,
            canReply = true,
            canReact = false,
            canDelete = false,
            onReply = {},
            onReact = {},
            onCopy = {},
            onDelete = {},
            onDismiss = { readerOpen = false },
            bottomBar = {
                SharedReaderComposer(
                    host = host,
                    onAfterSend = { readerOpen = false },
                    onDismiss = { readerOpen = false },
                )
            },
        )
    }

    private object TestDictationPlatform : ConversationDictationPlatform {
        override fun hasRecordAudioPermission() = true

        override fun recognitionAvailable() = true

        override fun createSession(listener: RecognitionListener): ConversationDictationRecognitionSession =
            object : ConversationDictationRecognitionSession {
                override fun start() = Unit

                override fun stop() = Unit

                override fun cancel() = Unit

                override fun destroy() = Unit
            }
    }

    private companion object {
        const val ACCOUNT = "reader-test"
        const val GROUP = "reader-group"
        const val REPLY = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    }
}
