package com.example.ui.screens

import com.example.ui.components.FloatingSosButton
import com.example.ui.components.EmergencyPanicDialog

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
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
import com.example.data.model.ClientEntity
import com.example.data.model.ClientWithActiveLoan
import com.example.data.model.LoanEntity
import com.example.ui.components.ConfirmPaymentPromptDialog
import com.example.ui.components.MapRouteCanvas
import com.example.ui.components.PaymentDialog
import com.example.ui.components.PaymentSuccessDisplay
import com.example.ui.components.PaymentSuccessModal
import com.example.ui.components.PendingPaymentConfirmation
import com.example.ui.components.ReceiptDialog
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlueInfo
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.GeometricAccent
import com.example.ui.theme.GeometricAccentContainer
import com.example.ui.theme.GeometricAccentLight
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.SlateNavy
import com.example.ui.viewmodel.CobranzaViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun LiveMapScreen(
    viewModel: CobranzaViewModel,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTrackingActive by viewModel.isTrackingActive.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val pointsCount by viewModel.pointsCount.collectAsStateWithLifecycle()
    val dailyRouteList by viewModel.dailyRouteList.collectAsStateWithLifecycle()
    val trackingSessions by viewModel.trackingSessions.collectAsStateWithLifecycle()
    val selectedSessionId by viewModel.selectedSessionId.collectAsStateWithLifecycle()
    val selectedSessionPoints by viewModel.selectedSessionPoints.collectAsStateWithLifecycle()
    val liveDistanceMeters by viewModel.liveDistanceMeters.collectAsStateWithLifecycle()
    val currentSpeedKmh by viewModel.currentSpeedKmh.collectAsStateWithLifecycle()
    val currentBearing by viewModel.currentBearing.collectAsStateWithLifecycle()
    val accuracyMeters by viewModel.accuracyMeters.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsStateWithLifecycle()
    val cloudSyncSuccessCount by viewModel.cloudSyncSuccessCount.collectAsStateWithLifecycle()

    var autoFollow by remember { mutableStateOf(true) }
    var selectedClientForPopup by remember { mutableStateOf<ClientWithActiveLoan?>(null) }
    var selectedClientForPayment by remember { mutableStateOf<ClientWithActiveLoan?>(null) }
    var pendingConfirmation by remember { mutableStateOf<PendingPaymentConfirmation?>(null) }
    var paymentSuccessInfo by remember { mutableStateOf<PaymentSuccessDisplay?>(null) }
    var showReceiptText by remember { mutableStateOf<String?>(null) }

    val activeSession = trackingSessions.find { it.sessionId == selectedSessionId }
    val displayDistanceKm = if (isTrackingActive) {
        liveDistanceMeters / 1000.0
    } else {
        (activeSession?.totalDistanceMeters ?: 0.0) / 1000.0
    }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val elapsedTimeFormatted = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    Box(modifier = modifier.fillMaxSize()) {
        // Real-time Map Canvas
        MapRouteCanvas(
            routePoints = selectedSessionPoints,
            clients = dailyRouteList,
            currentLocation = currentLocation,
            isTrackingActive = isTrackingActive,
            bearing = currentBearing,
            accuracy = accuracyMeters,
            autoFollow = autoFollow,
            onAutoFollowChanged = { autoFollow = it },
            modifier = Modifier.fillMaxSize(),
            onClientMarkerClick = { clientLoan ->
                selectedClientForPopup = clientLoan
            }
        )

        // Top Status & HUD Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tracking Status Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isTrackingActive) GeometricAccent.copy(alpha = 0.95f) else SlateNavy.copy(alpha = 0.92f),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(
                                    if (isTrackingActive) Color.White else Slate400,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTrackingActive) "GPS EN VIVO (2do Plano)" else "GPS EN PAUSA",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Accuracy and GPS Precision Chip
                if (currentLocation != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SlateNavy.copy(alpha = 0.92f),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = GeometricAccentLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "±${String.format(Locale.US, "%.1f", accuracyMeters)}m",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Active elapsed stopwatch if tracking is running
            if (isTrackingActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SlateNavy.copy(alpha = 0.92f),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tiempo: $elapsedTimeFormatted",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SlateNavy.copy(alpha = 0.92f),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                tint = if (cloudSyncSuccessCount > 0) Color(0xFF34D399) else Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Supabase / Admin ($cloudSyncSuccessCount)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Client Info Popup Card (When clicking a marker on map)
        AnimatedVisibility(
            visible = selectedClientForPopup != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 220.dp)
        ) {
            selectedClientForPopup?.let { item ->
                val client = item.client
                val loan = item.activeLoan
                val isCollected = item.isCollectedToday

                // Calculate distance in meters from current GPS location to client
                val distMeters = if (currentLocation != null && client.latitude != null && client.longitude != null) {
                    val r = 6371000.0
                    val dLat = Math.toRadians(client.latitude - currentLocation!!.latitude)
                    val dLon = Math.toRadians(client.longitude - currentLocation!!.longitude)
                    val a = sin(dLat / 2) * sin(dLat / 2) +
                            cos(Math.toRadians(currentLocation!!.latitude)) * cos(Math.toRadians(client.latitude)) *
                            sin(dLon / 2) * sin(dLon / 2)
                    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
                    r * c
                } else null

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateNavy),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = client.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (client.aliasOrBusiness.isNotEmpty()) {
                                    Text(
                                        text = client.aliasOrBusiness,
                                        fontSize = 12.sp,
                                        color = GeometricAccentLight
                                    )
                                }
                            }
                            IconButton(
                                onClick = { selectedClientForPopup = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Distance and Address Info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (distMeters != null && distMeters < 50) EmeraldLight else Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (distMeters != null) {
                                    if (distMeters < 1000) "A ${distMeters.toInt()} metros de ti"
                                    else "A ${String.format(Locale.US, "%.1f", distMeters / 1000.0)} km de ti"
                                } else {
                                    client.address.ifEmpty { "Sin dirección registrada" }
                                },
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quota & Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (loan != null) {
                                Column {
                                    Text("Cuota Diaria:", fontSize = 11.sp, color = Slate400)
                                    Text(
                                        "${com.example.util.CurrencyUtils.format(loan.quotaAmount)} (${loan.paidQuotas}/${loan.totalQuotas})",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCollected) EmeraldLight else AmberWarning
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (client.phone.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client.phone}"))
                                            context.startActivity(dialIntent)
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Slate800, CircleShape)
                                    ) {
                                        Icon(Icons.Default.Phone, contentDescription = "Llamar", tint = Color.White)
                                    }
                                }

                                if (currentLocation != null) {
                                    IconButton(
                                        onClick = {
                                            viewModel.assignCurrentGpsToClient(client)
                                            selectedClientForPopup = null
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Slate800, CircleShape)
                                    ) {
                                        Icon(Icons.Default.PinDrop, contentDescription = "Fijar GPS actual", tint = GeometricAccentLight)
                                    }
                                }

                                if (!isCollected && loan != null) {
                                    Button(
                                        onClick = {
                                            selectedClientForPayment = item
                                            selectedClientForPopup = null
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GeometricAccent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Cobrar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Dashboard Drawer (Telemetry + Foreground Service Controls)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 60.dp),
            shape = RoundedCornerShape(24.dp),
            color = SlateNavy.copy(alpha = 0.95f),
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Session Selector Chips
                if (trackingSessions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = GeometricAccentLight, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Historial de Recorridos GPS:", fontSize = 12.sp, color = Slate400, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(trackingSessions, key = { it.sessionId }) { session ->
                            val isSelected = session.sessionId == selectedSessionId
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectSession(session.sessionId) },
                                label = {
                                    Text(
                                        text = "${session.sessionId.takeLast(6)} (${session.pointsCount} pts)",
                                        fontSize = 11.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GeometricAccent,
                                    selectedLabelColor = Color.White,
                                    containerColor = Slate800,
                                    labelColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Telemetry Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Metric 1: Distance
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Route, contentDescription = null, tint = GeometricAccentLight, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Distancia", fontSize = 11.sp, color = Slate400)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format(Locale.US, "%.2f", displayDistanceKm)} km",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    // Metric 2: Speed
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Velocidad", fontSize = 11.sp, color = Slate400)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format(Locale.US, "%.1f", currentSpeedKmh)} km/h",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    // Metric 3: GPS Points
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timeline, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Puntos GPS", fontSize = 11.sp, color = Slate400)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isTrackingActive) "$pointsCount" else "${selectedSessionPoints.size}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Foreground Service Action Button
                Button(
                    onClick = {
                        if (isTrackingActive) {
                            viewModel.stopRouteTracking(context)
                        } else {
                            onRequestLocationPermission()
                            viewModel.startRouteTracking(context)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("map_toggle_tracking_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTrackingActive) RoseDanger else GeometricAccent
                    )
                ) {
                    Icon(
                        if (isTrackingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTrackingActive) "Detener Grabación en Segundo Plano" else "Iniciar Rastreo GPS en Tiempo Real",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
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

    showReceiptText?.let { receipt ->
        ReceiptDialog(
            receiptText = receipt,
            onDismiss = { showReceiptText = null }
        )
    }
}
