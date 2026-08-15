package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.components.ReceiptDialog
import com.example.ui.components.RevenueChartCard
import com.example.ui.theme.BlueCyan
import com.example.ui.theme.BlueInfo
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GeometricBorderDark
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.RoseContainer
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.SlateNavy
import com.example.ui.viewmodel.CobranzaViewModel
import com.example.util.CurrencyUtils
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
    val allPaymentsHistory by viewModel.allPaymentsHistory.collectAsStateWithLifecycle()
    val todayExpenses by viewModel.todayExpenses.collectAsStateWithLifecycle()
    val allClients by viewModel.allClients.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }

    // History filter states
    var historySearchQuery by remember { mutableStateOf("") }
    var selectedMethodFilter by remember { mutableStateOf("TODOS") }
    var selectedScopeFilter by remember { mutableStateOf("HOY") } // "HOY" or "TODO"

    // Digital receipt viewer
    var selectedReceiptText by remember { mutableStateOf<String?>(null) }

    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val fullDateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())

    val filteredPayments = remember(
        allPaymentsHistory,
        todayPayments,
        selectedScopeFilter,
        historySearchQuery,
        selectedMethodFilter,
        allClients
    ) {
        val baseList = if (selectedScopeFilter == "HOY") todayPayments else allPaymentsHistory
        baseList.filter { p ->
            val client = allClients.find { it.id == p.clientId }
            val matchesName = historySearchQuery.isBlank() ||
                    (client?.name?.contains(historySearchQuery, ignoreCase = true) == true) ||
                    (client?.aliasOrBusiness?.contains(historySearchQuery, ignoreCase = true) == true)

            val matchesMethod = selectedMethodFilter == "TODOS" || p.paymentMethod.equals(selectedMethodFilter, ignoreCase = true)

            matchesName && matchesMethod
        }
    }

    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Top Executive Header
            item {
                Surface(
                    color = Slate900,
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
                                    text = "Cierre diario y control de recaudo",
                                    fontSize = 13.sp,
                                    color = Slate400
                                )
                            }

                            // Share Report Action
                            Button(
                                onClick = {
                                    val reportText = viewModel.generateDailyReportText()
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, reportText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Enviar Reporte Diario"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GeometricBorderDark)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldMint)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reporte", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Net Cash in Hand Card
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F261E)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldLight.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("EFECTIVO NETO EN MANO", fontSize = 12.sp, color = EmeraldMint, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = EmeraldDark.copy(alpha = 0.8f)
                                    ) {
                                        Text(
                                            text = "En Ruta",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldMint,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = CurrencyUtils.format(summary.netCashInHand),
                                    fontSize = 34.sp,
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
                                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = EmeraldMint, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Total Cobrado", fontSize = 11.sp, color = Slate400)
                                        }
                                        Text(CurrencyUtils.format(summary.totalCollectedToday), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.TrendingDown, contentDescription = null, tint = RoseDanger, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Gastos de Ruta", fontSize = 11.sp, color = Slate400)
                                        }
                                        Text(CurrencyUtils.format(summary.totalExpensesToday), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RoseDanger)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sub Navigation Tabs
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Slate900,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = EmeraldMint,
                            height = 3.dp
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gráfica & Resumen", fontSize = 13.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Historial Pagos", fontSize = 13.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalGasStation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gastos", fontSize = 13.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                }
            }

            // TAB 0: REVENUE CHART & RESUMEN
            if (selectedTab == 0) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        RevenueChartCard(
                            payments = todayPayments,
                            totalExpected = summary.totalExpectedToday
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    // Efficiency KPIs
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GeometricBorderDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Métricas de Efectividad de Ruta",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Clientes en Ruta", fontSize = 11.sp, color = Slate400)
                                    Text("${summary.totalClientsCount}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column {
                                    Text("Cobrados", fontSize = 11.sp, color = Slate400)
                                    Text("${summary.collectedClientsCount}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EmeraldMint)
                                }
                                Column {
                                    Text("Pendientes", fontSize = 11.sp, color = Slate400)
                                    Text("${summary.pendingClientsCount}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RoseDanger)
                                }
                                Column {
                                    Text("Efectividad", fontSize = 11.sp, color = Slate400)
                                    Text("${String.format("%.1f", summary.progressPercentage)}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BlueCyan)
                                }
                            }
                        }
                    }
                }
            }

            // TAB 1: HISTORIAL DE PAGOS
            if (selectedTab == 1) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Scope selection (Hoy vs Todo)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedScopeFilter == "HOY",
                                onClick = { selectedScopeFilter = "HOY" },
                                label = { Text("Hoy (${todayPayments.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldDark,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = selectedScopeFilter == "TODO",
                                onClick = { selectedScopeFilter = "TODO" },
                                label = { Text("Todo el Historial (${allPaymentsHistory.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0369A1),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Search
                        OutlinedTextField(
                            value = historySearchQuery,
                            onValueChange = { historySearchQuery = it },
                            placeholder = { Text("Buscar abono por cliente...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Slate900,
                                unfocusedContainerColor = Slate900,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = EmeraldMint,
                                unfocusedBorderColor = GeometricBorderDark
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Method Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedMethodFilter == "TODOS",
                                onClick = { selectedMethodFilter = "TODOS" },
                                label = { Text("Todos") }
                            )
                            FilterChip(
                                selected = selectedMethodFilter == "EFECTIVO",
                                onClick = { selectedMethodFilter = "EFECTIVO" },
                                label = { Text("💵 Efectivo") }
                            )
                            FilterChip(
                                selected = selectedMethodFilter == "TRANSFERENCIA",
                                onClick = { selectedMethodFilter = "TRANSFERENCIA" },
                                label = { Text("📱 Transferencia") }
                            )
                        }
                    }
                }

                if (filteredPayments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No se encontraron pagos con los filtros seleccionados.",
                                fontSize = 13.sp,
                                color = Slate400
                            )
                        }
                    }
                } else {
                    items(filteredPayments, key = { it.id }) { payment ->
                        val client = allClients.find { it.id == payment.clientId }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Slate900,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GeometricBorderDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                if (payment.paymentMethod == "EFECTIVO") EmeraldDark else Color(0xFF0369A1),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (payment.paymentMethod == "EFECTIVO") Icons.Default.Payment else Icons.Default.ReceiptLong,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = client?.name ?: "Cliente #${payment.clientId}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Cuota #${payment.quotaNumber} • ${payment.paymentMethod}",
                                            fontSize = 12.sp,
                                            color = EmeraldMint,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = fullDateFormat.format(Date(payment.date)),
                                            fontSize = 11.sp,
                                            color = Slate400
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "+${CurrencyUtils.format(payment.amount)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = EmeraldMint
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Receipt trigger
                                    IconButton(
                                        onClick = {
                                            val rText = """
                                                🧾 COMPROBANTE DE PAGO
                                                --------------------------------
                                                Cliente: ${client?.name ?: "N/A"}
                                                Dirección: ${client?.address ?: "Barranquilla"}
                                                Fecha: ${fullDateFormat.format(Date(payment.date))}
                                                --------------------------------
                                                Cuota: #${payment.quotaNumber}
                                                Monto Cobrado: ${CurrencyUtils.format(payment.amount)}
                                                Forma de Pago: ${payment.paymentMethod}
                                                --------------------------------
                                                ¡Cobro Verificado!
                                            """.trimIndent()
                                            selectedReceiptText = rText
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ReceiptLong,
                                            contentDescription = "Ver Recibo",
                                            tint = BlueCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 2: GASTOS DE RUTA
            if (selectedTab == 2) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Gastos de Ruta Registrados", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

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

                if (todayExpenses.isEmpty()) {
                    item {
                        Text(
                            "No se han registrado gastos de combustible o viáticos hoy.",
                            fontSize = 13.sp,
                            color = Slate400,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    items(todayExpenses, key = { it.id }) { expense ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Slate900,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GeometricBorderDark),
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
                                            .background(RoseDanger.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = RoseDanger, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(expense.concept, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                        Text(expense.category, fontSize = 11.sp, color = Slate400)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("-${CurrencyUtils.format(expense.amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RoseDanger)
                                    IconButton(onClick = { viewModel.deleteExpense(expense) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Slate400, modifier = Modifier.size(18.dp))
                                    }
                                }
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

    selectedReceiptText?.let { receipt ->
        ReceiptDialog(
            receiptText = receipt,
            onDismiss = { selectedReceiptText = null }
        )
    }
}
