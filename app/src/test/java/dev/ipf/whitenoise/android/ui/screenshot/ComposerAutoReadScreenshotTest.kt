package dev.ipf.whitenoise.android.ui.screenshot

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.ipf.whitenoise.android.ui.conversation.composer.ComposerPill
import dev.ipf.whitenoise.android.ui.theme.WhiteNoiseTheme
import dev.ipf.whitenoise.android.ui.common.accountActionColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w320dp-h640dp-mdpi")
class ComposerAutoReadScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun offLight() = capture("composer_auto_read_off_light", checked = false)

    @Test
    fun onDark() = capture("composer_auto_read_on_dark", checked = true, dark = true)

    @Test
    fun onLargeRtl() = capture("composer_auto_read_on_large_rtl", checked = true, rtl = true)

    private fun capture(
        name: String,
        checked: Boolean,
        dark: Boolean = false,
        rtl: Boolean = false,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(1f, if (rtl) 2f else 1f),
            ) {
                WhiteNoiseTheme(darkTheme = dark) {
                    Surface {
                        ComposerPill(
                            textFieldValue = TextFieldValue(""),
                            composerFocus = FocusRequester(),
                            emojiPickerOpen = false,
                            onValueChange = {},
                            onEmojiPickerToggle = {},
                            onAttachmentsToggle = {},
                            attachmentSheetOpen = false,
                            onPickFromGallery = {},
                            onPickDocument = null,
                            onDictation = {},
                            autoReadEnabled = checked,
                            actionColors = accountActionColors(null),
                            modifier = Modifier.width(320.dp).testTag("composer-auto-read"),
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag("composer-auto-read").captureRoboImage("src/test/snapshots/$name.png")
    }
}
