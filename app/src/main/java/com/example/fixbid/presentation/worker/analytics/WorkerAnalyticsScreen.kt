package com.example.fixbid.presentation.worker.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.core.components.AppHeader
import com.example.fixbid.core.utils.formatCurrencyVnd
import com.example.fixbid.domain.usecase.worker.CategoryStat
import com.example.fixbid.domain.usecase.worker.MonthlyEarning
import com.example.fixbid.domain.usecase.worker.WorkerAnalytics
import com.example.fixbid.ui.theme.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerAnalyticsScreen(
    onBackClick: () -> Unit = {},
    onReviewsClick: () -> Unit = {},
    viewModel: WorkerAnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppHeader(title = "Thống kê & thu nhập", onBackClick = onBackClick)

        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            uiState.errorMessage != null && uiState.analytics == null -> Box(
                Modifier.fillMaxSize().padding(24.dp), Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.load() }) { Text("Thử lại") }
                }
            }
            else -> {
                val data = uiState.analytics ?: return@Column
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = WindowInsets.navigationBars.add(
                            WindowInsets(left = 16.dp, top = 16.dp, right = 16.dp, bottom = 16.dp)
                        ).asPaddingValues(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { EarningsHeroCard(data) }
                        item { MonthComparisonCard(data) }
                        item { KpiGrid(data, onReviewsClick = onReviewsClick) }
                        item { EarningsChartCard(data.monthlySeries) }
                        if (data.categoryBreakdown.isNotEmpty()) {
                            item { CategoryBreakdownCard(data.categoryBreakdown) }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

// ─── Hero earnings card ───────────────────────────────────────────────────────

/**
 * Headline KPI card. Shows total earnings prominently with a 6-month sparkline
 * to give an at-a-glance shape of the worker's earnings trajectory, plus a
 * compact MoM growth chip. Uses `primaryContainer` to match the dashboard's
 * earnings entry-point and the rest of the app's hero-card convention.
 */
@Composable
private fun EarningsHeroCard(data: WorkerAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Tổng thu nhập",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                Spacer(Modifier.weight(1f))
                data.monthOverMonthGrowth?.let { GrowthChip(growth = it) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = formatCurrencyVnd(data.totalEarnings),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Từ ${data.acceptanceJobs} thanh toán đã nhận",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )

            // Sparkline of the same 6-month series — small, embedded right in
            // the hero so the worker reads "amount + shape of trend" together.
            if (data.monthlySeries.any { it.amount > 0 }) {
                Spacer(Modifier.height(16.dp))
                Sparkline(
                    series = data.monthlySeries,
                    lineColor = MaterialTheme.colorScheme.primary,
                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )
            }
        }
    }
}

@Composable
private fun GrowthChip(growth: Double) {
    val up = growth >= 0
    val statusColors = StatusColorsTheme.current
    val tint = if (up) statusColors.positive else statusColors.negative
    Surface(
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (up) Icons.AutoMirrored.Outlined.TrendingUp
                else Icons.AutoMirrored.Outlined.TrendingDown,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "${if (up) "+" else "-"}${"%.0f".format(abs(growth))}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}

/**
 * Mini line chart embedded in the hero card. Plots a smooth path through the
 * normalized monthly amounts and fills underneath. No axis or grid — it's
 * purely a shape preview, not an analytic chart.
 */
@Composable
private fun Sparkline(
    series: List<MonthlyEarning>,
    lineColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty()) return
    val maxAmount = series.maxOf { it.amount }.coerceAtLeast(1.0)
    Canvas(modifier = modifier) {
        if (series.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val stepX = w / (series.size - 1)
        val points = series.mapIndexed { i, m ->
            val frac = (m.amount / maxAmount).toFloat().coerceIn(0f, 1f)
            Offset(i * stepX, h - frac * h)
        }

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
            }
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }
        drawPath(path = fillPath, color = fillColor)
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        // End-point dot so the user's eye lands on "where you are now".
        drawCircle(
            color = lineColor,
            radius = 4.dp.toPx(),
            center = points.last()
        )
    }
}

// ─── Month comparison ─────────────────────────────────────────────────────────

/**
 * Side-by-side comparison of this month vs last month with a normalized
 * progress bar. More immediate than a raw percentage delta — the bar widths
 * give a visual sense of how the months stack up.
 */
@Composable
private fun MonthComparisonCard(data: WorkerAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "So sánh theo tháng",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))
            val maxAmount = maxOf(data.thisMonthEarnings, data.lastMonthEarnings).coerceAtLeast(1.0)
            ComparisonRow(
                label = "Tháng này",
                amount = data.thisMonthEarnings,
                fraction = (data.thisMonthEarnings / maxAmount).toFloat(),
                color = MaterialTheme.colorScheme.primary,
                emphasised = true
            )
            Spacer(Modifier.height(12.dp))
            ComparisonRow(
                label = "Tháng trước",
                amount = data.lastMonthEarnings,
                fraction = (data.lastMonthEarnings / maxAmount).toFloat(),
                color = MaterialTheme.colorScheme.secondary,
                emphasised = false
            )
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    amount: Double,
    fraction: Float,
    color: Color,
    emphasised: Boolean
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "comparison_$label"
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatCurrencyVnd(amount),
                style = if (emphasised) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.titleSmall,
                fontWeight = if (emphasised) FontWeight.Bold else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

// ─── KPI grid ─────────────────────────────────────────────────────────────────

@Composable
private fun KpiGrid(data: WorkerAnalytics, onReviewsClick: () -> Unit = {}) {
    val sc = StatusColorsTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.CheckCircle,
                tint = sc.completed,
                value = "${data.completedJobs}",
                label = "Việc hoàn thành"
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Payments,
                tint = MaterialTheme.colorScheme.primary,
                value = formatCurrencyVnd(data.averagePerJob),
                label = "Trung bình/việc"
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Star,
                tint = sc.rating,
                value = if (data.averageRating > 0) "%.1f".format(data.averageRating) else "—",
                label = "${data.totalReviews} đánh giá",
                onClick = onReviewsClick
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.WorkHistory,
                tint = MaterialTheme.colorScheme.tertiary,
                value = "${data.acceptanceJobs}",
                label = "Lượt nhận việc"
            )
        }
    }
}

@Composable
private fun KpiCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    tint: Color,
    value: String,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Earnings bar chart ───────────────────────────────────────────────────────

/**
 * 6-month earnings bar chart with:
 *  • Y-axis grid lines + compact value labels (0 / mid / max).
 *  • Dashed horizontal line at the 6-month average — instantly tells the worker
 *    which months were above / below their typical run.
 *  • The current month rendered in `primary`, prior months in `secondary` so
 *    the focus reads at a glance even before scanning labels.
 *  • Value label only above the tallest bar (avoids label crowding).
 */
@Composable
private fun EarningsChartCard(series: List<MonthlyEarning>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Thu nhập 6 tháng",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val avg = series.map { it.amount }.average().takeIf { !it.isNaN() } ?: 0.0
                    if (avg > 0) {
                        Text(
                            text = "Trung bình: ${formatCurrencyVnd(avg)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Legend
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(color = MaterialTheme.colorScheme.primary, label = "Hiện tại")
                    Spacer(Modifier.width(10.dp))
                    LegendDot(color = MaterialTheme.colorScheme.secondary, label = "Trước")
                }
            }

            Spacer(Modifier.height(18.dp))
            BarChart(series = series)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BarChart(series: List<MonthlyEarning>) {
    val maxAmount = series.maxOfOrNull { it.amount }?.takeIf { it > 0 } ?: 1.0
    val avg = series.map { it.amount }.average().takeIf { !it.isNaN() } ?: 0.0
    val maxIndex = series.indices.maxByOrNull { series[it].amount } ?: -1
    val currentIndex = series.lastIndex // last item == current month per use case

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val avgColor = MaterialTheme.colorScheme.tertiary

    Box {
        // Y-axis grid (3 horizontal dashed lines) + average baseline drawn
        // behind bars on a Canvas so we don't fight Compose layout.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val w = size.width
            val h = size.height
            val gridStroke = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            // 4 horizontal grid lines (top, 1/3, 2/3, bottom)
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = gridStroke.width,
                    pathEffect = gridStroke.pathEffect
                )
            }
            // Average baseline
            if (avg > 0 && maxAmount > 0) {
                val avgY = h - (avg / maxAmount).toFloat() * h
                drawLine(
                    color = avgColor,
                    start = Offset(0f, avgY),
                    end = Offset(w, avgY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                )
            }
        }

        // Bars overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            series.forEachIndexed { index, bar ->
                val fraction = (bar.amount / maxAmount).toFloat().coerceIn(0f, 1f)
                val animated by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(600),
                    label = "bar_${bar.label}"
                )
                val barColor = when {
                    bar.amount <= 0 -> MaterialTheme.colorScheme.surfaceVariant
                    index == currentIndex -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (index == maxIndex && bar.amount > 0) {
                        Text(
                            text = compactCurrency(bar.amount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(animated.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        series.forEachIndexed { index, bar ->
            val isCurrent = index == series.lastIndex
            Text(
                text = bar.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─── Category breakdown (donut + legend) ──────────────────────────────────────

/**
 * Category distribution shown as a donut chart with a legend list. Replaces
 * the old horizontal-bar list which only conveyed counts — the donut adds
 * "share of total" intuition that bars alone struggle to show.
 *
 * Slice colours rotate through `primary`, `tertiary`, `secondary` and the
 * `secondaryContainer`/`tertiaryContainer` tints so the chart stays inside the
 * app palette and doesn't introduce new semantic colours.
 */
@Composable
private fun CategoryBreakdownCard(stats: List<CategoryStat>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Phân bổ theo dịch vụ",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))

            val total = stats.sumOf { it.count }.coerceAtLeast(1)
            val palette = donutPalette()
            // Top-N + "Khác" so the donut never gets too busy.
            val visible = stats.take(palette.size - 1)
            val rest = stats.drop(palette.size - 1)
            val slices = buildList {
                visible.forEachIndexed { i, s ->
                    add(DonutSlice(s.category.displayName, s.count, palette[i]))
                }
                if (rest.isNotEmpty()) {
                    add(DonutSlice("Khác", rest.sumOf { it.count }, palette.last()))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Donut(
                    slices = slices,
                    total = total,
                    modifier = Modifier.size(120.dp)
                )
                Spacer(Modifier.width(20.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    slices.forEach { slice ->
                        DonutLegendRow(
                            slice = slice,
                            total = total
                        )
                    }
                }
            }
        }
    }
}

private data class DonutSlice(
    val label: String,
    val count: Int,
    val color: Color
)

@Composable
private fun donutPalette(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.secondary,
    MaterialTheme.colorScheme.primaryContainer,
    MaterialTheme.colorScheme.tertiaryContainer,
    MaterialTheme.colorScheme.outline
)

@Composable
private fun Donut(
    slices: List<DonutSlice>,
    total: Int,
    modifier: Modifier = Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringWidth = 14.dp.toPx()
            val sumF = total.coerceAtLeast(1).toFloat()
            var start = -90f // start at 12 o'clock
            val gapDeg = if (slices.size > 1) 2f else 0f
            slices.forEach { slice ->
                val sweep = (slice.count.toFloat() / sumF) * 360f - gapDeg
                if (sweep > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(ringWidth / 2, ringWidth / 2),
                        size = Size(size.width - ringWidth, size.height - ringWidth),
                        style = Stroke(width = ringWidth, cap = StrokeCap.Butt)
                    )
                }
                start += (slice.count.toFloat() / sumF) * 360f
            }
        }
        // Center label — total job count
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$total",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = onSurface
            )
            Text(
                text = "việc",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DonutLegendRow(slice: DonutSlice, total: Int) {
    val pct = if (total > 0) (slice.count * 100f / total) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(slice.color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = slice.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${slice.count} • ${"%.0f".format(pct)}%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Compact VND label for chart bars: 1.2tr / 850k. */
private fun compactCurrency(amount: Double): String = when {
    amount >= 1_000_000 -> "%.1ftr".format(amount / 1_000_000)
    amount >= 1_000 -> "${(amount / 1_000).toInt()}k"
    else -> amount.toInt().toString()
}
