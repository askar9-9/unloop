package app.still.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object UnloopIcons {
    val Day = icon("Day", "M3 18.5H21", "M7 18.5A5 5 0 0 1 17 18.5", "M12 6V8.5", "M5.6 9.6L7.4 11.4", "M18.4 9.6L16.6 11.4")
    val LoopBreak = icon("LoopBreak", "M16.82 6.25A7.5 7.5 0 1 1 9.43 4.95", "M15.54 7.79L18.11 4.72")
    val Tone = icon("Tone", "M12 3.5C15 7.1 17.5 10.4 17.5 13.5A5.5 5.5 0 0 1 6.5 13.5C6.5 10.4 9 7.1 12 3.5Z", "M4.5 19.5L19.5 4.5")
    val Settings = icon(
        "Settings",
        "M18.92 10.02L21.48 10.50L21.48 13.50L18.92 13.98A7.2 7.2 0 0 1 18.30 15.49L19.77 17.64L17.64 19.77L15.49 18.30" +
            "A7.2 7.2 0 0 1 13.98 18.92L13.50 21.48L10.50 21.48L10.02 18.92A7.2 7.2 0 0 1 8.51 18.30L6.36 19.77L4.23 17.64" +
            "L5.70 15.49A7.2 7.2 0 0 1 5.08 13.98L2.52 13.50L2.52 10.50L5.08 10.02A7.2 7.2 0 0 1 5.70 8.51L4.23 6.36L6.36 4.23" +
            "L8.51 5.70A7.2 7.2 0 0 1 10.02 5.08L10.50 2.52L13.50 2.52L13.98 5.08A7.2 7.2 0 0 1 15.49 5.70L17.64 4.23" +
            "L19.77 6.36L18.30 8.51A7.2 7.2 0 0 1 18.92 10.02Z",
        circle(12f, 12f, 3f),
    )
    val Info = icon("Info", circle(12f, 12f, 9f), "M12 11V16.5", filled = listOf(circle(12f, 7.75f, 0.9f)))
    val Check = icon("Check", "M5 12.5L9.5 17L19 7.5")

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}A$r $r 0 1 0 ${cx + r} ${cy}A$r $r 0 1 0 ${cx - r} ${cy}Z"

    private fun icon(name: String, vararg strokes: String, filled: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            strokes.forEach {
                addPath(
                    addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
            filled.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()
}
