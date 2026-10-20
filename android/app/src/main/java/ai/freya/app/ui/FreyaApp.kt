package ai.freya.app.ui

import ai.freya.app.FreyaApplication
import ai.freya.app.ui.navigation.Routes
import ai.freya.app.ui.screens.auth.LoginScreen
import ai.freya.app.ui.screens.auth.RegisterScreen
import ai.freya.app.ui.screens.home.HomeScreen
import ai.freya.app.ui.screens.interview.InterviewScreen
import ai.freya.app.ui.screens.jobs.JobsScreen
import ai.freya.app.ui.screens.projects.ProjectsScreen
import ai.freya.app.ui.screens.report.ReportScreen
import ai.freya.app.ui.screens.search.SearchScreen
import ai.freya.app.ui.screens.settings.MemoryScreen
import ai.freya.app.ui.screens.settings.SettingsScreen
import ai.freya.app.ui.screens.simulation.SimulationScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute != null && currentRoute in Routes.bottomLevel) {
                FreyaBottomBar(navController, currentRoute)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (isAuthed == true) Routes.HOME else Routes.LOGIN,
            modifier = Modifier.padding(padding),
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
                HomeScreen(onNavigateToSearch = { navController.navigate(Routes.SEARCH) })
            }
            composable(Routes.PROJECTS) {
                ProjectsScreen(onOpenProject = { id -> navController.navigate(Routes.simulation(id)) })
            }
            composable(Routes.SEARCH) { SearchScreen() }
            composable(Routes.JOBS) { JobsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenMemory = { navController.navigate(Routes.MEMORY) })
            }
            composable(Routes.MEMORY) {
                MemoryScreen(onBack = { navController.popBackStack() })
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
    }
}

@Composable
private fun FreyaBottomBar(navController: NavHostController, currentRoute: String?) {
    val items = listOf(
        BottomNavItem(Routes.HOME, "Home", Icons.Filled.Home),
        BottomNavItem(Routes.PROJECTS, "Projects", Icons.Filled.Folder),
        BottomNavItem(Routes.SEARCH, "Search", Icons.Filled.Search),
        BottomNavItem(Routes.JOBS, "Tasks", Icons.Filled.Notifications),
        BottomNavItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
    )
    NavigationBar {
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
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)
