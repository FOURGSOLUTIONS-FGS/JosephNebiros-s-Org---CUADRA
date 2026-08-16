package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.SyncState
import kotlinx.coroutines.launch

@Composable
fun NetworkStatusBar(
    isOnline: Boolean,
    pendingSyncCount: Int,
    onSyncNow: () -> Unit,
    syncState: SyncState = SyncState.Idle(),
    modifier: Modifier = Modifier
) {
    var isManualTriggering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isSyncingNow = syncState is SyncState.Syncing || isManualTriggering

    val backgroundColor by animateColorAsState(
        targetValue = if (isOnline) {
            if (isSyncingNow) Color(0xFF1E3A8A)
            else if (pendingSyncCount > 0) Color(0xFF1E3A8A)
            else Color(0xFF0F172A)
        } else {
            Color(0xFF7C2D12) // Deep warm amber/red for offline
        },
        label = "bg_anim"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Status indicator pulse dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) (if (isSyncingNow) Color(0xFF38BDF8) else Color(0xFF22C55E)) else Color(0xFFF97316))
                )
                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (isOnline) (if (isSyncingNow) Color(0xFF7DD3FC) else Color(0xFF86EFAC)) else Color(0xFFFDBA74),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))

                val statusText = when {
                    !isOnline -> "Modo Local Offline • Guardado seguro en SQLite/Room"
                    syncState is SyncState.Syncing -> syncState.currentStep
                    syncState is SyncState.Success -> "Sincronizado • ${syncState.message}"
                    pendingSyncCount > 0 -> "En línea • $pendingSyncCount cobro(s) pendientes en Room"
                    else -> "En línea • Sincronización activa con Supabase Cloud"
                }

                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            if (pendingSyncCount > 0 && isOnline) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF3B82F6),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isSyncingNow) {
                            isManualTriggering = true
                            scope.launch {
                                onSyncNow()
                                kotlinx.coroutines.delay(1200)
                                isManualTriggering = false
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSyncingNow) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                color = Color.White,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sincronizar",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSyncingNow) "Subiendo..." else "Sincronizar ($pendingSyncCount)",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (!isOnline) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x33FFFFFF),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "Auto-Sync Activo",
                        color = Color(0xFFFFEDD5),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
