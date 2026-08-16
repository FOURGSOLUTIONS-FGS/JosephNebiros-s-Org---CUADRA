package com.example.ui.components

import android.location.Location
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ClientEntity
import com.example.data.model.LoanEntity
import com.example.service.SupabaseGpsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class VisitReason(
    val code: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

val VISIT_REASONS = listOf(
    VisitReason(
        code = "AUSENTE_CASA_CERRADA",
        title = "Casa Cerrada / Ausente",
        description = "No responde al llamado en el predio",
        icon = Icons.Default.DoorFront,
        color = Color(0xFFF59E0B)
    ),
    VisitReason(
        code = "PROMESA_PAGO",
        title = "Promesa de Pago",
        description = "Cliente acuerda pagar en fecha próxima",
        icon = Icons.Default.Handshake,
        color = Color(0xFF3B82F6)
    ),
    VisitReason(
        code = "REHUSO_PAGO",
        title = "Se Niega a Pagar",
        description = "Rechazo explícito o evasión de cuota",
        icon = Icons.Default.Cancel,
        color = Color(0xFFEF4444)
    ),
    VisitReason(
        code = "CAMBIO_DOMICILIO",
        title = "Cambio de Domicilio / Mudanza",
        description = "Vecinos indican traslado de vivienda",
        icon = Icons.Default.HomeWork,
        color = Color(0xFF8B5CF6)
    )
)

/**
 * Diálogo para registrar Evidencia de Visita No Cobrada (POD - Proof of Delivery/Visit)
 */
@Composable
fun VisitProofDialog(
    client: ClientEntity,
    loan: LoanEntity,
    currentLocation: Location?,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var selectedReason by remember { mutableStateOf<VisitReason?>(null) }
    var notesText by remember { mutableStateOf("") }
    var promiseDateText by remember {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time))
    }
    var isSubmitting by remember { mutableStateOf(false) }

    // Geofencing calculation
    val distMeters = remember(currentLocation, client) {
        if (currentLocation != null && client.latitude != 0.0 && client.longitude != 0.0) {
            val r = 6371000.0
            val dLat = Math.toRadians(client.latitude - currentLocation.latitude)
            val dLon = Math.toRadians(client.longitude - currentLocation.longitude)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(currentLocation.latitude)) * Math.cos(Math.toRadians(client.latitude)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            r * c
        } else null
    }
    val isOnSite = distMeters == null || distMeters <= 150.0

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF8B5CF6)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFF8B5CF6), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Evidencia de Visita (POD)",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = client.name,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Geofencing verification badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isOnSite) Color(0xFF065F46).copy(alpha = 0.6f) else Color(0xFF7C2D12).copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isOnSite) Color(0xFF10B981) else Color(0xFFF97316)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isOnSite) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                            contentDescription = null,
                            tint = if (isOnSite) Color(0xFF34D399) else Color(0xFFFB923C),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (distMeters != null) {
                                if (isOnSite) "📍 Ubicación validada: En sitio (${distMeters.toInt()}m)"
                                else "⚠️ Visita Remota: A ${distMeters.toInt()}m del cliente"
                            } else "📍 Coordenadas satelitales adjuntas",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Selecciona el motivo de no pago:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Reason cards
                VISIT_REASONS.forEach { reason ->
                    val isSelected = selectedReason?.code == reason.code
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) reason.color.copy(alpha = 0.2f) else Color(0xFF1E293B)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) reason.color else Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { selectedReason = reason }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(reason.color.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(reason.icon, contentDescription = null, tint = reason.color, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(reason.title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color.White)
                                Text(reason.description, fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = reason.color, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // If promise of payment, show promise date input
                if (selectedReason?.code == "PROMESA_PAGO") {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = promiseDateText,
                        onValueChange = { promiseDateText = it },
                        label = { Text("Fecha Prometida de Pago (YYYY-MM-DD)", fontSize = 11.5.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Notes input
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Observaciones / Novedad", fontSize = 11.5.sp) },
                    placeholder = { Text("Ej: Hablé con el hijo, regresa a las 6pm", fontSize = 11.5.sp, color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Submit Button
                Button(
                    onClick = {
                        val reason = selectedReason ?: return@Button
                        isSubmitting = true
                        CoroutineScope(Dispatchers.Main).launch {
                            val success = SupabaseGpsClient.recordVisitProofToSupabase(
                                loanId = loan.id,
                                clientId = client.id,
                                clientName = client.name,
                                routeCode = loan.routeCode,
                                visitStatus = reason.code,
                                notes = notesText,
                                promiseDate = if (reason.code == "PROMESA_PAGO") promiseDateText else null,
                                photoBase64 = null,
                                latitude = currentLocation?.latitude,
                                longitude = currentLocation?.longitude,
                                distanceToClientMeters = distMeters ?: 0.0,
                                isOnSite = isOnSite
                            )
                            isSubmitting = false
                            if (success) {
                                Toast.makeText(context, "📸 Evidencia de visita registrada exitosamente", Toast.LENGTH_LONG).show()
                                onSuccess()
                            } else {
                                Toast.makeText(context, "⚠️ Error guardando evidencia. Intenta de nuevo.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = selectedReason != null && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("REGISTRAR VISITA AUDITADA", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}
