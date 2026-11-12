package dev.ipf.whitenoise.android.ui.conversation

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.test.core.app.ApplicationProvider
import dev.ipf.whitenoise.android.R
import dev.ipf.whitenoise.android.core.MessageTextCopy
import dev.ipf.whitenoise.android.state.timelineAppMessage
import dev.ipf.whitenoise.android.ui.conversation.composer.ComposerBar
import dev.ipf.whitenoise.android.ui.conversation.composer.ComposerTextState
import dev.ipf.whitenoise.android.ui.conversation.messages.MessageBubbleSurfaceState
import dev.ipf.whitenoise.android.ui.theme.WhiteNoiseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercise retained callbacks through the production composer, not only the reader-session helper. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w360dp-h780dp-mdpi")
class ReaderPendingComposerSendTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun timelineTextAcceptanceKeepsANewerReaderAndItsPosition() = assertNewReaderRetained(attachments = false)

    @Test
    fun timelineAttachmentReplyAcceptanceKeepsANewerReaderAndItsPosition() = assertNewReaderRetained(attachments = true)

    private fun assertNewReaderRetained(attachments: Boolean) {
        val host = ConversationReaderComposer()
        val surfaces = MessageBubbleSurfaceState()
        val text = ComposerTextState(TextFieldValue("Pending reply"))
        var pending: ((Boolean) -> Unit)? = null
        var reveals = 0
        rule.setContent {
            WhiteNoiseTheme {
                Column(Modifier.fillMaxSize()) {
                    host.Host {
                        ComposerBar(
                            replyingTo = timelineAppMessage("reply-target").takeIf { attachments },
                            messageTextCopy = MessageTextCopy.Default,
                            onCancelReply = {},
                            textState = text,
                            hasPendingAttachments = attachments,
                            onSend = { _, accepted -> pending = { if (it) accepted() } },
                            onSendAttachments = { _, accepted -> pending = accepted },
                            onAfterSend = host.captureComposerSendAccepted { reveals++ },
                        )
                    }
                    if (surfaces.expandedFullView) {
                        Dialog(onDismissRequest = { surfaces.expandedFullView = false }) {
                            Column {
                                Text("Keep this reading position", Modifier.testTag("reader.position"))
                                host.Reader(
                                    onAfterSend = { error("An unrelated send cannot accept this reader") },
                                    onDismiss = { surfaces.expandedFullView = false },
                                )
                            }
                        }
                    }
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        rule.onNodeWithContentDescription(context.getString(R.string.send)).performClick()
        rule.runOnIdle {
            assertNotNull(pending)
            surfaces.openExpandedFullView()
        }
        rule.waitForIdle()
        val reader = rule.runOnIdle { checkNotNull(host.session) }
        rule.runOnIdle { checkNotNull(pending)(true) }
        rule.waitForIdle()
        rule.onNodeWithTag("reader.position").assertIsDisplayed()
        rule.runOnIdle {
            assertTrue(surfaces.expandedFullView)
            assertSame(reader, host.session)
            assertEquals(0, reveals)
            assertEquals("", text.valueState.value.text)
        }
    }
}
