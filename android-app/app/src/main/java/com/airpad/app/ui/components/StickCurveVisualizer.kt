package com.airpad.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airpad.app.data.models.StickCurveType
import com.airpad.app.ui.theme.PrimaryBlue
import com.airpad.app.ui.theme.TextMuted
import kotlin.math.pow

@Composable
fun StickCurveVisualizer(
    curveType: StickCurveType = StickCurveType.LINEAR,
    exponent: Float = 1.8f,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    showSubpixelGrid: Boolean = false
) {
    val curvePath = remember(curveType, exponent) {
        generateCurvePath(curveType, exponent)
    }

    val sizePx = with(LocalDensity.current) { size.toPx() }

    Canvas(modifier = modifier.size(size)) {
        drawGrid(this, sizePx, showSubpixelGrid)

        drawPath(
            path = curvePath,
            color = PrimaryBlue,
            style = Stroke(width = 3f, cap = StrokeCap.Round),
            alpha = 0.9f
        )

        drawLine(
            start = Offset(0f, sizePx),
            end = Offset(sizePx, 0f),
            color = TextMuted.copy(alpha = 0.3f),
            strokeWidth = 1f
        )

        drawCircle(color = Color(0x4CAF50), center = Offset(sizePx, 0f), radius = 4f)
        drawCircle(color = PrimaryBlue, center = Offset(0f, 0f), radius = 4f)
        drawLine(
            start = Offset(0f, sizePx),
            end = Offset(sizePx, 0f),
            color = Color(0xFFF44336),
            strokeWidth = 2f
        )
    }
}

@Composable
fun StickResponseGraph(
    input: Float,
    curveType: StickCurveType = StickCurveType.LINEAR,
    exponent: Float = 1.8f,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp
) {
    val output by animateFloatAsState(
        targetValue = applyCurve(input.coerceIn(0f, 1f), curveType, exponent),
        animationSpec = tween(durationMillis = 300)
    )

    Column(modifier = modifier) {
        Row(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(
                text = "Input",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Output",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = this.size.width
                val h = this.size.height

                drawRect(color = Color(0xFF18181B))

                val path = Path().apply {
                    moveTo(0f, h)
                    for (i in 0..100) {
                        val t = i.toFloat() / 100f
                        val outY = applyCurve(t, curveType, exponent)
                        lineTo(t * width, h - (outY * h))
                    }
                    lineTo(width, h)
                    close()
                }

                drawPath(
                    path = path,
                    color = PrimaryBlue.copy(alpha = 0.15f),
                    alpha = 0.5f
                )

                drawPath(
                    path = generateCurvePath(curveType, exponent).let { p ->
                        val w = width
                        val hh = h
                        Path().apply {
                            moveTo(0f, hh)
                            for (i in 0..100) {
                                val t = i.toFloat() / 100f
                                val outY = applyCurve(t, curveType, exponent)
                                lineTo(t * w, hh - (outY * hh))
                            }
                        }
                    },
                    color = PrimaryBlue,
                    style = Stroke(width = 2f)
                )

                val markerY = h - (output * h)
                drawCircle(
                    color = Color(0xFF10B981),
                    radius = 6f,
                    center = Offset(input * width, markerY)
                )
            }
        }
    }
}

private fun generateCurvePath(curveType: StickCurveType, exponent: Float): Path {
    val path = Path()
    val steps = 100f

    path.moveTo(0f, 0f)
    for (i in 1..steps.toInt()) {
        val t = i / steps
        val output = applyCurve(t, curveType, exponent)
        path.lineTo(t * 100f, output * 100f)
    }
    return path
}

private fun applyCurve(input: Float, curveType: StickCurveType, exponent: Float): Float {
    return when (curveType) {
        StickCurveType.LINEAR -> input
        StickCurveType.EXPONENTIAL -> input.pow(exponent)
        StickCurveType.LOGARITHMIC -> 1f - (1f - input).pow(exponent)
        StickCurveType.WIDE_CENTER -> {
            if (input < 0.5f) {
                0.5f * (2f * input).pow(exponent)
            } else {
                1f - 0.5f * (2f * (1f - input)).pow(exponent)
            }
        }
        StickCurveType.DIGITAL_STEP -> if (input >= 0.35f) 1f else 0f
    }
}

private fun drawGrid(canvas: androidx.compose.ui.graphics.drawscope.DrawScope, size: Float, showSubpixel: Boolean) {
    val gridAlpha = if (showSubpixel) 0.15f else 0.08f
    val count = 10
    for (i in 0..count) {
        val pos = (size / count) * i
        canvas.drawLine(
            start = Offset(pos, 0f),
            end = Offset(pos, size),
            color = TextMuted,
            alpha = gridAlpha,
            strokeWidth = 1f
        )
        canvas.drawLine(
            start = Offset(0f, pos),
            end = Offset(size, pos),
            color = TextMuted,
            alpha = gridAlpha,
            strokeWidth = 1f
        )
    }
}
