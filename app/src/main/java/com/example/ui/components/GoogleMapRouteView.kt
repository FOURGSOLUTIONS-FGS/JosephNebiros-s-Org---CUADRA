package com.example.ui.components

import android.location.Location
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClientWithActiveLoan
import com.example.data.model.RoutePointEntity
import com.example.ui.theme.AmberWarning
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
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.SlateNavy
import com.example.util.CurrencyUtils
import com.example.util.NavigationUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

// Barranquilla Centro Anchor
private val BARRANQUILLA_CENTER = LatLng(10.9878, -74.7889)

@Composable
fun GoogleMapRouteView(
    routePoints: List<RoutePointEntity>,
    clients: List<ClientWithActiveLoan>,
    currentLocation: Location?,
    isTrackingActive: Boolean,
    modifier: Modifier = Modifier,
    onSwitchToRadar: () -> Unit = {},
    onCollectPaymentClick: (ClientWithActiveLoan) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showApiKeyNotice by remember { mutableStateOf(true) }

    val initialPosition = remember(currentLocation) {
        if (currentLocation != null) {
            LatLng(currentLocation.latitude, currentLocation.longitude)
        } else {
            clients.firstOrNull { it.client.latitude != null && it.client.longitude != null }?.let {
                LatLng(it.client.latitude!!, it.client.longitude!!)
            } ?: BARRANQUILLA_CENTER
        }
    }

    val cameraPositionState: CameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPosition, 14f)
    }

    var selectedMapType by remember { mutableStateOf(MapType.NORMAL) }
    var selectedClient by remember { mutableStateOf<ClientWithActiveLoan?>(null) }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false
        )
    }

    val mapProperties = remember(selectedMapType) {
        MapProperties(
            isMyLocationEnabled = currentLocation != null,
            mapType = selectedMapType
        )
    }

    // Convert route history points to LatLng list for Polyline
    val polylinePoints = remember(routePoints) {
        routePoints.map { LatLng(it.latitude, it.longitude) }
    }

    // Auto-center when GPS updates if tracking is active and no client selected
    LaunchedEffect(currentLocation) {
        if (isTrackingActive && currentLocation != null && selectedClient == null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(LatLng(currentLocation.latitude, currentLocation.longitude))
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapClick = {
                selectedClient = null
            }
        ) {
            // Polyline for recorded route track
            if (polylinePoints.size > 1) {
                Polyline(
                    points = polylinePoints,
                    color = Color(0xFF0284C7),
                    width = 12f
                )
            }

            // Customer Markers
            clients.forEachIndexed { index, item ->
                val lat = item.client.latitude
                val lng = item.client.longitude
                if (lat != null && lng != null && !lat.isNaN() && !lng.isNaN()) {
                    val position = LatLng(lat, lng)
                    val isCollected = item.isCollectedToday
                    val markerColor = if (isCollected) BitmapDescriptorFactory.HUE_GREEN else BitmapDescriptorFactory.HUE_AZURE

                    Marker(
                        state = MarkerState(position = position),
                        title = "#${item.client.visitOrder} ${item.client.name}",
                        snippet = if (isCollected) "✓ Cobrado" else "Pendiente: ${CurrencyUtils.format(item.activeLoan?.quotaAmount ?: 0.0)}",
                        icon = BitmapDescriptorFactory.defaultMarker(markerColor),
                        onClick = {
                            selectedClient = item
                            coroutineScope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(position, 16f))
                            }
                            true
                        }
                    )
                }
            }
        }

        // Top Warning / Helper Banner if Google Maps Key or Tiles are pending
        AnimatedVisibility(
            visible = showApiKeyNotice,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp, end = 60.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SlateNavy.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate600),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modo Google Maps SDK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "¿Pantalla blanca? Usa el Radar Vectorial Offline o abre navegación.",
                            fontSize = 10.sp,
                            color = Slate400,
                            lineHeight = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GeometricAccent,
                        modifier = Modifier.clickable { onSwitchToRadar() }
                    ) {
                        Text(
                            text = "Ver Radar",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { showApiKeyNotice = false },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar aviso", tint = Slate400, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Top Right Controls (Map Type, Zoom Controls, Center Location, Fit Route)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 78.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom In
            Surface(
                shape = CircleShape,
                color = SlateNavy.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.zoomIn())
                        }
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Aumentar zoom",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Zoom Out
            Surface(
                shape = CircleShape,
                color = SlateNavy.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.zoomOut())
                        }
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Disminuir zoom",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Toggle Satellite / Normal Map Type
            Surface(
                shape = CircleShape,
                color = SlateNavy.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                IconButton(
                    onClick = {
                        selectedMapType = when (selectedMapType) {
                            MapType.NORMAL -> MapType.HYBRID
                            MapType.HYBRID -> MapType.TERRAIN
                            else -> MapType.NORMAL
                        }
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = "Tipo de mapa",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Recenter on current GPS
            if (currentLocation != null) {
                Surface(
                    shape = CircleShape,
                    color = EmeraldDark.copy(alpha = 0.95f),
                    shadowElevation = 4.dp
                ) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(currentLocation.latitude, currentLocation.longitude),
                                        16f
                                    )
                                )
                            }
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = "Mi Ubicación",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Fit All Clients Bounds
            Surface(
                shape = CircleShape,
                color = Slate800.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                IconButton(
                    onClick = {
                        val validPoints = clients.mapNotNull {
                            val lat = it.client.latitude
                            val lng = it.client.longitude
                            if (lat != null && lng != null && !lat.isNaN() && !lng.isNaN()) LatLng(lat, lng) else null
                        }
                        if (validPoints.isNotEmpty()) {
                            val builder = LatLngBounds.builder()
                            validPoints.forEach { builder.include(it) }
                            currentLocation?.let { builder.include(LatLng(it.latitude, it.longitude)) }
                            val bounds = builder.build()
                            coroutineScope.launch {
                                try {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 80))
                                } catch (e: Exception) {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLng(bounds.center))
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Default.ZoomOutMap,
                        contentDescription = "Ver toda la ruta",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bottom Quick Directions Card when a client marker is tapped
        AnimatedVisibility(
            visible = selectedClient != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            selectedClient?.let { item ->
                val distanceText = NavigationUtils.formatDistance(
                    currentLocation,
                    item.client.latitude,
                    item.client.longitude
                )

                ElevatedCard(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${item.client.visitOrder} ${item.client.name}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateNavy
                                    )
                                    if (distanceText != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Slate100
                                        ) {
                                            Text(
                                                text = "📍 $distanceText",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = GeometricAccent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (item.client.address.isNotEmpty()) {
                                    Text(
                                        text = item.client.address,
                                        fontSize = 12.sp,
                                        color = Slate600,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedClient = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate600)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Actions: Turn-by-turn Navigation & Collect
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Turn-by-Turn GPS Button
                            Button(
                                onClick = {
                                    NavigationUtils.openGoogleMapsNavigation(
                                        context = context,
                                        destinationLat = item.client.latitude,
                                        destinationLng = item.client.longitude,
                                        destinationAddress = item.client.address,
                                        destinationName = item.client.name
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GeometricAccent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cómo Llegar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // Call button if phone exists
                            if (item.client.phone.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_DIAL,
                                            android.net.Uri.parse("tel:${item.client.phone}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Llamar", tint = Slate700)
                                }
                            }

                            // Collect Payment Button
                            if (!item.isCollectedToday && item.activeLoan != null) {
                                Button(
                                    onClick = {
                                        onCollectPaymentClick(item)
                                        selectedClient = null
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.MonetizationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cobrar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
