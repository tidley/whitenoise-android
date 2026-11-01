package dev.ipf.whitenoise.android.ui.conversation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.window.Dialog
import dev.ipf.whitenoise.android.ui.theme.WhiteNoiseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-mdpi")
class ConversationReaderComposerTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun staleReaderDisposalCannotDetachItsReplacement() {
        val host = ConversationReaderComposer()
        val first = ConversationReaderComposer.Session({}, {})
        val second = ConversationReaderComposer.Session({}, {})
        host.attach(first)
        host.attach(second)
        host.detach(first)
        assertSame(second, host.session)
        host.detach(second)
        assertEquals(null, host.session)
    }

    @Test
    fun acceptedSendRemainsPinnedToTheReaderThatSubmittedIt() {
        val host = ConversationReaderComposer()
        var firstSends = 0
        var secondSends = 0
        val first = ConversationReaderComposer.Session({ firstSends++ }, {})
        val second = ConversationReaderComposer.Session({ secondSends++ }, {})
        host.attach(first)
        val accepted = requireNotNull(host.session).onAfterSend
        host.attach(second)
        accepted()
        assertEquals(1, firstSends)
        assertEquals(0, secondSends)
        assertSame(second, host.session)
    }

    @Test
    fun movingIntoAndOutOfADialogKeepsExactlyOneComposerAndItsRememberedState() {
        val host = ConversationReaderComposer()
        val open = mutableStateOf(false)
        var remembered: Any? = null
        var disposals = 0
        composeRule.setContent {
            WhiteNoiseTheme {
                Column {
                    host.Host {
                        val identity = remember { Any() }
                        remembered = identity
                        DisposableEffect(identity) { onDispose { disposals++ } }
                        Text("Full composer")
                    }
                    if (open.value) {
                        Dialog(onDismissRequest = { open.value = false }) {
                            host.Reader(onAfterSend = {}, onDismiss = { open.value = false })
                        }
                    }
                }
            }
        }
        composeRule.onAllNodesWithText("Full composer").assertCountEquals(1)
        val original = composeRule.runOnIdle { requireNotNull(remembered) }
        composeRule.runOnIdle { open.value = true }
        composeRule.onAllNodesWithText("Full composer").assertCountEquals(1)
        composeRule.runOnIdle {
            assertSame(original, remembered)
            assertEquals(0, disposals)
        }
        composeRule.runOnIdle { open.value = false }
        composeRule.onAllNodesWithText("Full composer").assertCountEquals(1)
        composeRule.runOnIdle {
            assertSame(original, remembered)
            assertEquals(0, disposals)
            assertEquals(null, host.session)
        }
        composeRule.runOnIdle { open.value = true }
        composeRule.onAllNodesWithText("Full composer").assertCountEquals(1)
        composeRule.runOnIdle {
            assertSame(original, remembered)
            assertNotSame(null, host.session)
        }
    }
}
