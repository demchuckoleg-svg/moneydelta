package com.moneydelta.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneydelta.app.domain.MoneyFormatter
import com.moneydelta.app.domain.PeriodBarGroup
import com.moneydelta.app.domain.StatisticsSnapshot
import com.moneydelta.app.domain.StatsRange
import com.moneydelta.app.domain.StatsRangePreset
import com.moneydelta.app.domain.TrendPoint
import com.moneydelta.app.ui.StatsUiState
import com.moneydelta.app.ui.theme.LocalSemanticColors
import com.moneydelta.app.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    uiState: StatsUiState?,
    onSelectPreset: (StatsRangePreset) -> Unit,
    onSelectCustomRange: (LocalDate, LocalDate) -> Unit,
    onSelectSender: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDateRangeDialog by remember { mutableStateOf(false) }

    if (uiState == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val snapshot = uiState.snapshot
    val symbol = uiState.currencySymbol

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "stats_header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Statistics",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = buildString {
                        append(snapshot.periodSubtitle)
                        if (!uiState.selectedSender.isNullOrBlank()) {
                            append(" · ")
                            append(uiState.selectedSender)
                        }
                        append(" · ")
                        append("${snapshot.transactionCount} tx")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "range_chips") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = listOf(
                    StatsRangePreset.THIS_CYCLE,
                    StatsRangePreset.THREE_MONTHS,
                    StatsRangePreset.SIX_MONTHS,
                    StatsRangePreset.TWELVE_MONTHS,
                    StatsRangePreset.ALL
                )
                items(presets, key = { it.name }) { preset ->
                    val selected = (uiState.selectedRange as? StatsRange.Preset)?.preset == preset
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPreset(preset) },
                        label = { Text(preset.label) }
                    )
                }
                item(key = "CUSTOM") {
                    val isCustom = uiState.selectedRange is StatsRange.Custom
                    FilterChip(
                        selected = isCustom,
                        onClick = { showDateRangeDialog = true },
                        label = { Text(StatsRangePreset.CUSTOM.label) }
                    )
                }
            }
        }

        if (uiState.availableSenders.size > 1) {
            item(key = "sender_chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "all_senders") {
                        FilterChip(
                            selected = uiState.selectedSender == null,
                            onClick = { onSelectSender(null) },
                            label = { Text("All sources") }
                        )
                    }
                    items(uiState.availableSenders, key = { it.lowercase() }) { sender ->
                        val selected = uiState.selectedSender.equals(sender, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectSender(if (selected) null else sender) },
                            label = { Text(sender) }
                        )
                    }
                }
            }
        }

        item(key = "totals_and_averages") {
            TotalsAndAveragesSection(
                snapshot = snapshot,
                currencySymbol = symbol
            )
        }

        item(key = "net_delta_line_chart") {
            NetDeltaLineChartCard(
                snapshot = snapshot,
                currencySymbol = symbol
            )
        }

        item(key = "income_expense_bar_chart") {
            IncomeExpenseBarChartCard(
                snapshot = snapshot,
                currencySymbol = symbol
            )
        }
    }

    if (showDateRangeDialog) {
        CustomDateRangePickerDialog(
            initialRange = uiState.selectedRange as? StatsRange.Custom,
            onDismiss = { showDateRangeDialog = false },
            onConfirm = { start, end ->
                onSelectCustomRange(start, end)
                showDateRangeDialog = false
            }
        )
    }
}

@Composable
private fun TotalsAndAveragesSection(
    snapshot: StatisticsSnapshot,
    currencySymbol: String
) {
    val semantic = LocalSemanticColors.current
    val netColor = when {
        snapshot.netDeltaCents > 0L -> semantic.positive
        snapshot.netDeltaCents < 0L -> semantic.negative
        else -> MaterialTheme.colorScheme.onSurface
    }
    val avgNetColor = when {
        snapshot.avgMonthlyNetCents > 0L -> semantic.positive
        snapshot.avgMonthlyNetCents < 0L -> semantic.negative
        else -> MaterialTheme.colorScheme.onSurface
    }
    val savingsText = snapshot.savingsRatePercent?.let { pct ->
        if (pct > 0) "+$pct%" else "$pct%"
    } ?: "—"
    val savingsColor = when {
        snapshot.savingsRatePercent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        snapshot.savingsRatePercent > 0 -> semantic.positive
        snapshot.savingsRatePercent < 0 -> semantic.negative
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Period Net Delta",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = MoneyFormatter.formatSigned(snapshot.netDeltaCents, currencySymbol),
                    style = MaterialTheme.typography.headlineMedium.tabular().copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = netColor
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "In +${MoneyFormatter.formatUnsigned(snapshot.totalIncomeCents, currencySymbol)}",
                        style = MaterialTheme.typography.bodyMedium.tabular().copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = semantic.positive
                    )
                    Text(
                        text = "Out -${MoneyFormatter.formatUnsigned(snapshot.totalExpenseCents, currencySymbol)}",
                        style = MaterialTheme.typography.bodyMedium.tabular().copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = semantic.negative
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMetricTile(
                title = "Avg / Cycle Net",
                value = MoneyFormatter.formatSigned(snapshot.avgMonthlyNetCents, currencySymbol),
                subtitle = "${snapshot.activeCycleCount} active ${if (snapshot.activeCycleCount == 1) "cycle" else "cycles"}",
                valueColor = avgNetColor,
                modifier = Modifier.weight(1f)
            )
            StatMetricTile(
                title = "Savings Rate",
                value = savingsText,
                subtitle = "Net / Income",
                valueColor = savingsColor,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMetricTile(
                title = "Avg / Cycle In",
                value = "+${MoneyFormatter.formatUnsigned(snapshot.avgMonthlyIncomeCents, currencySymbol)}",
                subtitle = "Per active cycle",
                valueColor = semantic.positive,
                modifier = Modifier.weight(1f)
            )
            StatMetricTile(
                title = "Avg / Cycle Out",
                value = "-${MoneyFormatter.formatUnsigned(snapshot.avgMonthlyExpenseCents, currencySymbol)}",
                subtitle = "Per active cycle",
                valueColor = semantic.negative,
                modifier = Modifier.weight(1f)
            )
        }

        StatMetricTile(
            title = "Daily Spend Average",
            value = MoneyFormatter.formatUnsigned(snapshot.dailySpendCents, currencySymbol),
            subtitle = "Averaged across ${snapshot.elapsedDays} elapsed ${if (snapshot.elapsedDays == 1) "day" else "days"}",
            valueColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatMetricTile(
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.tabular().copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NetDeltaLineChartCard(
    snapshot: StatisticsSnapshot,
    currencySymbol: String
) {
    val points = snapshot.deltaTrendPoints
    var selectedIndex by remember(points) {
        mutableIntStateOf((points.lastIndex).coerceAtLeast(0))
    }
    val selectedPoint: TrendPoint? = points.getOrNull(selectedIndex)
    val semantic = LocalSemanticColors.current
    val lineColor = MaterialTheme.colorScheme.primary
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "line_chart_progress"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (snapshot.isCumulativeDailyTrend) {
                    "Cumulative Net Delta"
                } else {
                    "Net Delta Trend"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            if (selectedPoint != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val valueColor = when {
                    selectedPoint.valueCents > 0L -> semantic.positive
                    selectedPoint.valueCents < 0L -> semantic.negative
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedPoint.detailTitle}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = MoneyFormatter.formatSigned(selectedPoint.valueCents, currencySymbol),
                        style = MaterialTheme.typography.bodyMedium.tabular().copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = valueColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (points.isEmpty()) {
                Text(
                    text = "No data for this range",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val rawMin = points.minOf { it.valueCents }
                val rawMax = points.maxOf { it.valueCents }
                val minVal = min(0L, rawMin)
                val maxVal = max(0L, rawMax)
                val span = (maxVal - minVal).coerceAtLeast(1L)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Max ${MoneyFormatter.formatSigned(rawMax, currencySymbol)}",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Min ${MoneyFormatter.formatSigned(rawMin, currencySymbol)}",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                if (points.size == 1) {
                                    selectedIndex = 0
                                } else {
                                    val fraction = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val idx = (fraction * (points.size - 1) + 0.5f).toInt()
                                        .coerceIn(0, points.lastIndex)
                                    selectedIndex = idx
                                }
                            }
                        }
                ) {
                    val padH = 10.dp.toPx()
                    val padV = 12.dp.toPx()
                    val chartW = (size.width - padH * 2f).coerceAtLeast(1f)
                    val chartH = (size.height - padV * 2f).coerceAtLeast(1f)

                    fun yFor(valueCents: Long): Float {
                        val norm = (valueCents - minVal).toFloat() / span.toFloat()
                        return padV + chartH * (1f - norm)
                    }

                    // Zero baseline
                    val zeroY = yFor(0L)
                    drawLine(
                        color = zeroLineColor,
                        start = Offset(padH, zeroY),
                        end = Offset(size.width - padH, zeroY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )

                    val coords = points.mapIndexed { idx, point ->
                        val x = if (points.size == 1) {
                            padH + chartW / 2f
                        } else {
                            padH + (idx.toFloat() / (points.size - 1).toFloat()) * chartW
                        }
                        Offset(x, yFor(point.valueCents))
                    }

                    if (coords.size >= 2) {
                        val path = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 1 until coords.size) {
                                lineTo(coords[i].x, coords[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = lineColor.copy(alpha = animProgress),
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Draw dots (sparser if > 16 points, always draw selected point)
                    val showAllDots = coords.size <= 16
                    coords.forEachIndexed { idx, pt ->
                        val isSelected = idx == selectedIndex
                        if (showAllDots || isSelected) {
                            val pointColor = when {
                                points[idx].valueCents > 0L -> semantic.positive
                                points[idx].valueCents < 0L -> semantic.negative
                                else -> lineColor
                            }
                            drawCircle(
                                color = pointColor,
                                radius = if (isSelected) 5.5.dp.toPx() else 3.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                ChartXAxisLabels(labels = points.map { it.label })
            }
        }
    }
}

@Composable
private fun IncomeExpenseBarChartCard(
    snapshot: StatisticsSnapshot,
    currencySymbol: String
) {
    val groups = snapshot.barGroups
    var selectedIndex by remember(groups) {
        mutableIntStateOf((groups.lastIndex).coerceAtLeast(0))
    }
    val selectedGroup: PeriodBarGroup? = groups.getOrNull(selectedIndex)
    val semantic = LocalSemanticColors.current
    val highlightBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (snapshot.isCumulativeDailyTrend) {
                        "Weekly Income vs. Expenses"
                    } else {
                        "Income vs. Expenses"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(color = semantic.positive, label = "In")
                    LegendDot(color = semantic.negative, label = "Out")
                }
            }

            if (selectedGroup != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${selectedGroup.detailTitle} · In +${MoneyFormatter.formatUnsigned(selectedGroup.incomeCents, currencySymbol)} · Out -${MoneyFormatter.formatUnsigned(selectedGroup.expenseCents, currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (groups.isEmpty()) {
                Text(
                    text = "No data for this range",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxVal = groups.maxOf { max(it.incomeCents, it.expenseCents) }.coerceAtLeast(1L)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .pointerInput(groups) {
                            detectTapGestures { offset ->
                                val slotWidth = size.width.toFloat() / groups.size.coerceAtLeast(1)
                                val idx = (offset.x / slotWidth).toInt().coerceIn(0, groups.lastIndex)
                                selectedIndex = idx
                            }
                        }
                ) {
                    val bottomPad = 4.dp.toPx()
                    val topPad = 8.dp.toPx()
                    val chartH = (size.height - bottomPad - topPad).coerceAtLeast(1f)
                    val slotW = size.width / groups.size.toFloat()
                    val barW = min(14.dp.toPx(), (slotW * 0.30f).coerceAtLeast(3.dp.toPx()))
                    val gapBetweenBars = min(4.dp.toPx(), barW * 0.4f)
                    val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())

                    groups.forEachIndexed { idx, group ->
                        val slotLeft = idx * slotW
                        val slotCenter = slotLeft + slotW / 2f

                        if (idx == selectedIndex) {
                            drawRoundRect(
                                color = highlightBg,
                                topLeft = Offset(slotLeft + 2.dp.toPx(), 0f),
                                size = Size((slotW - 4.dp.toPx()).coerceAtLeast(1f), size.height - bottomPad),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }

                        val incomeH = (group.incomeCents.toFloat() / maxVal.toFloat()) * chartH
                        val expenseH = (group.expenseCents.toFloat() / maxVal.toFloat()) * chartH

                        val incomeLeft = slotCenter - gapBetweenBars / 2f - barW
                        val expenseLeft = slotCenter + gapBetweenBars / 2f
                        val baseBottom = size.height - bottomPad

                        if (incomeH > 0f) {
                            val drawH = max(incomeH, 2.dp.toPx())
                            drawRoundRect(
                                color = semantic.positive,
                                topLeft = Offset(incomeLeft, baseBottom - drawH),
                                size = Size(barW, drawH),
                                cornerRadius = corner
                            )
                        }
                        if (expenseH > 0f) {
                            val drawH = max(expenseH, 2.dp.toPx())
                            drawRoundRect(
                                color = semantic.negative,
                                topLeft = Offset(expenseLeft, baseBottom - drawH),
                                size = Size(barW, drawH),
                                cornerRadius = corner
                            )
                        }
                    }

                    drawLine(
                        color = baselineColor,
                        start = Offset(0f, size.height - bottomPad),
                        end = Offset(size.width, size.height - bottomPad),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                ChartXAxisLabels(
                    labels = groups.map { it.label },
                    centerAll = groups.size <= 6
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChartXAxisLabels(
    labels: List<String>,
    centerAll: Boolean = false
) {
    if (labels.isEmpty()) return

    val displayed = remember(labels) {
        if (labels.size <= 6) {
            labels
        } else {
            listOf(labels.first(), labels[labels.size / 2], labels.last())
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        displayed.forEachIndexed { idx, text ->
            val align = when {
                centerAll || displayed.size == 1 -> TextAlign.Center
                idx == 0 -> TextAlign.Start
                idx == displayed.lastIndex -> TextAlign.End
                else -> TextAlign.Center
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDateRangePickerDialog(
    initialRange: StatsRange.Custom?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val initialStartMillis = remember(initialRange) {
        initialRange?.startDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    }
    val initialEndMillis = remember(initialRange) {
        initialRange?.endDateInclusive?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    }

    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = {
                    val startMillis = state.selectedStartDateMillis ?: return@TextButton
                    val endMillis = state.selectedEndDateMillis ?: return@TextButton
                    val startDate = Instant.ofEpochMilli(startMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    val endDate = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(startDate, endDate)
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .padding(horizontal = 8.dp)
        )
    }
}
