package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ClientEntity
import com.example.data.model.LoanEntity
import com.example.data.model.PaymentEntity
import com.example.ui.theme.BlueCyan
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GeometricBorderDark
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SlateNavy
import com.example.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PendingPaymentConfirmation(
    val client: ClientEntity,
    val loan: LoanEntity,
    val amount: Double,
    val method: String,
    val notes: String
)

data class PaymentSuccessDisplay(
    val clientName: String,
    val amount: Double,
    val quotaNumber: Int,
    val remainingBalance: Double,
    val receiptText: String,
    val clientPhone: String = "",
    val clientAlias: String = "",
    val clientAddress: String = ""
)

/**
 * Diálogo de pre-confirmación antes de procesar el pago para evitar errores del cobrador.
 */
@Composable
fun ConfirmPaymentPromptDialog(
    client: ClientEntity,
    loan: LoanEntity,
    amount: Double,
    paymentMethod: String,
    notes: String,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val newBalance = (loan.remainingBalance - amount).coerceAtLeast(0.0)
    val nextQuota = loan.paidQuotas + 1

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, GeometricBorderDark),
            tonalElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(EmeraldDark, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Paid,
                                contentDescription = null,
                                tint = EmeraldMint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "¿Confirmar Cobro?",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Breakdown Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cliente:", color = Slate400, fontSize = 13.sp)
                            Text(client.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Monto Recibido:", color = Slate400, fontSize = 13.sp)
                            Text(
                                CurrencyUtils.format(amount),
                                fontWeight = FontWeight.Black,
                                color = EmeraldMint,
                                fontSize = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cuota a Aplicar:", color = Slate400, fontSize = 13.sp)
                            Text(
                                "Cuota #$nextQuota de ${loan.totalQuotas}",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Forma de Pago:", color = Slate400, fontSize = 13.sp)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (paymentMethod == "EFECTIVO") EmeraldDark else Color(0xFF0369A1)
                            ) {
                                Text(
                                    text = if (paymentMethod == "EFECTIVO") "💵 EFECTIVO" else "📱 TRANSFERENCIA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Divider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = Color(0xFF1F2937)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Nuevo Saldo Pendiente:", color = Slate400, fontSize = 13.sp)
                            Text(
                                CurrencyUtils.format(newBalance),
                                fontWeight = FontWeight.Bold,
                                color = if (newBalance <= 0.01) EmeraldMint else Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Corregir", color = Slate400)
                    }

                    Button(
                        onClick = {
                            onConfirmed()
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("btn_confirm_payment_modal"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Registrar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Modal de Confirmación Exitosa para el cobrador con comprobante instantáneo y envío por WhatsApp.
 */
@Composable
fun PaymentSuccessModal(
    clientName: String,
    amount: Double,
    quotaNumber: Int,
    remainingBalance: Double,
    receiptText: String,
    clientPhone: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    var isAnimated by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isAnimated) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        isAnimated = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldLight.copy(alpha = 0.5f)),
            tonalElevation = 14.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success Badge Icon
                Box(
                    modifier = Modifier
                        .scale(scale)
                        .size(72.dp)
                        .background(
                            Brush.radialGradient(listOf(EmeraldMint, EmeraldDark)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Éxito",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "¡Cobro Registrado con Éxito!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = clientName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldMint
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mini summary card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Abono recibido:", fontSize = 13.sp, color = Slate400)
                            Text(CurrencyUtils.format(amount), fontSize = 16.sp, fontWeight = FontWeight.Black, color = EmeraldMint)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cuota aplicada:", fontSize = 13.sp, color = Slate400)
                            Text("Cuota #$quotaNumber", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Saldo restante:", fontSize = 13.sp, color = Slate400)
                            Text(CurrencyUtils.format(remainingBalance), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (remainingBalance <= 0.01) EmeraldMint else Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Action: Send receipt via WhatsApp
                Button(
                    onClick = {
                        com.example.util.WhatsAppReceiptHelper.sendWhatsAppMessage(
                            context = context,
                            phoneNumber = clientPhone,
                            message = receiptText
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_send_whatsapp_receipt"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "💬 Enviar Recibo por WhatsApp",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Actions: Share / Copy receipt
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(receiptText))
                            copied = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = Slate400)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (copied) "¡Copiado!" else "Copiar", fontSize = 12.sp, color = Slate400)
                    }

                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, receiptText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Enviar Comprobante"))
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldMint)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compartir", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_done_payment_success"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Continuar con la Ruta", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * Diálogo de Historial de Pagos de un Cliente específico
 */
@Composable
fun ClientPaymentHistoryDialog(
    client: ClientEntity,
    payments: List<PaymentEntity>,
    onDismiss: () -> Unit,
    onViewReceipt: (receiptText: String) -> Unit
) {
    val context = LocalContext.current
    val totalPaid = payments.sumOf { it.amount }
    val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, GeometricBorderDark),
            tonalElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .height(480.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFF0369A1), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Historial de Pagos",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = client.name,
                                fontSize = 12.sp,
                                color = EmeraldMint,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Banner
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
                            Text("Total Abonado", fontSize = 11.sp, color = Slate400)
                            Text(CurrencyUtils.format(totalPaid), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = EmeraldMint)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmeraldDark
                        ) {
                            Text(
                                text = "${payments.size} pagos",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (payments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No hay abonos registrados para este cliente.",
                            fontSize = 13.sp,
                            color = Slate400
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(payments, key = { it.id }) { p ->
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
                                        Text(
                                            text = "Cuota #${p.quotaNumber} • ${p.paymentMethod}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = dateFormat.format(Date(p.date)),
                                            fontSize = 11.sp,
                                            color = Slate400
                                        )
                                        if (p.notes.isNotEmpty()) {
                                            Text(
                                                text = "Nota: ${p.notes}",
                                                fontSize = 11.sp,
                                                color = Slate400
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "+${CurrencyUtils.format(p.amount)}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = EmeraldMint
                                        )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                val receipt = com.example.util.WhatsAppReceiptHelper.formatWhatsAppReceipt(
                                                    clientName = client.name,
                                                    aliasOrBusiness = client.aliasOrBusiness,
                                                    address = client.address,
                                                    amountPaid = p.amount,
                                                    quotaNumber = p.quotaNumber,
                                                    totalQuotas = 24,
                                                    remainingBalance = 0.0,
                                                    paymentMethod = p.paymentMethod,
                                                    receiptCode = "PAG-${p.id}"
                                                )
                                                com.example.util.WhatsAppReceiptHelper.sendWhatsAppMessage(
                                                    context = context,
                                                    phoneNumber = client.phone,
                                                    message = receipt
                                                )
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = "Enviar por WhatsApp",
                                                tint = Color(0xFF25D366),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                val receipt = """
                                                    🧾 COMPROBANTE DE PAGO
                                                    --------------------------------
                                                    Cliente: ${client.name}
                                                    Fecha: ${dateFormat.format(Date(p.date))}
                                                    Cuota: #${p.quotaNumber}
                                                    Monto: ${CurrencyUtils.format(p.amount)}
                                                    Método: ${p.paymentMethod}
                                                    --------------------------------
                                                    ¡Pago Registrado!
                                                """.trimIndent()
                                                onViewReceipt(receipt)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ReceiptLong,
                                                contentDescription = "Ver Recibo",
                                                tint = BlueCyan,
                                                modifier = Modifier.size(16.dp)
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
        }
    }
}
