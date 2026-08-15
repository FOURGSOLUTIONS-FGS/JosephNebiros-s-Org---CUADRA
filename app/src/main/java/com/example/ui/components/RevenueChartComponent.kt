package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PaymentEntity
import com.example.ui.theme.BlueCyan
import com.example.ui.theme.BlueInfo
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GeometricBorderDark
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SlateNavy
import com.example.util.CurrencyUtils
import java.util.Calendar

data class HourlyStat(val hourLabel: String, val amount: Double, val count: Int)

@Composable
fun RevenueChartCard(
    payments: List<PaymentEntity>,
    totalExpected: Double,
    modifier: Modifier = Modifier
) {
    val totalCollected = payments.sumOf { it.amount }
    val cashAmount = payments.filter { it.paymentMethod == "EFECTIVO" }.sumOf { it.amount }
    val transferAmount = payments.filter { it.paymentMethod == "TRANSFERENCIA" }.sumOf { it.amount }

    // Hourly distribution
    val hourlyData = remember(payments) {
        val hourMap = mutableMapOf<Int, Double>()
        for (h in 7..18) hourMap[h] = 0.0

        val cal = Calendar.getInstance()
        payments.forEach { p ->
            cal.timeInMillis = p.date
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val bucket = h.coerceIn(7, 18)
            hourMap[bucket] = (hourMap[bucket] ?: 0.0) + p.amount
        }

        listOf(8, 10, 12, 14, 16, 18).map { h ->
            val sum = (hourMap[h - 1] ?: 0.0) + (hourMap[h] ?: 0.0)
            val label = when (h) {
                8 -> "8am"
                10 -> "10am"
                12 -> "12pm"
                14 -> "2pm"
                16 -> "4pm"
                18 -> "6pm"
                else -> "${h}h"
            }
            HourlyStat(label, sum, 1)
        }
    }

    val maxHourly = (hourlyData.maxOfOrNull { it.amount } ?: 1.0).coerceAtLeast(100.0)

    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(payments.size, totalCollected) {
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = androidx.compose.foundation.BorderStroke(1.dp, GeometricBorderDark),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.linearGradient(listOf(EmeraldGreen, BlueCyan)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.QueryStats,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Gráfica de Recaudo en Vivo",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Rendimiento y distribución horaria",
                            fontSize = 12.sp,
                            color = Slate400
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldDark.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldLight.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(EmeraldMint, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${payments.size} Cobros",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldMint
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dual Stats: Total Collected vs Progress Gauge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TOTAL RECAUDADO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate400,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyUtils.format(totalCollected),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = EmeraldMint
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    val pct = if (totalExpected > 0) ((totalCollected / totalExpected) * 100).coerceIn(0.0, 100.0) else 0.0
                    Text(
                        text = "Meta Diaria: ${CurrencyUtils.format(totalExpected)} (${String.format("%.1f", pct)}%)",
                        fontSize = 12.sp,
                        color = Slate400
                    )
                }

                // Mini Circular Gauge Canvas
                Box(
                    modifier = Modifier.size(68.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val sweepProgress = if (totalExpected > 0) (totalCollected / totalExpected).toFloat().coerceIn(0f, 1f) else 0f
                    val animatedSweep = sweepProgress * progressAnim.value

                    Canvas(modifier = Modifier.size(64.dp)) {
                        val strokeWidth = 7.dp.toPx()
                        // Track
                        drawCircle(
                            color = Color(0xFF1E293B),
                            radius = (size.minDimension - strokeWidth) / 2,
                            style = Stroke(width = strokeWidth)
                        )
                        // Progress Arc
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(EmeraldLight, BlueCyan, EmeraldMint)
                            ),
                            startAngle = -90f,
                            sweepAngle = animatedSweep * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    val percentInt = if (totalExpected > 0) ((totalCollected / totalExpected) * 100).toInt() else 0
                    Text(
                        text = "$percentInt%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Hourly Bar Chart Canvas
            Text(
                text = "Recaudo por Franja Horaria",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate400
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFF070B14), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                    val width = size.width
                    val height = size.height
                    val barWidth = 24.dp.toPx()
                    val count = hourlyData.size
                    val spacing = (width - (barWidth * count)) / (count + 1)

                    // Draw grid lines
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(0f, height / 2),
                        end = Offset(width, height / 2),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(0f, height),
                        end = Offset(width, height),
                        strokeWidth = 1.dp.toPx()
                    )

                    hourlyData.forEachIndexed { i, stat ->
                        val x = spacing + i * (barWidth + spacing)
                        val barHeight = if (maxHourly > 0) {
                            ((stat.amount / maxHourly).toFloat() * height * progressAnim.value).coerceAtLeast(4.dp.toPx())
                        } else 4.dp.toPx()

                        val y = height - barHeight

                        // Bar background
                        drawRoundRect(
                            brush = if (stat.amount > 0) {
                                Brush.verticalGradient(
                                    listOf(BlueCyan, EmeraldLight),
                                    startY = y,
                                    endY = height
                                )
                            } else {
                                Brush.verticalGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
                            },
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }

                // X-Axis Labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(top = 84.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    hourlyData.forEach { stat ->
                        Text(
                            text = stat.hourLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate400
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Payment Methods Breakdown (Efectivo vs Transferencia)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Efectivo Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF111827),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(EmeraldDark, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = EmeraldMint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Efectivo", fontSize = 11.sp, color = Slate400)
                            Text(
                                text = CurrencyUtils.format(cashAmount),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Transferencia Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF111827),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF0369A1), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = Color(0xFF7DD3FC),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Transferencias", fontSize = 11.sp, color = Slate400)
                            Text(
                                text = CurrencyUtils.format(transferAmount),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
