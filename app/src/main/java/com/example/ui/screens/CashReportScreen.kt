package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ExpenseEntity
import com.example.data.model.PaymentEntity
import com.example.ui.components.AddExpenseDialog
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.SlateNavy
import com.example.ui.viewmodel.CobranzaViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CashReportScreen(
    viewModel: CobranzaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val summary by viewModel.dailySummary.collectAsStateWithLifecycle()
    val todayPayments by viewModel.todayPayments.collectAsStateWithLifecycle()
    val todayExpenses by viewModel.todayExpenses.collectAsStateWithLifecycle()
    val allClients by viewModel.allClients.collectAsStateWithLifecycle()

    var showAddExpenseDialog by remember { mutableStateOf(false) }

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Header
            item {
                Surface(
                    color = SlateNavy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Caja & Liquidación",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Cierre diario de operaciones",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            // Share Report Action
                            OutlinedButton(
                                onClick = {
                                    val reportText = viewModel.generateDailyReportText()
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, reportText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Enviar Reporte Diario"))
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldLight)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reporte", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Net Cash in Hand Card
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = EmeraldDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("EFECTIVO NETO EN MANO", fontSize = 12.sp, color = EmeraldLight, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = com.example.util.CurrencyUtils.format(summary.netCashInHand),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Total Cobrado", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        }
                                        Text(com.example.util.CurrencyUtils.format(summary.totalCollectedToday), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.TrendingDown, contentDescription = null, tint = RoseDanger, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Gastos de Ruta", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        }
                                        Text(com.example.util.CurrencyUtils.format(summary.totalExpensesToday), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RoseDanger)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Gasto Button Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gastos de Ruta del Día", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SlateNavy)

                    Button(
                        onClick = { showAddExpenseDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseDanger),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Gasto", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Expenses List
            if (todayExpenses.isEmpty()) {
                item {
                    Text(
                        "No se han registrado gastos de combustible o viáticos hoy.",
                        fontSize = 13.sp,
                        color = Slate600,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            } else {
                items(todayExpenses, key = { it.id }) { expense ->
                    ElevatedCard(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(RoseContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = RoseDanger, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(expense.concept, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SlateNavy)
                                    Text(expense.category, fontSize = 11.sp, color = Slate600)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("-${com.example.util.CurrencyUtils.format(expense.amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RoseDanger)
                                IconButton(onClick = { viewModel.deleteExpense(expense) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Payments Log Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Historial de Cobros Recientes Hoy (${todayPayments.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateNavy,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (todayPayments.isEmpty()) {
                item {
                    Text(
                        "Aún no hay abonos registrados el día de hoy.",
                        fontSize = 13.sp,
                        color = Slate600,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                items(todayPayments, key = { it.id }) { payment ->
                    val client = allClients.find { it.id == payment.clientId }
                    ElevatedCard(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(EmeraldContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Payment, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(client?.name ?: "Cliente #${payment.clientId}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SlateNavy)
                                    Text("Cuota #${payment.quotaNumber} • ${payment.paymentMethod}", fontSize = 11.sp, color = Slate600)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("+${com.example.util.CurrencyUtils.format(payment.amount)}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = EmeraldDark)
                                Text(timeFormat.format(Date(payment.date)), fontSize = 11.sp, color = Slate600)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            onDismiss = { showAddExpenseDialog = false },
            onConfirm = { concept, amount, category ->
                viewModel.addExpense(concept, amount, category)
            }
        )
    }
}
