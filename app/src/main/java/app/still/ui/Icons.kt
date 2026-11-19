package app.still.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object UnloopIcons {
    // Bottom bar: outline when idle, bolder with a filled detail when selected.
    val Pulse = icon("Pulse", PULSE)
    val PulseActive = icon("PulseActive", PULSE, width = 2.5f)
    val EyeActive = icon("EyeActive", EYE, width = 2.5f, filled = listOf(circle(12f, 12f, 3.4f)))
    val Eye = icon("Eye", EYE, circle(12f, 12f, 3f))
    val LoopBreak = icon("LoopBreak", LOOP, filled = listOf(circle(18.42f, 5.58f, 1.6f)))
    val LoopBreakActive = icon("LoopBreakActive", LOOP, width = 2.5f, filled = listOf(circle(18.42f, 5.58f, 2.1f)))
    val Settings = icon(
        "Settings",
        "M18.92 10.02L21.48 10.50L21.48 13.50L18.92 13.98A7.2 7.2 0 0 1 18.30 15.49L19.77 17.64L17.64 19.77L15.49 18.30" +
            "A7.2 7.2 0 0 1 13.98 18.92L13.50 21.48L10.50 21.48L10.02 18.92A7.2 7.2 0 0 1 8.51 18.30L6.36 19.77L4.23 17.64" +
            "L5.70 15.49A7.2 7.2 0 0 1 5.08 13.98L2.52 13.50L2.52 10.50L5.08 10.02A7.2 7.2 0 0 1 5.70 8.51L4.23 6.36L6.36 4.23" +
            "L8.51 5.70A7.2 7.2 0 0 1 10.02 5.08L10.50 2.52L13.50 2.52L13.98 5.08A7.2 7.2 0 0 1 15.49 5.70L17.64 4.23" +
            "L19.77 6.36L18.30 8.51A7.2 7.2 0 0 1 18.92 10.02Z",
        circle(12f, 12f, 3f),
    )
    val Back = icon("Back", "M14.5 6L8.5 12L14.5 18")
    val Forward = icon("Forward", "M9.5 6L15.5 12L9.5 18")
    val Info = icon("Info", circle(12f, 12f, 9f), "M12 11V16.5", filled = listOf(circle(12f, 7.75f, 0.9f)))

    private const val PULSE = "M3 12H7L9.5 6L14.5 18L17 12H21"
    private const val LOOP = "M17.75 11.52A6.5 6.5 0 1 1 12.48 6.25"
    private const val EYE = "M2.5 12C5 7.5 8.3 5.5 12 5.5S19 7.5 21.5 12C19 16.5 15.7 18.5 12 18.5S5 16.5 2.5 12Z"

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}A$r $r 0 1 0 ${cx + r} ${cy}A$r $r 0 1 0 ${cx - r} ${cy}Z"

    private fun icon(name: String, vararg strokes: String, width: Float = 1.75f, filled: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            strokes.forEach {
                addPath(
                    addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
            filled.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()
}
