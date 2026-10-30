package com.zambudio.registropesos.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shader.verticalGradient
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.ui.GymCard
import com.zambudio.registropesos.core.ui.Pill
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// Gráficas con Vico 2.x. El eje X es el índice del punto (sesión o semana) y se etiqueta con su fecha,
// así las sesiones quedan equiespaciadas aunque haya huecos en el calendario.

private val ChartDateFormat = DateTimeFormatter.ofPattern("d/M", Locale.forLanguageTag("es-ES"))
private val AxisNumberFormat = DecimalFormat("#,##0.#", DecimalFormatSymbols(Locale.forLanguageTag("es-ES")))
private val ChartHeight = 200.dp
private val PointSize = 10.dp
private val PointStroke = 2.dp
private val ColumnThickness = 14.dp
private const val ColumnCornerPercent = 40
private const val AreaAlpha = 0.28f

fun chartDateLabel(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    ChartDateFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

enum class ChartKind { LINE, COLUMN }

/** Tarjeta con título, último valor y la gráfica. [labels] y [values] tienen la misma longitud (> 0). */
@Composable
fun ChartCard(
    title: String,
    labels: List<String>,
    values: List<Double>,
    latestText: String,
    kind: ChartKind,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.progress_chart_description, title, values.size, latestText)
    GymCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Pill(
                text = stringResource(R.string.progress_chart_latest, latestText),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(12.dp))
        VicoChart(
            labels = labels,
            values = values,
            kind = kind,
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .semantics { contentDescription = description },
        )
    }
}

@Composable
private fun VicoChart(labels: List<String>, values: List<Double>, kind: ChartKind, modifier: Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values, kind) {
        val xs = values.indices.toList()
        modelProducer.runTransaction {
            when (kind) {
                ChartKind.LINE -> lineSeries { series(xs, values) }
                ChartKind.COLUMN -> columnSeries { series(xs, values) }
            }
        }
    }
    val bottomFormatter = remember(labels) {
        // Vico no admite etiquetas vacías: un espacio para posiciones sin fecha.
        CartesianValueFormatter { _, x, _ -> labels.getOrNull(x.roundToInt()) ?: " " }
    }
    val startFormatter = remember { CartesianValueFormatter.decimal(AxisNumberFormat) }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        // Serie en el naranja primario: línea con degradado debajo o columnas con la parte de arriba redondeada.
        val color = MaterialTheme.colorScheme.primary
        val layer = when (kind) {
            ChartKind.LINE -> {
                val line = LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(color)),
                    areaFill = LineCartesianLayer.AreaFill.single(
                        fill(
                            ShaderProvider.verticalGradient(arrayOf(color.copy(alpha = AreaAlpha), Color.Transparent)),
                        ),
                    ),
                    pointProvider = LineCartesianLayer.PointProvider.single(
                        LineCartesianLayer.point(
                            rememberShapeComponent(
                                fill = fill(color),
                                shape = CorneredShape.Pill,
                                strokeFill = fill(MaterialTheme.colorScheme.surfaceContainerLow),
                                strokeThickness = PointStroke,
                            ),
                            PointSize,
                        ),
                    ),
                )
                rememberLineCartesianLayer(lineProvider = LineCartesianLayer.LineProvider.series(line))
            }
            ChartKind.COLUMN -> rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(
                        fill = fill(color),
                        thickness = ColumnThickness,
                        shape = CorneredShape.rounded(ColumnCornerPercent, ColumnCornerPercent, 0, 0),
                    ),
                ),
            )
        }
        CartesianChartHost(
            chart = rememberCartesianChart(
                layer,
                startAxis = VerticalAxis.rememberStart(valueFormatter = startFormatter),
                // Sin guías verticales: con las horizontales basta para leer el valor y la gráfica respira.
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter, guideline = null),
            ),
            modelProducer = modelProducer,
            modifier = modifier,
            scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
        )
    }
}
