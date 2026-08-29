package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.QrType
import com.example.model.TransferRole
import com.example.transfer.FileTransferViewModel
import com.example.ui.QrViewModel
import com.example.ui.screens.CustomizerScreen
import com.example.ui.screens.FileTransferScreen
import com.example.ui.screens.GeneratorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PrivacyScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.QRStudioTheme

enum class AppDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val showInBottomBar: Boolean = true
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    CREATE("Create", Icons.Filled.AddCircle, Icons.Outlined.AddCircle),
    TRANSFER("Transfer", Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz),
    SCAN("Scan", Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner),
    CUSTOMIZE("Style", Icons.Filled.Palette, Icons.Outlined.Palette),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History),
    PRIVACY("Privacy", Icons.Filled.Shield, Icons.Outlined.Shield, showInBottomBar = false)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: QrViewModel = viewModel()
            val transferViewModel: FileTransferViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            QRStudioTheme(themeMode = themeMode) {
                QRStudioApp(
                    viewModel = viewModel,
                    transferViewModel = transferViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRStudioApp(
    viewModel: QrViewModel,
    transferViewModel: FileTransferViewModel
) {
    var currentDestination by remember { mutableStateOf(AppDestination.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            if (currentDestination != AppDestination.SCAN) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = currentDestination.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        if (currentDestination != AppDestination.PRIVACY) {
                            IconButton(
                                onClick = { currentDestination = AppDestination.PRIVACY },
                                modifier = Modifier.testTag("top_bar_privacy_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = "Privacy & Offline Info"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("app_navigation_bar")
            ) {
                AppDestination.values().filter { it.showInBottomBar }.forEach { destination ->
                    val isSelected = currentDestination == destination
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.title
                            )
                        },
                        label = {
                            Text(
                                text = destination.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("nav_item_${destination.name.lowercase()}")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen_transition"
            ) { destination ->
                when (destination) {
                    AppDestination.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToCreate = { type ->
                            if (type != null) viewModel.selectQrType(type)
                            currentDestination = AppDestination.CREATE
                        },
                        onNavigateToScan = { currentDestination = AppDestination.SCAN },
                        onNavigateToTransfer = { role ->
                            if (role != null) {
                                transferViewModel.selectRole(role)
                            } else {
                                transferViewModel.resetToHub()
                            }
                            currentDestination = AppDestination.TRANSFER
                        },
                        onNavigateToHistory = { currentDestination = AppDestination.HISTORY },
                        onNavigateToPrivacy = {
                            currentDestination = AppDestination.PRIVACY
                        }
                    )
                    AppDestination.CREATE -> GeneratorScreen(
                        viewModel = viewModel,
                        onNavigateToCustomize = { currentDestination = AppDestination.CUSTOMIZE }
                    )
                    AppDestination.TRANSFER -> FileTransferScreen(
                        transferViewModel = transferViewModel
                    )
                    AppDestination.CUSTOMIZE -> CustomizerScreen(
                        viewModel = viewModel
                    )
                    AppDestination.SCAN -> ScannerScreen(
                        viewModel = viewModel,
                        onNavigateToCreateWithContent = { content ->
                            currentDestination = AppDestination.CREATE
                        }
                    )
                    AppDestination.HISTORY -> HistoryScreen(
                        viewModel = viewModel,
                        onNavigateToCreate = { currentDestination = AppDestination.CREATE }
                    )
                    AppDestination.PRIVACY -> PrivacyScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
