package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.ClientEntity
import com.example.data.model.LoanEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility helper to format and dispatch digital receipts and payment reminders via WhatsApp.
 */
object WhatsAppReceiptHelper {

    /**
     * Formats a clean, high-impact digital receipt optimized for WhatsApp with emojis and markdown.
     */
    fun formatWhatsAppReceipt(
        clientName: String,
        aliasOrBusiness: String? = null,
        address: String? = null,
        amountPaid: Double,
        quotaNumber: Int,
        totalQuotas: Int,
        remainingBalance: Double,
        paymentMethod: String = "EFECTIVO",
        receiptCode: String? = null,
        collectorName: String = "Cobranza Ruta Barranquilla"
    ): String {
        val df = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale("es", "CO"))
        val dateStr = df.format(Date())
        val code = receiptCode ?: "REC-${System.currentTimeMillis().toString().takeLast(6)}"
        val amountFormatted = CurrencyUtils.format(amountPaid)
        val balanceFormatted = CurrencyUtils.format(remainingBalance)
        val businessText = if (!aliasOrBusiness.isNullOrBlank()) "🏪 *$aliasOrBusiness*\n" else ""
        val addressText = if (!address.isNullOrBlank()) "📍 _${address}_\n" else ""
        val statusText = if (remainingBalance <= 0.01) "🎉 *¡PRÉSTAMO CANCELADO EN SU TOTALIDAD!*" else "✅ *AL DÍA*"

        return """
🧾 *COMPROBANTE OFICIAL DE PAGO* 🧾
━━━━━━━━━━━━━━━━━━━━━
👤 *Cliente:* $clientName
$businessText$addressText━━━━━━━━━━━━━━━━━━━━━
💰 *VALOR ABONADO:* $amountFormatted
📋 *Cuota:* #$quotaNumber de $totalQuotas
💳 *Medio de Pago:* $paymentMethod
📉 *Saldo Restante:* $balanceFormatted
🛡️ *Estado:* $statusText
━━━━━━━━━━━━━━━━━━━━━
🗓️ *Fecha:* $dateStr
🆔 *N° Comprobante:* #$code
🛵 *Atendido por:* $collectorName
━━━━━━━━━━━━━━━━━━━━━
🌟 *¡Muchas gracias por su puntualidad y confianza!*
📱 _Conserve este mensaje como soporte de su pago._
        """.trimIndent()
    }

    /**
     * Formats a payment reminder for WhatsApp.
     */
    fun formatPaymentReminder(
        clientName: String,
        quotaAmount: Double,
        remainingBalance: Double,
        quotasPending: Int
    ): String {
        val quotaFormatted = CurrencyUtils.format(quotaAmount)
        val balanceFormatted = CurrencyUtils.format(remainingBalance)

        return """
👋 *Hola $clientName*, cordial saludo de su asesor de cobranza.

Le recordamos su compromiso de pago para el día de hoy:
💵 *Valor de la Cuota:* $quotaFormatted
📉 *Saldo Pendiente:* $balanceFormatted
📋 *Cuotas Restantes:* $quotasPending

🛵 _Estaré pasando por su negocio/domicilio en los próximos minutos._
¡Muchas gracias por su atención! 🙏
        """.trimIndent()
    }

    /**
     * Cleans and formats a phone number for international WhatsApp link (defaults to Colombia +57 if 10 digits).
     */
    fun formatPhoneNumberForWhatsApp(rawPhone: String): String {
        val digitsOnly = rawPhone.replace("[^0-9]".toRegex(), "")
        return when {
            digitsOnly.startsWith("57") -> digitsOnly
            digitsOnly.length == 10 && digitsOnly.startsWith("3") -> "57$digitsOnly"
            digitsOnly.startsWith("502") -> digitsOnly // Guatemala
            else -> digitsOnly
        }
    }

    /**
     * Launches WhatsApp directly with the pre-filled message.
     * Uses WhatsApp package directly if available, with browser fallback.
     */
    fun sendWhatsAppMessage(
        context: Context,
        phoneNumber: String,
        message: String
    ): Boolean {
        return try {
            val formattedPhone = formatPhoneNumberForWhatsApp(phoneNumber)
            val encodedMessage = Uri.encode(message)

            // Direct API link that works with WhatsApp app and WhatsApp Business
            val url = if (formattedPhone.isNotBlank()) {
                "https://api.whatsapp.com/send?phone=$formattedPhone&text=$encodedMessage"
            } else {
                "https://api.whatsapp.com/send?text=$encodedMessage"
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.whatsapp")
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                // Fallback to WhatsApp Business package or generic view intent
                val wbIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.whatsapp.w4b")
                }
                if (wbIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(wbIntent)
                    true
                } else {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(browserIntent)
                    true
                }
            }
        } catch (e: Exception) {
            try {
                // Generic share fallback
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, message)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Enviar Comprobante"))
                true
            } catch (e2: Exception) {
                Toast.makeText(context, "No se pudo abrir WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }
}
