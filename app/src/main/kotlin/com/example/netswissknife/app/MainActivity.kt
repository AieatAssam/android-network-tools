package com.example.netswissknife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.netswissknife.app.ui.navigation.AppNavHost
import com.example.netswissknife.app.ui.navigation.AppNavigationViewModel
import com.example.netswissknife.app.ui.navigation.MoreToolsSheet
import com.example.netswissknife.app.ui.navigation.NavRoutes
import com.example.netswissknife.app.ui.theme.NetSwissKnifeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NetSwissKnifeTheme {
                val navController = rememberNavController()
                NetSwissKnifeApp(navController)
            }
        }
    }
}

@Composable
fun NetSwissKnifeApp(navController: NavHostController) {
    val navViewModel: AppNavigationViewModel = hiltViewModel()
    val pinnedRoutes by navViewModel.pinnedRoutes.collectAsState()
    var showMoreSheet by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            AppBottomNavigationBar(
                navController  = navController,
                pinnedRoutes   = pinnedRoutes,
                onMoreClick    = { showMoreSheet = true }
            )
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier      = Modifier.padding(innerPadding)
        )
    }

    if (showMoreSheet) {
        MoreToolsSheet(
            pinnedRoutes = pinnedRoutes,
            onNavigate   = { route ->
                showMoreSheet = false
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            onTogglePin  = navViewModel::togglePin,
            maxPinned    = AppNavigationViewModel.MAX_PINNED,
            onDismiss    = { showMoreSheet = false },
            onDebugLogsClick = {
                showMoreSheet = false
                navController.navigate(NavRoutes.DebugLogs.route) {
                    launchSingleTop = true
                }
            },
        )
    }
}

@Composable
private fun AppBottomNavigationBar(
    navController: NavHostController,
    pinnedRoutes: List<String>,
    onMoreClick: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val pinnedTools = pinnedRoutes.mapNotNull { route ->
        NavRoutes.allTools.find { it.route == route }
    }

    NavigationBar {
        // Home is always first
        NavigationBarItem(
            selected = currentRoute == NavRoutes.Home.route,
            onClick  = {
                navController.navigate(NavRoutes.Home.route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            icon  = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_home)) }
        )

        // Pinned tools (dynamic, up to MAX_PINNED)
        pinnedTools.forEach { tool ->
            NavigationBarItem(
                selected = currentRoute == tool.route,
                onClick  = {
                    navController.navigate(tool.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
                icon  = { Icon(tool.icon, contentDescription = null) },
                label = { Text(tool.shortLabel) }
            )
        }

        // "More" is always last
        NavigationBarItem(
            selected = false,
            onClick  = onMoreClick,
            icon     = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
            label    = { Text(stringResource(R.string.nav_more)) }
        )
    }
}
