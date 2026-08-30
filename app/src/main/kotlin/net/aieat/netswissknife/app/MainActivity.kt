package net.aieat.netswissknife.app

import android.os.Bundle
import net.aieat.netswissknife.app.BuildConfig
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.aieat.netswissknife.app.ui.navigation.AppNavHost
import net.aieat.netswissknife.app.ui.navigation.AppNavigationViewModel
import net.aieat.netswissknife.app.ui.navigation.MoreToolsSheet
import net.aieat.netswissknife.app.ui.navigation.NavRoutes
import net.aieat.netswissknife.app.ui.screens.onboarding.OnboardingSheet
import net.aieat.netswissknife.app.ui.screens.onboarding.OnboardingViewModel
import net.aieat.netswissknife.app.ui.screens.settings.SettingsViewModel
import net.aieat.netswissknife.app.ui.theme.NetSwissKnifeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // Draw behind the system bars on every supported API level.
        //
        // NOTE: do NOT use androidx.activity's enableEdgeToEdge() here. It calls
        // Window.setStatusBarColor / setNavigationBarColor and sets
        // LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES, all deprecated in Android 15,
        // which Play Console flags as "deprecated APIs for edge-to-edge".
        // setDecorFitsSystemWindows() is the non-deprecated equivalent; the
        // transparent system bar colours needed below API 35 come from themes.xml,
        // and the bar icon appearance is set in NetSwissKnifeTheme.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeOverride by settingsViewModel.themeOverride.collectAsStateWithLifecycle()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsStateWithLifecycle()
            val darkTheme = when (themeOverride) {
                "LIGHT" -> false
                "DARK"  -> true
                else    -> isSystemInDarkTheme()
            }
            NetSwissKnifeTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                val navController = rememberNavController()
                NetSwissKnifeApp(navController)
            }
        }
    }
}

@Composable
fun NetSwissKnifeApp(navController: NavHostController) {
    val navViewModel: AppNavigationViewModel = hiltViewModel()
    val pinnedRoutes by navViewModel.pinnedRoutes.collectAsStateWithLifecycle()
    var showMoreSheet by remember { mutableStateOf(false) }
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val shouldShowOnboarding by onboardingViewModel.shouldShowOnboarding.collectAsStateWithLifecycle()

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
            // The bottom bar's window insets already include the IME, so
            // innerPadding accounts for the keyboard — no imePadding() here.
            modifier      = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
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
            onSettingsClick = {
                showMoreSheet = false
                navController.navigate(NavRoutes.Settings.route) {
                    launchSingleTop = true
                }
            },
            onDebugLogsClick = if (BuildConfig.DEBUG) ({
                showMoreSheet = false
                navController.navigate(NavRoutes.DebugLogs.route) {
                    launchSingleTop = true
                }
            }) else ({}),
        )
    }

    if (shouldShowOnboarding) {
        OnboardingSheet(onDismiss = { onboardingViewModel.completeOnboarding() })
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

    // union() takes the larger inset per side, so the bar sits above the keyboard
    // when it is open and above the navigation bar otherwise. Modifier.imePadding()
    // would instead stack on top of the bar's own navigationBars inset and leave a
    // navigation-bar-sized gap while the IME is showing.
    NavigationBar(windowInsets = NavigationBarDefaults.windowInsets.union(WindowInsets.ime)) {
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

        // "More" is always last — highlighted when the current screen is not Home and not a pinned tool
        val isMoreSelected = currentRoute != null &&
            currentRoute != NavRoutes.Home.route &&
            pinnedTools.none { it.route == currentRoute }
        NavigationBarItem(
            selected = isMoreSelected,
            onClick  = onMoreClick,
            icon     = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
            label    = { Text(stringResource(R.string.nav_more)) }
        )
    }
}
