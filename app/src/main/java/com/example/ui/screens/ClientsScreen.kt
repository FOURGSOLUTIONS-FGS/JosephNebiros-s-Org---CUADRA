package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ClientEntity
import com.example.ui.components.AddClientDialog
import com.example.ui.components.AddLoanDialog
import com.example.ui.components.ClientPaymentHistoryDialog
import com.example.ui.components.PhotoCaptureDialog
import com.example.ui.components.ReceiptDialog
import com.example.ui.components.RemindersDialog
import com.example.ui.theme.BlueCyan
import com.example.ui.theme.BlueInfo
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GeometricBorderDark
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SlateNavy
import com.example.ui.viewmodel.CobranzaViewModel
import com.example.util.CurrencyUtils
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Navigation
import com.example.util.NavigationUtils
import com.example.util.WhatsAppReceiptHelper
import java.io.File
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ClientsScreen(
    viewModel: CobranzaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allClients by viewModel.allClients.collectAsStateWithLifecycle()
    val dailyRouteList by viewModel.dailyRouteList.collectAsStateWithLifecycle()
    val allPaymentsHistory by viewModel.allPaymentsHistory.collectAsStateWithLifecycle()
    val allReminders by viewModel.allReminders.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showAddClientDialog by remember { mutableStateOf(false) }
    var showAddLoanDialog by remember { mutableStateOf(false) }
    var preselectedClientId by remember { mutableStateOf<Long?>(null) }
    var selectedClientForHistory by remember { mutableStateOf<ClientEntity?>(null) }
    var selectedClientForPhoto by remember { mutableStateOf<ClientEntity?>(null) }
    var selectedClientForReminders by remember { mutableStateOf<ClientEntity?>(null) }
    var viewingReceiptText by remember { mutableStateOf<String?>(null) }

    val filteredClients = remember(allClients, searchQuery) {
        if (searchQuery.isBlank()) allClients
        else allClients.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.aliasOrBusiness.contains(searchQuery, ignoreCase = true) ||
                    it.address.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Header
            item {
                Surface(
                    color = Slate900,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Cartera de Clientes",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "${allClients.size} clientes en sistema",
                            fontSize = 13.sp,
                            color = Slate400
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar por nombre, negocio o dirección...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_client_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Slate800,
                                unfocusedContainerColor = Slate800,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = EmeraldMint,
                                unfocusedBorderColor = GeometricBorderDark
                            )
                        )
                    }
                }
            }

            // Quick Actions Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showAddClientDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Cliente", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            preselectedClientId = null
                            showAddLoanDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Crédito", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Client Cards
            items(filteredClients, key = { it.id }) { client ->
                val clientLoanInfo = dailyRouteList.find { it.client.id == client.id }
                val activeLoan = clientLoanInfo?.activeLoan
                val clientPayments = allPaymentsHistory.filter { it.clientId == client.id }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Slate900,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GeometricBorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = client.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (client.aliasOrBusiness.isNotEmpty()) {
                                    Text(
                                        text = "🏬 ${client.aliasOrBusiness}",
                                        fontSize = 13.sp,
                                        color = EmeraldMint,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                if (client.phone.isNotEmpty()) {
                                    Text(
                                        text = "📞 ${client.phone}",
                                        fontSize = 12.sp,
                                        color = Slate400
                                    )
                                }
                                if (client.address.isNotEmpty()) {
                                    Text(
                                        text = "📍 ${client.address}",
                                        fontSize = 12.sp,
                                        color = Slate400
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Historial de Pagos Button
                                IconButton(
                                    onClick = { selectedClientForHistory = client },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = "Historial de Pagos",
                                        tint = BlueCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteClient(client) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar",
                                        tint = RoseDanger.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Active Loan Summary
                        if (activeLoan != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF111827),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Crédito Activo", fontSize = 11.sp, color = Slate400)
                                        Text("${CurrencyUtils.format(activeLoan.amountBorrowed)} (+${activeLoan.interestRate.toInt()}%)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Column {
                                        Text("Saldo Restante", fontSize = 11.sp, color = Slate400)
                                        Text(CurrencyUtils.format(activeLoan.remainingBalance), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldMint)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Cuota Diaria", fontSize = 11.sp, color = Slate400)
                                        Text(CurrencyUtils.format(activeLoan.quotaAmount), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = EmeraldMint)
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF111827),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Sin crédito activo", fontSize = 12.sp, color = Slate400)
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            preselectedClientId = client.id
                                            showAddLoanDialog = true
                                        }
                                    ) {
                                        Text("+ Crear Crédito", fontSize = 12.sp, color = EmeraldMint, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Recent payments badge
                        if (clientPayments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedClientForHistory = client },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📜 ${clientPayments.size} pagos registrados (Total: ${com.example.util.CurrencyUtils.format(clientPayments.sumOf { it.amount })})",
                                    fontSize = 11.sp,
                                    color = BlueCyan,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = BlueCyan, modifier = Modifier.size(14.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Action Buttons (Waze, Maps, WhatsApp, Phone, Photo, Reminders)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Waze Navigation
                            IconButton(
                                onClick = {
                                    NavigationUtils.openWazeNavigation(
                                        context = context,
                                        destinationLat = client.latitude,
                                        destinationLng = client.longitude,
                                        destinationAddress = client.address,
                                        destinationName = client.name
                                    )
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF00D4D4), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Navigation,
                                    contentDescription = "Waze",
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Google Maps Navigation
                            IconButton(
                                onClick = {
                                    NavigationUtils.openGoogleMapsNavigation(
                                        context = context,
                                        destinationLat = client.latitude,
                                        destinationLng = client.longitude,
                                        destinationAddress = client.address,
                                        destinationName = client.name
                                    )
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF2563EB), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Google Maps",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            if (client.phone.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        val reminder = WhatsAppReceiptHelper.formatPaymentReminder(
                                            clientName = client.name,
                                            quotaAmount = activeLoan?.quotaAmount ?: 0.0,
                                            remainingBalance = activeLoan?.remainingBalance ?: 0.0,
                                            quotasPending = if (activeLoan != null) (activeLoan.totalQuotas - activeLoan.paidQuotas).coerceAtLeast(0) else 0
                                        )
                                        WhatsAppReceiptHelper.sendWhatsAppMessage(
                                            context = context,
                                            phoneNumber = client.phone,
                                            message = reminder
                                        )
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF25D366), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Chat,
                                        contentDescription = "WhatsApp Recordatorio",
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client.phone}"))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Slate800, CircleShape)
                                        .border(BorderStroke(1.dp, GeometricBorderDark), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Call,
                                        contentDescription = "Llamar",
                                        tint = BlueCyan,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedClientForPhoto = client },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Slate800, CircleShape)
                                    .border(BorderStroke(1.dp, GeometricBorderDark), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Foto Fachada",
                                    tint = Slate300,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            IconButton(
                                onClick = { selectedClientForReminders = client },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Slate800, CircleShape)
                                    .border(BorderStroke(1.dp, GeometricBorderDark), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Alarm,
                                    contentDescription = "Recordatorios",
                                    tint = com.example.ui.theme.AmberWarning,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
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
            preselectedClientId = preselectedClientId,
            onDismiss = { showAddLoanDialog = false },
            onConfirm = { clientId, amount, interest, quotas, frequency ->
                viewModel.createLoan(clientId, amount, interest, quotas, frequency)
            }
        )
    }

    selectedClientForHistory?.let { client ->
        val payments = allPaymentsHistory.filter { it.clientId == client.id }
        ClientPaymentHistoryDialog(
            client = client,
            payments = payments,
            onDismiss = { selectedClientForHistory = null },
            onViewReceipt = { receipt ->
                viewingReceiptText = receipt
            }
        )
    }

    selectedClientForPhoto?.let { client ->
        PhotoCaptureDialog(
            title = "Foto de Fachada / Negocio",
            clientName = client.name,
            initialPhotoUri = client.photoUri,
            onPhotoSaved = { path ->
                viewModel.updateClientPhoto(client.id, path)
            },
            onDismiss = { selectedClientForPhoto = null }
        )
    }

    selectedClientForReminders?.let { client ->
        val clientReminders = allReminders.filter { it.clientId == client.id }
        RemindersDialog(
            clientId = client.id,
            clientName = client.name,
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

    viewingReceiptText?.let { receipt ->
        ReceiptDialog(
            receiptText = receipt,
            onDismiss = { viewingReceiptText = null }
        )
    }
}
