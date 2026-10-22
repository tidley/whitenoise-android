package dev.ipf.whitenoise.android.ui.conversation.composer

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ipf.whitenoise.android.R

/** A compact glyph with a full touch target; checked state is also exposed to TalkBack. */
@Composable
internal fun ComposerAutoReadToggle(
    checked: Boolean,
    onClick: () -> Unit,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_volume_up),
            contentDescription = stringResource(R.string.tts_auto_read_title),
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
