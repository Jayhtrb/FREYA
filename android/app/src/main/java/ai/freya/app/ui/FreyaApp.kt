package ai.freya.app.ui

import ai.freya.app.FreyaApplication
import ai.freya.app.domain.ToolPolicy
import ai.freya.app.ui.components.ApprovalSheet
import ai.freya.app.ui.components.FreyaBackdrop
import ai.freya.app.ui.navigation.Routes
import ai.freya.app.ui.screens.auth.LoginScreen
import ai.freya.app.ui.screens.auth.RegisterScreen
import ai.freya.app.ui.screens.control.ControlCenterScreen
import ai.freya.app.ui.screens.home.HomeScreen
import ai.freya.app.ui.screens.interview.InterviewScreen
import ai.freya.app.ui.screens.jobs.JobsScreen
import ai.freya.app.ui.screens.power.PowerScreen
import ai.freya.app.ui.screens.projects.ProjectsScreen
import ai.freya.app.ui.screens.report.ReportScreen
import ai.freya.app.ui.screens.search.SearchScreen
import ai.freya.app.ui.screens.settings.MemoryScreen
import ai.freya.app.ui.screens.settings.SettingsScreen
import ai.freya.app.ui.screens.simulation.SimulationScreen
import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.Motion
import ai.freya.app.ui.theme.Radius
import ai.freya.app.ui.theme.Space
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun FreyaApp() {
    val app = LocalContext.current.applicationContext as FreyaApplication
    val isAuthed by app.container.authRepository.isAuthenticated()
        .collectAsStateWithLifecycle(initialValue = false)

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Single source of truth for auth-driven navigation (handles sign-in and sign-out).
    LaunchedEffect(isAuthed) {
        val target = if (isAuthed == true) Routes.HOME else Routes.LOGIN
        val current = navController.currentDestination?.route
        val wrongSide = if (isAuthed == true) {
            current == null || current.startsWith("auth/")
        } else {
            current == null || !current.startsWith("auth/")
        }
        if (wrongSide) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun go(route: String) = navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    FreyaBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (currentRoute != null && currentRoute in Routes.bottomLevel) {
                    FreyaBottomBar(navController, currentRoute)
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                NavHost(
                    navController = navController,
                    startDestination = if (isAuthed == true) Routes.HOME else Routes.LOGIN,
                    enterTransition = { fadeIn(tween(Motion.layout, easing = Motion.emphasized)) },
                    exitTransition = { fadeOut(tween(Motion.state)) },
                ) {
                    composable(Routes.LOGIN) {
                        LoginScreen(
                            onLoggedIn = { navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                            onGoToRegister = { navController.navigate(Routes.REGISTER) },
                        )
                    }
                    composable(Routes.REGISTER) {
                        RegisterScreen(
                            onRegistered = { navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                            onBackToLogin = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.HOME) {
                        HomeScreen(
                            onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                            onOpenControlCenter = { go(Routes.CONTROL) },
                            onOpenPower = { navController.navigate(Routes.POWER) },
                        )
                    }
                    composable(Routes.CONTROL) {
                        ControlCenterScreen(
                            onBack = null,
                            onOpenPower = { navController.navigate(Routes.POWER) },
                            onOpenMemory = { navController.navigate(Routes.MEMORY) },
                        )
                    }
                    composable(Routes.PROJECTS) {
                        ProjectsScreen(onOpenProject = { id -> navController.navigate(Routes.simulation(id)) })
                    }
                    composable(Routes.SEARCH) { SearchScreen() }
                    composable(Routes.JOBS) { JobsScreen() }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onOpenMemory = { navController.navigate(Routes.MEMORY) },
                            onOpenPower = { navController.navigate(Routes.POWER) },
                            onOpenControlCenter = { go(Routes.CONTROL) },
                        )
                    }
                    composable(Routes.MEMORY) {
                        MemoryScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.POWER) {
                        PowerScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = Routes.SIMULATION,
                        arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
                    ) { entry ->
                        val projectId = entry.arguments?.getString("projectId").orEmpty()
                        SimulationScreen(
                            projectId = projectId,
                            onBack = { navController.popBackStack() },
                            onOpenReport = { sid -> navController.navigate(Routes.report(sid)) },
                            onOpenInterview = { sid -> navController.navigate(Routes.interview(sid)) },
                        )
                    }
                    composable(
                        route = Routes.REPORT,
                        arguments = listOf(navArgument("simulationId") { type = NavType.StringType }),
                    ) { entry ->
                        val simulationId = entry.arguments?.getString("simulationId").orEmpty()
                        ReportScreen(simulationId = simulationId, onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = Routes.INTERVIEW,
                        arguments = listOf(navArgument("simulationId") { type = NavType.StringType }),
                    ) { entry ->
                        val simulationId = entry.arguments?.getString("simulationId").orEmpty()
                        InterviewScreen(simulationId = simulationId, onBack = { navController.popBackStack() })
                    }
                }

                if (isAuthed == true) {
                    SessionOverlays(
                        showTaskPill = currentRoute != Routes.HOME,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

/** App-wide session UI: the approval gate and a running-task pill on non-Home screens. */
@Composable
private fun SessionOverlays(showTaskPill: Boolean, modifier: Modifier) {
    val session = (LocalContext.current.applicationContext as FreyaApplication).container.session
    val pending by session.pendingApproval.collectAsStateWithLifecycle()
    val activeTool by session.activeTool.collectAsStateWithLifecycle()
    val c = FreyaTheme.colors

    AnimatedVisibility(
        visible = showTaskPill && activeTool != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier.statusBarsPadding().padding(top = Space.sm),
    ) {
        Row(
            Modifier
                .clip(Radius.pill)
                .background(if (c.isDark) Color(0xF00E1222) else Color(0xF0FFFFFF))
                .border(1.dp, c.ice.copy(alpha = 0.5f), Radius.pill)
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), color = c.ice, strokeWidth = 2.dp)
            Spacer(Modifier.width(Space.sm))
            Text(
                "Running · ${ToolPolicy.info(activeTool.orEmpty()).title}",
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = session::cancelTool) { Text("Cancel", color = c.error) }
        }
    }

    pending?.let {
        ApprovalSheet(pending = it, onApprove = session::approve, onDeny = session::deny)
    }
}

@Composable
private fun FreyaBottomBar(navController: NavHostController, currentRoute: String?) {
    val c = FreyaTheme.colors
    val items = listOf(
        BottomNavItem(Routes.HOME, "FREYA", Icons.Outlined.Hub),
        BottomNavItem(Routes.PROJECTS, "Projects", Icons.Outlined.Folder),
        BottomNavItem(Routes.CONTROL, "Control", Icons.Outlined.Tune),
        BottomNavItem(Routes.JOBS, "Tasks", Icons.Outlined.TaskAlt),
        BottomNavItem(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
    )
    NavigationBar(
        containerColor = if (c.isDark) Color(0xCC070A14) else Color(0xE6FFFFFF),
        tonalElevation = 0.dp,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(imageVector = item.icon, contentDescription = null) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.cyan,
                    selectedTextColor = c.cyan,
                    indicatorColor = c.cyan.copy(alpha = 0.12f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)
