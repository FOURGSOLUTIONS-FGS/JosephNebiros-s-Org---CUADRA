package com.example.ui.components

import android.location.Location
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.service.SupabaseGpsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class EmergencyOption(
    val type: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
)

val EMERGENCY_OPTIONS = listOf(
    EmergencyOption(
        type = "SOS_PANICO",
        title = "SOS / Atraco en Curso",
        subtitle = "Alerta silenciosa inmediata a la central",
        icon = Icons.Default.Warning,
        color = Color(0xFFEF4444)
    ),
    EmergencyOption(
        type = "CLIENTE_AGRESIVO",
        title = "Cliente Agresivo / Amenaza",
        subtitle = "Conflicto o agresión en predio",
        icon = Icons.Default.PersonOff,
        color = Color(0xFFF97316)
    ),
    EmergencyOption(
        type = "ACCIDENTE",
        title = "Accidente / Emergencia Médica",
        subtitle = "Choque o caída en motocicleta",
        icon = Icons.Default.LocalHospital,
        color = Color(0xFFDC2626)
    ),
    EmergencyOption(
        type = "MOTO_VARADA",
        title = "Moto Varada / Falla Mecánica",
        subtitle = "Imposibilidad de continuar recorrido",
        icon = Icons.Default.TwoWheeler,
        color = Color(0xFFEAB308)
    ),
    EmergencyOption(
        type = "PREDIO_CERRADO",
        title = "Predio Cerrado / Mudanza Sospechosa",
        subtitle = "Cliente no ubicado o negocio cerrado",
        icon = Icons.Default.HomeWork,
        color = Color(0xFF3B82F6)
    )
)

/**
 * Botón Flotante de Pánico SOS (Patrón TraceOps)
 */
@Composable
fun FloatingSosButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = Color(0xFFDC2626),
        contentColor = Color.White,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(8.dp),
        modifier = modifier.size(54.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Emergency,
                contentDescription = "Botón de Pánico SOS",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Diálogo de Selección y Confirmación de Alerta de Emergencia
 */
@Composable
fun EmergencyPanicDialog(
    routeCode: String,
    collectorName: String,
    currentLocation: Location?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedOption by remember { mutableStateOf<EmergencyOption?>(null) }
    var notesText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isSending) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFDC2626)),
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
                                .background(Color(0xFFDC2626), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ShieldAlert, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Centro de Emergencia",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, enabled = !isSending) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Selecciona el incidente. Tu ubicación GPS en vivo será transmitida inmediatamente a la central:",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Options List
                EMERGENCY_OPTIONS.forEach { option ->
                    val isSelected = selectedOption?.type == option.type
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) option.color.copy(alpha = 0.2f) else Color(0xFF1E293B)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) option.color else Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedOption = option }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(option.color.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(option.icon, contentDescription = null, tint = option.color, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                Text(option.subtitle, fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = option.color, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Additional details input
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Detalles adicionales (opcional)", fontSize = 12.sp) },
                    placeholder = { Text("Ej: Moto placa XYZ, cliente en local verde...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFEF4444),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Button(
                    onClick = {
                        val opt = selectedOption ?: return@Button
                        isSending = true
                        CoroutineScope(Dispatchers.Main).launch {
                            val success = SupabaseGpsClient.sendEmergencyAlert(
                                routeCode = routeCode,
                                collectorName = collectorName,
                                alertType = opt.type,
                                location = currentLocation,
                                notes = notesText
                            )
                            isSending = false
                            if (success) {
                                Toast.makeText(context, "🚨 ALERTA TRANSMITIDA A LA CENTRAL", Toast.LENGTH_LONG).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "⚠️ Error de red. Intenta de nuevo o llama a la central.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = selectedOption != null && !isSending,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.EmergencyShare, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EMITIR ALERTA AHORA", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
