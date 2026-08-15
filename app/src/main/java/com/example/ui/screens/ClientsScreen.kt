package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClientEntity
import com.example.ui.components.AddClientDialog
import com.example.ui.components.AddLoanDialog
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.SlateNavy
import com.example.ui.viewmodel.CobranzaViewModel
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
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showAddClientDialog by remember { mutableStateOf(false) }
    var showAddLoanDialog by remember { mutableStateOf(false) }
    var preselectedClientId by remember { mutableStateOf<Long?>(null) }

    val filteredClients = remember(allClients, searchQuery) {
        if (searchQuery.isBlank()) allClients
        else allClients.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.aliasOrBusiness.contains(searchQuery, ignoreCase = true) ||
                    it.address.contains(searchQuery, ignoreCase = true)
        }
    }

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())

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
                        Text(
                            text = "Cartera de Clientes",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "${allClients.size} clientes registrados",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar por nombre, negocio o zona...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_client_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1E293B),
                                unfocusedContainerColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = EmeraldLight,
                                unfocusedBorderColor = Color(0xFF334155)
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
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

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
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
                                    color = SlateNavy
                                )
                                if (client.aliasOrBusiness.isNotEmpty()) {
                                    Text(
                                        text = "🏬 ${client.aliasOrBusiness}",
                                        fontSize = 13.sp,
                                        color = EmeraldDark,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (client.phone.isNotEmpty()) {
                                    Text(
                                        text = "📞 ${client.phone}",
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                }
                                if (client.address.isNotEmpty()) {
                                    Text(
                                        text = "📍 ${client.address}",
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.deleteClient(client) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = RoseDanger.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Active Loan Summary
                        if (activeLoan != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(EmeraldContainer, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Crédito Activo", fontSize = 11.sp, color = OnEmeraldContainer)
                                    Text("${com.example.util.CurrencyUtils.format(activeLoan.amountBorrowed)} (+${activeLoan.interestRate.toInt()}%)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldDark)
                                }
                                Column {
                                    Text("Saldo Restante", fontSize = 11.sp, color = OnEmeraldContainer)
                                    Text(com.example.util.CurrencyUtils.format(activeLoan.remainingBalance), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldDark)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Cuota Diaria", fontSize = 11.sp, color = OnEmeraldContainer)
                                    Text(com.example.util.CurrencyUtils.format(activeLoan.quotaAmount), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = EmeraldDark)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate100, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Sin crédito activo", fontSize = 12.sp, color = Slate600)
                                TextButton(
                                    onClick = {
                                        preselectedClientId = client.id
                                        showAddLoanDialog = true
                                    }
                                ) {
                                    Text("+ Dar Préstamo", fontSize = 12.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                }
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
}

@Composable
private fun TextButton(onClick: () -> Unit, content: @Composable () -> Unit) =
    androidx.compose.material3.TextButton(onClick = onClick) { content() }

