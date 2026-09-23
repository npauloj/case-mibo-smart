package io.github.npauloj.mibosmart.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * What the app knows about a thing right now, as a shape rather than only as a sentence.
 *
 * The four tones are not severities, and reading them as a scale from good to bad is the mistake this
 * type exists to prevent. [Settled] and [Live] are both fine; [Waiting] is not a lesser failure but a
 * different category — the app does not know yet, which on a lock is the honest and most important
 * thing it can say (ADR-021).
 */
internal enum class StateTone {
    /** A fact the device confirmed. Drawn in ink: "Fechada" and "Aberta" are equally legitimate. */
    Settled,

    /** On, live, connected — the brand's own colour, used where it means "working". */
    Live,

    /** Sent and unconfirmed, reconnecting, about to expire: what the app does not know. */
    Waiting,

    /** It failed, and the cause is named beside this. */
    Failed,
}

@Composable
private fun StateTone.color(): Color = when (this) {
    StateTone.Settled -> MaterialTheme.colorScheme.outlineVariant
    StateTone.Live -> MaterialTheme.colorScheme.tertiary
    StateTone.Waiting -> LocalAppColors.current.waiting
    StateTone.Failed -> MaterialTheme.colorScheme.error
}

/**
 * A thin coloured edge running down the left of whatever it wraps.
 *
 * It repeats, on purpose, what the text beside it already says. That redundancy is the point: it
 * survives being glanced at, it works before the sentence is read, and it gives six screens one
 * grammar so a state on the lock reads the same way as a state on the camera.
 *
 * The rail is decoration to a screen reader — [clearAndSetSemantics] with nothing in it — because the
 * sentence it echoes is already announced, and hearing the colour twice helps nobody.
 */
@Composable
internal fun StateRail(
    tone: StateTone,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    // Drawn, not laid out. The first version was a Row of [rail, content] with
    // `height(IntrinsicSize.Min)`, which works until the content contains a fixed-aspect box: the
    // video surface made the intrinsic measurement degenerate, the frame overflowed across the
    // camera's name and the rail collapsed to a stub. `drawBehind` asks the layout nothing — it
    // paints the edge of whatever height the Column ends up with, for any content.
    val color = tone.color()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = color,
                    size = Size(width = RailWidth.toPx(), height = size.height),
                    cornerRadius = CornerRadius(RailWidth.toPx() / 2),
                )
            }
            // No `clearAndSetSemantics` here: an earlier draft put it on this Column and would have
            // hidden every screen's content from a screen reader. The rail is drawn into the
            // background, which is already invisible to semantics — nothing to silence.
            .padding(start = RailWidth + 14.dp),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

private val RailWidth = 3.dp
/**
 * The rail's other form: a filled block for a sentence that interrupts, rather than an edge beside a
 * screen that continues.
 *
 * Used where the prototype used a banner — "Comando enviado, esperando a fechadura confirmar", "Token
 * expira em breve". A notice carries its own background because it has to be found, not just noticed.
 */
@Composable
internal fun StateNotice(
    tone: StateTone,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val scheme = MaterialTheme.colorScheme
    val background = when (tone) {
        StateTone.Settled -> scheme.surfaceVariant
        StateTone.Live -> scheme.tertiaryContainer
        StateTone.Waiting -> colors.waitingContainer
        StateTone.Failed -> scheme.errorContainer
    }
    val foreground = when (tone) {
        StateTone.Settled -> scheme.onSurfaceVariant
        StateTone.Live -> scheme.onTertiaryContainer
        StateTone.Waiting -> colors.onWaitingContainer
        StateTone.Failed -> scheme.onErrorContainer
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = foreground,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}
