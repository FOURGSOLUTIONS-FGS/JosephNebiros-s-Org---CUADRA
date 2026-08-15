package com.example.ui.components

import android.location.Location
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClientWithActiveLoan
import com.example.data.model.RoutePointEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.GeometricAccent
import com.example.ui.theme.GeometricAccentLight
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.SlateNavy
import com.example.util.CurrencyUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Barranquilla Default Geospatial Boundaries
private const val BQ_MIN_LAT = 10.9400
private const val BQ_MAX_LAT = 11.0250
private const val BQ_MIN_LNG = -74.8320
private const val BQ_MAX_LNG = -74.7620

@Composable
fun MapRouteCanvas(
    routePoints: List<RoutePointEntity>,
    clients: List<ClientWithActiveLoan>,
    currentLocation: Location?,
    isTrackingActive: Boolean,
    modifier: Modifier = Modifier,
    bearing: Float = 0f,
    accuracy: Float = 0f,
    autoFollow: Boolean = true,
    onAutoFollowChanged: (Boolean) -> Unit = {},
    onClientMarkerClick: (ClientWithActiveLoan) -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Pulsing radar animation for live tracking dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 44f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    // Store rendered client touch hitboxes
    val clientTouchTargets = remember { mutableListOf<Pair<Offset, ClientWithActiveLoan>>() }

    // Auto-center on current location if autoFollow is active
    LaunchedEffect(currentLocation, autoFollow) {
        if (autoFollow && currentLocation != null) {
            panOffset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF090E17))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.15f, 20.0f)
                    panOffset += pan
                    onAutoFollowChanged(false)
                }
            }
            .pointerInput(clients, routePoints, scale, panOffset) {
                detectTapGestures { tapOffset ->
                    val hit = clientTouchTargets.minByOrNull { (pos, _) ->
                        val dx = pos.x - tapOffset.x
                        val dy = pos.y - tapOffset.y
                        sqrt((dx * dx + dy * dy).toDouble())
                    }
                    if (hit != null) {
                        val dist = sqrt(
                            ((hit.first.x - tapOffset.x) * (hit.first.x - tapOffset.x) +
                             (hit.first.y - tapOffset.y) * (hit.first.y - tapOffset.y)).toDouble()
                        )
                        if (dist < 75.0) {
                            onClientMarkerClick(hit.second)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            clientTouchTargets.clear()

            // Gather all coordinates to compute bounding box around Barranquilla
            val allLats = mutableListOf<Double>()
            val allLngs = mutableListOf<Double>()

            // Add Barranquilla base anchors to ensure city perspective is always framed
            allLats.add(BQ_MIN_LAT)
            allLats.add(BQ_MAX_LAT)
            allLngs.add(BQ_MIN_LNG)
            allLngs.add(BQ_MAX_LNG)

            routePoints.forEach {
                allLats.add(it.latitude)
                allLngs.add(it.longitude)
            }
            clients.forEach {
                it.client.latitude?.let { lat -> allLats.add(lat) }
                it.client.longitude?.let { lng -> allLngs.add(lng) }
            }
            currentLocation?.let {
                allLats.add(it.latitude)
                allLngs.add(it.longitude)
            }

            val minLat = allLats.minOrNull() ?: BQ_MIN_LAT
            val maxLat = allLats.maxOrNull() ?: BQ_MAX_LAT
            val minLng = allLngs.minOrNull() ?: BQ_MIN_LNG
            val maxLng = allLngs.maxOrNull() ?: BQ_MAX_LNG

            val latSpan = max(maxLat - minLat, 0.005)
            val lngSpan = max(maxLng - minLng, 0.005)

            val padding = 70f

            fun toCanvasOffset(lat: Double, lng: Double): Offset {
                val normX = ((lng - minLng) / lngSpan).toFloat()
                val normY = (1.0f - ((lat - minLat) / latSpan).toFloat()) // Invert Y for screen

                val baseCoordX = padding + normX * (canvasWidth - padding * 2)
                val baseCoordY = padding + normY * (canvasHeight - padding * 2)

                val centeredX = (baseCoordX - canvasWidth / 2f) * scale + canvasWidth / 2f + panOffset.x
                val centeredY = (baseCoordY - canvasHeight / 2f) * scale + canvasHeight / 2f + panOffset.y

                return Offset(centeredX, centeredY)
            }

            // 1. Draw Technical Radar Grid
            drawRadarGrid(canvasWidth, canvasHeight, panOffset, scale)

            // 2. Draw Barranquilla Geographic Base Layer (Río Magdalena, Arterias Viales, Sectores)
            drawBarranquillaBaseMap(::toCanvasOffset, scale)

            // 3. Draw Route Polyline with Neon Trail & Gradient
            if (routePoints.size > 1) {
                val path = Path()
                val firstPt = toCanvasOffset(routePoints.first().latitude, routePoints.first().longitude)
                path.moveTo(firstPt.x, firstPt.y)

                for (i in 1 until routePoints.size) {
                    val pt = toCanvasOffset(routePoints[i].latitude, routePoints[i].longitude)
                    path.lineTo(pt.x, pt.y)
                }

                // Outer Glowing Shadow
                drawPath(
                    path = path,
                    color = GeometricAccentLight.copy(alpha = 0.25f),
                    style = Stroke(
                        width = 14f * scale.coerceIn(0.7f, 2.2f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                // Mid Line
                drawPath(
                    path = path,
                    color = GeometricAccent,
                    style = Stroke(
                        width = 6f * scale.coerceIn(0.7f, 2.2f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                // Inner Bright Core
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.8f),
                    style = Stroke(
                        width = 2f * scale.coerceIn(0.7f, 2.2f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Start Flag Pin
                val startOffset = toCanvasOffset(routePoints.first().latitude, routePoints.first().longitude)
                drawCircle(color = Color(0xFF38BDF8), radius = 10f * scale.coerceIn(0.8f, 2f), center = startOffset)
                drawCircle(color = Color.White, radius = 4f * scale.coerceIn(0.8f, 2f), center = startOffset)

                val startPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 22f * scale.coerceIn(0.7f, 1.2f)
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
                }
                drawContext.canvas.nativeCanvas.drawText("🚩 INICIO", startOffset.x, startOffset.y + 24f * scale.coerceIn(0.8f, 1.5f), startPaint)
            }

            // 4. Draw Client Stops / Customer Geofence Markers
            clients.forEachIndexed { index, item ->
                val lat = item.client.latitude
                val lng = item.client.longitude
                if (lat != null && lng != null) {
                    val pinOffset = toCanvasOffset(lat, lng)
                    clientTouchTargets.add(Pair(pinOffset, item))

                    val isCollected = item.isCollectedToday
                    val pinColor = if (isCollected) EmeraldLight else AmberWarning

                    // Outer pulse ring for pending
                    if (!isCollected) {
                        drawCircle(
                            color = pinColor.copy(alpha = 0.22f),
                            radius = 22f * scale.coerceIn(0.7f, 2f),
                            center = pinOffset
                        )
                    }

                    // Main Marker Body
                    drawCircle(
                        color = pinColor,
                        radius = 13f * scale.coerceIn(0.7f, 2f),
                        center = pinOffset
                    )
                    drawCircle(
                        color = Color(0xFF0F172A),
                        radius = 10f * scale.coerceIn(0.7f, 2f),
                        center = pinOffset
                    )
                    drawCircle(
                        color = if (isCollected) EmeraldLight else Color.White,
                        radius = 5f * scale.coerceIn(0.7f, 2f),
                        center = pinOffset
                    )

                    // Client Tag Label with Stop Number & COP Quota
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 23f * scale.coerceIn(0.7f, 1.2f)
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                        setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
                    }
                    val quotaText = item.activeLoan?.let { CurrencyUtils.format(it.quotaAmount) } ?: "OK"
                    val statusSymbol = if (isCollected) "✓ COBRADO" else quotaText
                    drawContext.canvas.nativeCanvas.drawText(
                        "${index + 1}. ${item.client.name.take(12)} ($statusSymbol)",
                        pinOffset.x,
                        pinOffset.y + 30f * scale.coerceIn(0.7f, 1.8f),
                        labelPaint
                    )
                }
            }

            // 5. Draw Live Collector Position & Directional Bearing Cone
            currentLocation?.let { loc ->
                val myOffset = toCanvasOffset(loc.latitude, loc.longitude)

                if (isTrackingActive) {
                    // Radar pulse wave
                    drawCircle(
                        color = GeometricAccentLight.copy(alpha = pulseAlpha),
                        radius = pulseRadius * scale.coerceIn(0.8f, 2.5f),
                        center = myOffset
                    )
                }

                // Accuracy circle
                if (accuracy > 0f) {
                    val accRadius = (accuracy * 2f * scale).coerceIn(16f, 90f)
                    drawCircle(
                        color = Color(0xFF0D9488).copy(alpha = 0.15f),
                        radius = accRadius,
                        center = myOffset
                    )
                    drawCircle(
                        color = Color(0xFF14B8A6).copy(alpha = 0.35f),
                        radius = accRadius,
                        center = myOffset,
                        style = Stroke(width = 1.5f)
                    )
                }

                // Directional Bearing Cone if moving or bearing available
                val currentHeading = if (loc.hasBearing()) loc.bearing else bearing
                rotate(degrees = currentHeading, pivot = myOffset) {
                    val conePath = Path().apply {
                        moveTo(myOffset.x, myOffset.y - (34f * scale.coerceIn(0.8f, 2f)))
                        lineTo(myOffset.x - (16f * scale.coerceIn(0.8f, 2f)), myOffset.y + (12f * scale.coerceIn(0.8f, 2f)))
                        lineTo(myOffset.x, myOffset.y + (4f * scale.coerceIn(0.8f, 2f)))
                        lineTo(myOffset.x + (16f * scale.coerceIn(0.8f, 2f)), myOffset.y + (12f * scale.coerceIn(0.8f, 2f)))
                        close()
                    }
                    drawPath(
                        path = conePath,
                        color = Color(0xFF14B8A6).copy(alpha = 0.95f)
                    )
                }

                // Center solid indicator
                drawCircle(
                    color = SlateNavy,
                    radius = 12f * scale.coerceIn(0.8f, 2f),
                    center = myOffset
                )
                drawCircle(
                    color = Color.White,
                    radius = 6f * scale.coerceIn(0.8f, 2f),
                    center = myOffset
                )

                // Collector Label
                val myPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.CYAN
                    textSize = 25f * scale.coerceIn(0.7f, 1.2f)
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                    setShadowLayer(8f, 0f, 2f, android.graphics.Color.BLACK)
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "📍 MI POSICIÓN EN VIVO",
                    myOffset.x,
                    myOffset.y - 38f * scale.coerceIn(0.8f, 1.8f),
                    myPaint
                )
            }
        }

        // Top City Banner Badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = SlateNavy.copy(alpha = 0.92f),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationCity,
                    contentDescription = null,
                    tint = GeometricAccentLight,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "BARRANQUILLA, ATLÁNTICO • MAPA GPS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Overlay Navigation Controls (Shifted down to avoid overlapping with top status chips/bubbles)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 78.dp, end = 14.dp),
            shape = RoundedCornerShape(18.dp),
            color = SlateNavy.copy(alpha = 0.94f),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = {
                        scale = (scale * 1.35f).coerceAtMost(20.0f)
                        onAutoFollowChanged(false)
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Aumentar Zoom", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        scale = (scale / 1.35f).coerceAtLeast(0.15f)
                        onAutoFollowChanged(false)
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Disminuir Zoom", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        scale = 1.0f
                        panOffset = Offset.Zero
                        onAutoFollowChanged(true)
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (autoFollow) GeometricAccent.copy(alpha = 0.35f) else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        if (autoFollow) Icons.Default.GpsFixed else Icons.Default.MyLocation,
                        contentDescription = "Centrar en mi ubicación",
                        tint = if (autoFollow) GeometricAccentLight else Slate400
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawBarranquillaBaseMap(
    toOffset: (lat: Double, lng: Double) -> Offset,
    scale: Float
) {
    // 1. Draw Río Magdalena Corridor (East Waterway)
    val riverPath = Path().apply {
        val r1 = toOffset(10.9400, -74.7580)
        val r2 = toOffset(10.9650, -74.7640)
        val r3 = toOffset(10.9850, -74.7720)
        val r4 = toOffset(11.0150, -74.7800)
        val r5 = toOffset(11.0350, -74.7850)

        moveTo(r1.x, r1.y)
        lineTo(r2.x, r2.y)
        lineTo(r3.x, r3.y)
        lineTo(r4.x, r4.y)
        lineTo(r5.x, r5.y)
    }

    drawPath(
        path = riverPath,
        color = Color(0xFF0F3E5D).copy(alpha = 0.85f),
        style = Stroke(
            width = 38f * scale.coerceIn(0.6f, 2.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    drawPath(
        path = riverPath,
        color = Color(0xFF1E6F9F).copy(alpha = 0.45f),
        style = Stroke(
            width = 44f * scale.coerceIn(0.6f, 2.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // 2. Draw Major Barranquilla Road Arteries
    fun drawAvenue(points: List<Pair<Double, Double>>, color: Color, widthDp: Float) {
        if (points.size < 2) return
        val p = Path()
        val start = toOffset(points[0].first, points[0].second)
        p.moveTo(start.x, start.y)
        for (i in 1 until points.size) {
            val pt = toOffset(points[i].first, points[i].second)
            p.lineTo(pt.x, pt.y)
        }
        drawPath(
            path = p,
            color = color,
            style = Stroke(
                width = widthDp * scale.coerceIn(0.6f, 2.2f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }

    val primaryRoadColor = Color(0xFF334155)
    val secondaryRoadColor = Color(0xFF1E293B)
    val highwayColor = Color(0xFF475569)

    // Av. Murillo (Calle 45) - Transmetro Troncal
    drawAvenue(
        listOf(
            Pair(10.9420, -74.7950),
            Pair(10.9525, -74.7810),
            Pair(10.9612, -74.7865),
            Pair(10.9700, -74.7780)
        ),
        highwayColor,
        5f
    )

    // Vía 40 (Zona Industrial & Malecón)
    drawAvenue(
        listOf(
            Pair(10.9780, -74.7800),
            Pair(10.9850, -74.7880),
            Pair(10.9980, -74.7980),
            Pair(11.0200, -74.8150)
        ),
        primaryRoadColor,
        4.5f
    )

    // Carrera 46 (Av. Olaya Herrera)
    drawAvenue(
        listOf(
            Pair(10.9750, -74.7780),
            Pair(10.9880, -74.7920),
            Pair(11.0000, -74.8050),
            Pair(11.0220, -74.8250)
        ),
        highwayColor,
        4.5f
    )

    // Carrera 38
    drawAvenue(
        listOf(
            Pair(10.9600, -74.7750),
            Pair(10.9780, -74.7790),
            Pair(10.9900, -74.8020),
            Pair(11.0100, -74.8300)
        ),
        primaryRoadColor,
        3.5f
    )

    // Calle 30 (Hacia Soledad / Aeropuerto)
    drawAvenue(
        listOf(
            Pair(10.9700, -74.7700),
            Pair(10.9500, -74.7740),
            Pair(10.9350, -74.7780)
        ),
        primaryRoadColor,
        4f
    )

    // Calle 72 & Calle 84 (Norte de Barranquilla)
    drawAvenue(
        listOf(
            Pair(10.9950, -74.7920),
            Pair(11.0020, -74.8080),
            Pair(11.0100, -74.8250)
        ),
        secondaryRoadColor,
        3f
    )
    drawAvenue(
        listOf(
            Pair(11.0020, -74.8000),
            Pair(11.0080, -74.8180),
            Pair(11.0180, -74.8320)
        ),
        secondaryRoadColor,
        3f
    )

    // 3. Draw Barranquilla Sector Labels
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(160, 148, 163, 184)
        textSize = 19f * scale.coerceIn(0.6f, 1.3f)
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        letterSpacing = 0.15f
    }

    val waterPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(190, 56, 189, 248)
        textSize = 18f * scale.coerceIn(0.6f, 1.2f)
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Sectores
    val sectors = listOf(
        Pair(Pair(10.9995, -74.8015), "EL PRADO"),
        Pair(Pair(10.9780, -74.7790), "CENTRO / PASEO BOLÍVAR"),
        Pair(Pair(10.9885, -74.7932), "BOSTON"),
        Pair(Pair(10.9612, -74.7865), "SAN JOSÉ / MURILLO"),
        Pair(Pair(11.0080, -74.8180), "ALTO PRADO / RIOMAR"),
        Pair(Pair(10.9525, -74.7810), "LA VICTORIA"),
        Pair(Pair(10.9450, -74.7700), "SIMÓN BOLÍVAR / SOLEDAD")
    )

    sectors.forEach { (coord, name) ->
        val pos = toOffset(coord.first, coord.second)
        drawContext.canvas.nativeCanvas.drawText(name, pos.x, pos.y, textPaint)
    }

    val maldeconPos = toOffset(10.9950, -74.7730)
    drawContext.canvas.nativeCanvas.drawText("🌊 RÍO MAGDALENA / GRAN MALECÓN", maldeconPos.x, maldeconPos.y, waterPaint)
}

private fun DrawScope.drawRadarGrid(width: Float, height: Float, pan: Offset, scale: Float) {
    val gridSize = 64f * scale.coerceIn(0.5f, 2.5f)
    val startX = (pan.x % gridSize)
    val startY = (pan.y % gridSize)

    var x = startX
    while (x < width) {
        drawLine(
            color = Color(0xFF1E293B).copy(alpha = 0.45f),
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f
        )
        x += gridSize
    }

    var y = startY
    while (y < height) {
        drawLine(
            color = Color(0xFF1E293B).copy(alpha = 0.45f),
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f
        )
        y += gridSize
    }
}
