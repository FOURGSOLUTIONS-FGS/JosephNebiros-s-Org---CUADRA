package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.CashReportScreen
import com.example.ui.screens.ClientsScreen
import com.example.ui.screens.LiveMapScreen
import com.example.ui.screens.RouteScreen
import com.example.ui.theme.GeometricAccent
import com.example.ui.theme.GeometricAccentContainer
import com.example.ui.theme.GeometricAccentLight
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Slate400
import com.example.ui.theme.SlateNavy
import com.example.ui.viewmodel.CobranzaViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CobradorAppMain()
            }
        }
    }
}

@Composable
fun CobradorAppMain(viewModel: CobranzaViewModel = viewModel()) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Runtime Permission Launcher for GPS Location and Notifications (Foreground Service)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled reactively
    }

    fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            permissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        checkAndRequestPermissions()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_navigation_bar"),
                containerColor = SlateNavy,
                tonalElevation = 8.dp
            ) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SlateNavy,
                    selectedTextColor = GeometricAccentLight,
                    indicatorColor = GeometricAccentLight,
                    unselectedIconColor = Slate400,
                    unselectedTextColor = Slate400
                )

                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            if (selectedTab == 0) Icons.Filled.Route else Icons.Outlined.Route,
                            contentDescription = "Ruta"
                        )
                    },
                    label = { Text("Ruta", fontSize = 11.sp) },
                    colors = itemColors,
                    modifier = Modifier.testTag("nav_item_route")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            if (selectedTab == 1) Icons.Filled.People else Icons.Outlined.People,
                            contentDescription = "Clientes"
                        )
                    },
                    label = { Text("Clientes", fontSize = 11.sp) },
                    colors = itemColors,
                    modifier = Modifier.testTag("nav_item_clients")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            if (selectedTab == 2) Icons.Filled.Map else Icons.Outlined.Map,
                            contentDescription = "Mapa GPS"
                        )
                    },
                    label = { Text("Mapa GPS", fontSize = 11.sp) },
                    colors = itemColors,
                    modifier = Modifier.testTag("nav_item_map")
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            if (selectedTab == 3) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet,
                            contentDescription = "Caja"
                        )
                    },
                    label = { Text("Caja", fontSize = 11.sp) },
                    colors = itemColors,
                    modifier = Modifier.testTag("nav_item_cash")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> RouteScreen(
                    viewModel = viewModel,
                    onRequestLocationPermission = { checkAndRequestPermissions() }
                )
                1 -> ClientsScreen(viewModel = viewModel)
                2 -> LiveMapScreen(
                    viewModel = viewModel,
                    onRequestLocationPermission = { checkAndRequestPermissions() }
                )
                3 -> CashReportScreen(viewModel = viewModel)
            }
        }
    }
}
