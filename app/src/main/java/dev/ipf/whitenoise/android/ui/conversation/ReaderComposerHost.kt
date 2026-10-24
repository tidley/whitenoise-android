package dev.ipf.whitenoise.android.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import dev.ipf.whitenoise.android.ui.common.WhiteNoiseScaffold

internal data class ReaderComposerCallbacks(
    val onAfterSend: () -> Unit,
    val onDismiss: () -> Unit,
)

internal typealias ReaderAwareComposer = @Composable (ReaderComposerCallbacks?) -> Unit

/** Owns only the visible composer location; drafts, attachments and audio stay with the conversation. */
@Stable
internal class ReaderComposerHost(
    val content: State<ReaderAwareComposer>,
) {
    private var readerOwner by mutableStateOf<Any?>(null)
    val hasReader: Boolean
        get() = readerOwner != null

    fun open(owner: Any) {
        readerOwner = owner
    }

    fun close(owner: Any) {
        if (readerOwner === owner) readerOwner = null
    }

    fun owns(owner: Any): Boolean = readerOwner === owner
}

internal val LocalReaderComposerHost = staticCompositionLocalOf<ReaderComposerHost?> { null }
internal const val CONVERSATION_COMPOSER_SLOT_TAG = "conversation.composer_slot"

/** Supplies the same fully configured composer to either the conversation or its Read more dialog. */
@Composable
@Suppress("FunctionNaming", "LongParameterList")
internal fun ReaderComposerScaffold(
    ownerKey: Any?,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    topNoticeSlot: Boolean = true,
    topBar: @Composable () -> Unit = {},
    bottomBar: ReaderAwareComposer,
    content: @Composable (PaddingValues) -> Unit,
) {
    val latestComposer = rememberUpdatedState(bottomBar)
    val host = remember(ownerKey) { ReaderComposerHost(latestComposer) }
    var restingHeightPx by remember(ownerKey) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalReaderComposerHost provides host) {
        WhiteNoiseScaffold(
            modifier = modifier,
            contentWindowInsets = contentWindowInsets,
            topNoticeSlot = topNoticeSlot,
            topBar = topBar,
            bottomBar = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .testTag(CONVERSATION_COMPOSER_SLOT_TAG)
                        .onSizeChanged { if (!host.hasReader) restingHeightPx = it.height },
                ) {
                    if (host.hasReader) {
                        // Shrinking the transcript could evict the lazy row that owns the open reader.
                        Spacer(Modifier.height(with(density) { restingHeightPx.toDp() }))
                    } else {
                        bottomBar(null)
                    }
                }
            },
            content = content,
        )
    }
}

/** A reader session hides the underlying input before mounting its fully wired replacement. */
@Composable
@Suppress("FunctionNaming")
internal fun SharedReaderComposer(
    host: ReaderComposerHost,
    onAfterSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val owner = remember(host) { Any() }
    DisposableEffect(host, owner) {
        host.open(owner)
        onDispose { host.close(owner) }
    }
    if (host.owns(owner)) host.content.value(ReaderComposerCallbacks(onAfterSend, onDismiss))
}
