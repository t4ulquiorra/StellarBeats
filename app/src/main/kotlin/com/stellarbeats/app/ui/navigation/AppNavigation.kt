package com.stellarbeats.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stellarbeats.app.ui.home.HomeScreen
import com.stellarbeats.app.ui.player.BottomSheetPlayer
import com.stellarbeats.app.ui.player.PlayerViewModel
import com.stellarbeats.database.entities.LocalTrack

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val NAV_ITEMS = listOf(
    NavItem("home", "Home", Icons.Default.Home),
    NavItem("search", "Search", Icons.Default.Search),
    NavItem("library", "Library", Icons.Default.LibraryMusic),
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomNav = NAV_ITEMS.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                ) {
                    NAV_ITEMS.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(
                navController = navController,
                startDestination = "home",
            ) {
                composable("home") {
                    HomeScreen(
                        onTrackClick = { track ->
                            playerViewModel.play(track)
                            playerViewModel.expandSheet()
                        },
                    )
                }
                composable("search") {
                    // SearchScreen placeholder
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            text = "Search",
                            modifier = Modifier.padding(24.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                composable("library") {
                    // LibraryScreen placeholder
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            text = "Library",
                            modifier = Modifier.padding(24.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Player overlay (above content, below status bar)
            if (playerState.currentTrack != null) {
                BottomSheetPlayer(viewModel = playerViewModel)
            }
        }
    }
}
