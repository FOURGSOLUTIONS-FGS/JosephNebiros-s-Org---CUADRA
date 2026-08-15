package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClientWithActiveLoan
import com.example.ui.components.AddClientDialog
import com.example.ui.components.AddLoanDialog
import com.example.ui.components.ConfirmPaymentPromptDialog
import com.example.ui.components.DidiCollectorHudCard
import com.example.ui.components.GoogleMapRouteView
import com.example.ui.components.MapRouteCanvas
import com.example.ui.components.PaymentDialog
import com.example.ui.components.PaymentSuccessDisplay
import com.example.ui.components.PaymentSuccessModal
import com.example.ui.components.PendingPaymentConfirmation
import com.example.ui.components.PhotoCaptureDialog
import com.example.ui.components.ReceiptDialog
import com.example.ui.components.RemindersDialog
import com.example.data.model.ClientEntity
import com.example.data.model.LoanEntity
import com.example.util.NavigationUtils
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.GeometricAccent
import com.example.ui.theme.GeometricAccentLight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(
    viewModel: CobranzaViewModel,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dailyRouteList by viewModel.dailyRouteList.collectAsStateWithLifecycle()
    val allClients by viewModel.allClients.collectAsStateWithLifecycle()
    val allReminders by viewModel.allReminders.collectAsStateWithLifecycle()
    val isTrackingActive by viewModel.isTrackingActive.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val pointsCount by viewModel.pointsCount.collectAsStateWithLifecycle()
    val summary by viewModel.dailySummary.collectAsStateWithLifecycle()
    val selectedSessionPoints by viewModel.selectedSessionPoints.collectAsStateWithLifecycle()
    val currentBearing by viewModel.currentBearing.collectAsStateWithLifecycle()
    val accuracyMeters by viewModel.accuracyMeters.collectAsStateWithLifecycle()
    val isSyncingRoute by viewModel.isSyncingRoute.collectAsStateWithLifecycle()

    var showMapPreview by remember { mutableStateOf(true) }
    var useGoogleMaps by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf("TODOS") } // TODOS, PENDIENTES, COBRADOS
    var selectedClientForPayment by remember { mutableStateOf<ClientWithActiveLoan?>(null) }
    var pendingConfirmation by remember { mutableStateOf<PendingPaymentConfirmation?>(null) }
    var paymentSuccessInfo by remember { mutableStateOf<PaymentSuccessDisplay?>(null) }
    var showAddClientDialog by remember { mutableStateOf(false) }
    var showAddLoanDialog by remember { mutableStateOf(false) }
    var showReceiptText by remember { mutableStateOf<String?>(null) }
    var selectedClientForPhoto by remember { mutableStateOf<ClientWithActiveLoan?>(null) }
    var selectedClientForReminders by remember { mutableStateOf<ClientWithActiveLoan?>(null) }
    var currentStopIndex by remember { mutableStateOf(0) }

    // Identify current pending target for DiDi HUD
    val pendingStops = remember(dailyRouteList) {
        dailyRouteList.filter { !it.isCollectedToday }
    }
    val currentHudStop = remember(pendingStops, dailyRouteList, currentStopIndex) {
        if (pendingStops.isNotEmpty()) {
            pendingStops[currentStopIndex.coerceIn(0, pendingStops.size - 1)]
        } else {
            dailyRouteList.firstOrNull()
        }
    }

    val filteredList = remember(dailyRouteList, filterType) {
        when (filterType) {
            "PENDIENTES" -> dailyRouteList.filter { !it.isCollectedToday }
            "COBRADOS" -> dailyRouteList.filter { it.isCollectedToday }
            else -> dailyRouteList
        }
    }

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())
    val todayFormatted = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES")).format(Date())

    // Radar pulse animation for active GPS button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_button")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Header Section
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
                                    text = "Ruta de Cobro",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = todayFormatted.replaceFirstChar { it.uppercase() },
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Sync Route from Supabase Button (for offline prep)
                                IconButton(
                                    onClick = {
                                        viewModel.syncRouteFromSupabase { success, msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("sync_route_button")
                                ) {
                                    if (isSyncingRoute) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Sincronizar Ruta Supabase",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // GPS Foreground Tracking Toggle Button
                                Button(
                                    onClick = {
                                        if (isTrackingActive) {
                                            viewModel.stopRouteTracking(context)
                                        } else {
                                            onRequestLocationPermission()
                                            viewModel.startRouteTracking(context)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isTrackingActive) RoseDanger else EmeraldGreen
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.testTag("toggle_tracking_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isTrackingActive) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .alpha(pulseAlpha)
                                                    .background(Color.White, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("GPS En Vivo ($pointsCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Iniciar Ruta", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Daily Progress Bar & Summary Stats
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate800),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Cobrado Hoy", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                        Text(
                                            com.example.util.CurrencyUtils.format(summary.totalCollectedToday),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = EmeraldLight
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Meta Esperada", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                        Text(
                                            com.example.util.CurrencyUtils.format(summary.totalExpectedToday),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                LinearProgressIndicator(
                                    progress = { summary.progressPercentage / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = EmeraldLight,
                                    trackColor = Color(0xFF334155),
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${summary.collectedClientsCount} de ${summary.totalClientsCount} cobrados",
                                        fontSize = 12.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                    Text(
                                        "${String.format(Locale.getDefault(), "%.0f", summary.progressPercentage)}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldLight
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Interactive Map Card Preview
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate800),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("route_map_preview_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { showMapPreview = !showMapPreview }
                                    ) {
                                        Icon(
                                            Icons.Default.Map,
                                            contentDescription = null,
                                            tint = GeometricAccentLight,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (useGoogleMaps) "Google Maps" else "Radar Satelital",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        if (isTrackingActive) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = GeometricAccent,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) {
                                                Text(
                                                    "EN VIVO",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Mode switch button (Google Maps vs Canvas)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (useGoogleMaps) GeometricAccent else Slate700,
                                            modifier = Modifier.clickable { useGoogleMaps = !useGoogleMaps }
                                        ) {
                                            Text(
                                                text = if (useGoogleMaps) "Modo Google Maps" else "Modo Radar",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = { showMapPreview = !showMapPreview },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                if (showMapPreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = "Alternar mapa",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = showMapPreview) {
                                    Column(modifier = Modifier.padding(top = 10.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(260.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                        ) {
                                            if (useGoogleMaps) {
                                                GoogleMapRouteView(
                                                    routePoints = selectedSessionPoints,
                                                    clients = dailyRouteList,
                                                    currentLocation = currentLocation,
                                                    isTrackingActive = isTrackingActive,
                                                    modifier = Modifier.fillMaxSize(),
                                                    onSwitchToRadar = { useGoogleMaps = false },
                                                    onCollectPaymentClick = { clientLoan ->
                                                        selectedClientForPayment = clientLoan
                                                    }
                                                )
                                            } else {
                                                MapRouteCanvas(
                                                    routePoints = selectedSessionPoints,
                                                    clients = dailyRouteList,
                                                    currentLocation = currentLocation,
                                                    isTrackingActive = isTrackingActive,
                                                    bearing = currentBearing,
                                                    accuracy = accuracyMeters,
                                                    onClientMarkerClick = { clientLoan ->
                                                        selectedClientForPayment = clientLoan
                                                    },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Filter Chips Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterType == "TODOS",
                        onClick = { filterType = "TODOS" },
                        label = { Text("Todos (${dailyRouteList.size})") },
                        modifier = Modifier.testTag("filter_all")
                    )
                    FilterChip(
                        selected = filterType == "PENDIENTES",
                        onClick = { filterType = "PENDIENTES" },
                        label = { Text("Pendientes (${dailyRouteList.count { !it.isCollectedToday }})") },
                        modifier = Modifier.testTag("filter_pending")
                    )
                    FilterChip(
                        selected = filterType == "COBRADOS",
                        onClick = { filterType = "COBRADOS" },
                        label = { Text("Cobrados (${dailyRouteList.count { it.isCollectedToday }})") },
                        modifier = Modifier.testTag("filter_collected")
                    )
                }
            }

            // Empty state if no clients in filter
            if (filteredList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (filterType == "PENDIENTES") "¡Excelente! No hay cobros pendientes" else "No hay clientes registrados",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = SlateNavy
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Presiona el botón + para registrar clientes y créditos",
                            fontSize = 13.sp,
                            color = Slate600
                        )
                    }
                }
            }

            // Client Cards
            items(filteredList, key = { it.client.id }) { item ->
                ClientCollectionCard(
                    item = item,
                    currentLocation = currentLocation,
                    currencyFormat = currencyFormat,
                    onCollectClick = { selectedClientForPayment = item },
                    onNavigateClick = {
                        NavigationUtils.openGoogleMapsNavigation(
                            context = context,
                            destinationLat = item.client.latitude,
                            destinationLng = item.client.longitude,
                            destinationAddress = item.client.address,
                            destinationName = item.client.name
                        )
                    },
                    onCallClick = {
                        if (item.client.phone.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.client.phone}"))
                            context.startActivity(intent)
                        }
                    },
                    onWhatsAppClick = {
                        if (item.client.phone.isNotEmpty()) {
                            val cleanNumber = item.client.phone.replace("[^0-9]".toRegex(), "")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber"))
                            context.startActivity(intent)
                        }
                    },
                    onPhotoClick = { selectedClientForPhoto = item },
                    onReminderClick = { selectedClientForReminders = item }
                )
            }
        }

        // DiDi / Rappi Style Heads-Up Navigation Card for current stop
        if (currentHudStop != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 0.dp)
            ) {
                DidiCollectorHudCard(
                    currentClientItem = currentHudStop,
                    currentIndex = if (pendingStops.isNotEmpty()) currentStopIndex + 1 else 1,
                    totalClients = if (pendingStops.isNotEmpty()) pendingStops.size else dailyRouteList.size,
                    collectorLat = currentLocation?.latitude,
                    collectorLng = currentLocation?.longitude,
                    onNextStop = {
                        if (pendingStops.isNotEmpty()) {
                            currentStopIndex = (currentStopIndex + 1) % pendingStops.size
                        }
                    },
                    onCollectClick = { clientItem ->
                        selectedClientForPayment = clientItem
                    },
                    onPhotoClick = { clientItem ->
                        selectedClientForPhoto = clientItem
                    },
                    onReminderClick = { clientItem ->
                        selectedClientForReminders = clientItem
                    }
                )
            }
        }

        // Floating Action Buttons for quick creation
        if (currentHudStop == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = { showAddClientDialog = true },
                    containerColor = EmeraldGreen,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("fab_add_client")
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = "Nuevo Cliente")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nuevo Cliente", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Dialogs
    selectedClientForPayment?.let { item ->
        PaymentDialog(
            item = item,
            onDismiss = { selectedClientForPayment = null },
            onConfirmPayment = { amount, notes, method ->
                item.activeLoan?.let { loan ->
                    pendingConfirmation = PendingPaymentConfirmation(
                        client = item.client,
                        loan = loan,
                        amount = amount,
                        method = method,
                        notes = notes
                    )
                }
                selectedClientForPayment = null
            },
            onShowReceipt = { receiptText -> showReceiptText = receiptText }
        )
    }

    // Step 2: Collector Confirmation Prompt Dialog
    pendingConfirmation?.let { pending ->
        ConfirmPaymentPromptDialog(
            client = pending.client,
            loan = pending.loan,
            amount = pending.amount,
            paymentMethod = pending.method,
            notes = pending.notes,
            onDismiss = { pendingConfirmation = null },
            onConfirmed = {
                viewModel.registerPayment(
                    client = pending.client,
                    loan = pending.loan,
                    amount = pending.amount,
                    notes = pending.notes,
                    paymentMethod = pending.method
                )
                val receiptText = viewModel.generatePaymentReceiptText(pending.client, pending.loan, pending.amount)
                val newBal = (pending.loan.remainingBalance - pending.amount).coerceAtLeast(0.0)
                val nextQ = pending.loan.paidQuotas + 1

                paymentSuccessInfo = PaymentSuccessDisplay(
                    clientName = pending.client.name,
                    amount = pending.amount,
                    quotaNumber = nextQ,
                    remainingBalance = newBal,
                    receiptText = receiptText
                )
                pendingConfirmation = null
            }
        )
    }

    // Step 3: Success Confirmation Modal with WhatsApp & Receipt
    paymentSuccessInfo?.let { success ->
        PaymentSuccessModal(
            clientName = success.clientName,
            amount = success.amount,
            quotaNumber = success.quotaNumber,
            remainingBalance = success.remainingBalance,
            receiptText = success.receiptText,
            onDismiss = { paymentSuccessInfo = null }
        )
    }

    // Photo Capture Dialog
    selectedClientForPhoto?.let { item ->
        PhotoCaptureDialog(
            title = "Foto Fachada / Evidencia",
            clientName = item.client.name,
            initialPhotoUri = item.client.photoUri,
            onPhotoSaved = { path ->
                viewModel.updateClientPhoto(item.client.id, path)
            },
            onDismiss = { selectedClientForPhoto = null }
        )
    }

    // Reminders Dialog
    selectedClientForReminders?.let { item ->
        val clientReminders = allReminders.filter { it.clientId == item.client.id }
        RemindersDialog(
            clientId = item.client.id,
            clientName = item.client.name,
            reminders = clientReminders,
            onAddReminder = { cId, cName, title, dueTime, notes, prio ->
                viewModel.createReminder(cId, cName, title, dueTime, notes, prio)
            },
            onToggleCompleted = { rId, completed ->
                viewModel.setReminderCompleted(rId, completed)
            },
            onDeleteReminder = { reminder ->
                viewModel.deleteReminder(reminder)
            },
            onDismiss = { selectedClientForReminders = null }
        )
    }

    if (showAddClientDialog) {
        AddClientDialog(
            currentGpsLocation = currentLocation,
            onDismiss = { showAddClientDialog = false },
            onConfirm = { name, alias, phone, address, lat, lng, notes ->
                viewModel.createClient(name, alias, phone, address, lat, lng, notes)
            }
        )
    }

    if (showAddLoanDialog) {
        AddLoanDialog(
            clients = allClients,
            onDismiss = { showAddLoanDialog = false },
            onConfirm = { clientId, amount, interest, quotas, frequency ->
                viewModel.createLoan(clientId, amount, interest, quotas, frequency)
            }
        )
    }

    showReceiptText?.let { receipt ->
        ReceiptDialog(
            receiptText = receipt,
            onDismiss = { showReceiptText = null }
        )
    }
}

@Composable
fun ClientCollectionCard(
    item: ClientWithActiveLoan,
    currentLocation: android.location.Location?,
    currencyFormat: NumberFormat,
    onCollectClick: () -> Unit,
    onNavigateClick: () -> Unit,
    onCallClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onPhotoClick: () -> Unit = {},
    onReminderClick: () -> Unit = {}
) {
    val isCollected = item.isCollectedToday
    val loan = item.activeLoan
    val distanceText = remember(currentLocation, item.client.latitude, item.client.longitude) {
        NavigationUtils.formatDistance(currentLocation, item.client.latitude, item.client.longitude)
    }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isCollected) Color(0xFFF0FDF4) else Color.White
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("client_card_${item.client.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (isCollected) EmeraldLight.copy(alpha = 0.2f) else GeometricAccent.copy(alpha = 0.15f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "#${item.client.visitOrder}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isCollected) EmeraldDark else GeometricAccent
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.client.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateNavy
                        )
                    }

                    if (item.client.aliasOrBusiness.isNotEmpty()) {
                        Text(
                            text = "🏬 ${item.client.aliasOrBusiness}",
                            fontSize = 13.sp,
                            color = EmeraldDark,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (item.client.address.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { onNavigateClick() }
                        ) {
                            Text(
                                text = "📍 ${item.client.address}",
                                fontSize = 12.sp,
                                color = Slate600
                            )
                            if (distanceText != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Slate100
                                ) {
                                    Text(
                                        text = distanceText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GeometricAccent,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCollected) EmeraldContainer else RoseContainer
                ) {
                    Text(
                        text = if (isCollected) "✓ COBRADO" else "PENDIENTE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isCollected) OnEmeraldContainer else RoseDanger,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Loan Details row
            if (loan != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCollected) Color(0xFFDCFCE7) else Slate100, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Cuota Diaria", fontSize = 11.sp, color = Slate600)
                        Text(
                            com.example.util.CurrencyUtils.format(loan.quotaAmount),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDark
                        )
                    }
                    Column {
                        Text("Saldo Restante", fontSize = 11.sp, color = Slate600)
                        Text(
                            com.example.util.CurrencyUtils.format(loan.remainingBalance),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateNavy
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Cuotas", fontSize = 11.sp, color = Slate600)
                        Text(
                            "${loan.paidQuotas}/${loan.totalQuotas}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlateNavy
                        )
                    }
                }
            } else {
                Text(
                    "Sin crédito activo registrado",
                    fontSize = 13.sp,
                    color = Slate600,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // GPS Directions / Turn-by-Turn Navigation
                IconButton(
                    onClick = onNavigateClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Slate100, CircleShape)
                ) {
                    Icon(
                        Icons.Default.Navigation,
                        contentDescription = "Cómo llegar con Google Maps",
                        tint = GeometricAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (item.client.phone.isNotEmpty()) {
                    IconButton(
                        onClick = onCallClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Slate100, CircleShape)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Llamar", tint = Slate700, modifier = Modifier.size(18.dp))
                    }
                }

                IconButton(
                    onClick = onPhotoClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Slate100, CircleShape)
                ) {
                    Icon(
                        Icons.Default.Add, // Or camera icon
                        contentDescription = "Foto de evidencia",
                        tint = Slate700,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (loan != null) {
                    if (isCollected) {
                        OutlinedButton(
                            onClick = onCollectClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Abono Adicional", fontSize = 13.sp, color = EmeraldDark)
                        }
                    } else {
                        Button(
                            onClick = onCollectClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_collect_${item.client.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                        ) {
                            Icon(Icons.Default.MonetizationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cobrar ${com.example.util.CurrencyUtils.format(loan.quotaAmount)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
