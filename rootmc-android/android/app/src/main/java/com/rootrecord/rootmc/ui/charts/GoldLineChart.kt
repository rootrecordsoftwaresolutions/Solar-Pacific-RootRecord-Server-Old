package com.rootrecord.rootmc.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val ChartGold = Color(0xFFF5B942)
private val ChartGoldFill = Color(0x29F5B942)
private val ChartHolder = Color(0xFF7EB8DA)
private val ChartGrid = Color(0x0DFFFFFF)
private val ChartIn = Color(0xFF5DD39E)
private val ChartOut = Color(0xFFEF5B5B)

@Composable
fun GoldLineChart(
    labels: List<String>,
    values: List<Double>,
    modifier: Modifier = Modifier,
    heightDp: Int = 200,
    fill: Boolean = true,
    lineColor: Color = ChartGold,
    secondaryValues: List<Double?>? = null,
    secondaryColor: Color = ChartHolder,
) {
    if (labels.isEmpty() || values.isEmpty()) return
    val gridColor = ChartGrid
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
    ) {
        val padL = 8f
        val padR = 8f
        val padT = 12f
        val padB = 20f
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        val all = buildList {
            addAll(values)
            secondaryValues?.forEach { v -> if (v != null) add(v) }
        }
        val minV = all.minOrNull() ?: 0.0
        val maxV = all.maxOrNull() ?: 1.0
        val range = (maxV - minV).coerceAtLeast(0.01)
        fun yFor(v: Double): Float = padT + h - ((v - minV) / range * h).toFloat()
        fun xFor(i: Int): Float = padL + (if (values.size <= 1) 0f else i.toFloat() / (values.size - 1) * w)

        for (i in 0..4) {
            val y = padT + h * i / 4f
            drawLine(gridColor, Offset(padL, y), Offset(padL + w, y), strokeWidth = 1f)
        }

        if (fill) {
            val path = Path()
            values.forEachIndexed { i, v ->
                val pt = Offset(xFor(i), yFor(v))
                if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            path.lineTo(xFor(values.lastIndex), padT + h)
            path.lineTo(xFor(0), padT + h)
            path.close()
            drawPath(path, ChartGoldFill)
        }

        var last: Offset? = null
        values.forEachIndexed { i, v ->
            val pt = Offset(xFor(i), yFor(v))
            last?.let { drawLine(lineColor, it, pt, strokeWidth = 2.5f) }
            last = pt
        }

        secondaryValues?.let { secondary ->
            var secLast: Offset? = null
            secondary.forEachIndexed { i, v ->
                if (v == null) {
                    secLast = null
                    return@forEachIndexed
                }
                val pt = Offset(xFor(i), yFor(v))
                secLast?.let { drawLine(secondaryColor, it, pt, strokeWidth = 2f, pathEffect = null) }
                secLast = pt
            }
        }
    }
}

@Composable
fun FlowBarChart(
    labels: List<String>,
    inflows: List<Double>,
    outflows: List<Double>,
    modifier: Modifier = Modifier,
    heightDp: Int = 180,
) {
    if (labels.isEmpty()) return
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
    ) {
        val padL = 8f
        val padR = 8f
        val padT = 8f
        val padB = 16f
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        val maxV = (inflows.maxOrNull() ?: 0.0).coerceAtLeast(outflows.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        val groupW = w / labels.size.coerceAtLeast(1)
        val barW = groupW * 0.32f
        labels.forEachIndexed { i, _ ->
            val cx = padL + groupW * i + groupW / 2f
            val inV = inflows.getOrElse(i) { 0.0 }
            val outV = outflows.getOrElse(i) { 0.0 }
            val inH = (inV / maxV * h).toFloat()
            val outH = (outV / maxV * h).toFloat()
            drawRect(
                ChartIn,
                topLeft = Offset(cx - barW - 2f, padT + h - inH),
                size = androidx.compose.ui.geometry.Size(barW, inH),
            )
            drawRect(
                ChartOut,
                topLeft = Offset(cx + 2f, padT + h - outH),
                size = androidx.compose.ui.geometry.Size(barW, outH),
            )
        }
        drawLine(ChartGrid, Offset(padL, padT + h), Offset(padL + w, padT + h), strokeWidth = 1f)
    }
}
