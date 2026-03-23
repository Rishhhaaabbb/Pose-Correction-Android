package com.posemonitor2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.posemonitor2.screen.*

// ── Bottom-nav destinations ───────────────────────────────────────────────────

sealed class UserRoute(val route: String, val label: String, val icon: ImageVector) {
    object Home      : UserRoute("home",      "HOME",      Icons.Outlined.Home)
    object Exercises : UserRoute("exercises", "WORKOUT",  Icons.Outlined.FitnessCenter)
    object Progress  : UserRoute("progress",  "STATS",     Icons.Outlined.BarChart)
    object Profile   : UserRoute("profile",   "PROFILE",   Icons.Outlined.Person)
}

private val bottomNavItems = listOf(
    UserRoute.Home,
    UserRoute.Exercises,
    UserRoute.Progress,
    UserRoute.Profile,
)

// ── Activity ──────────────────────────────────────────────────────────────────

class UserFlowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                UserFlowNavigation()
            }
        }
    }
}

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun UserFlowNavigation() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = Color(0xFFF2EEE4),
        bottomBar = { UserBottomNavBar(navController) },
    ) { innerPadding ->

        NavHost(
            navController    = navController,
            startDestination = UserRoute.Home.route,
            modifier         = Modifier.padding(innerPadding),
        ) {
            composable(UserRoute.Home.route) {
                UserHomeScreen(navController = navController)
            }
            composable(UserRoute.Exercises.route) {
                ExercisesScreen(navController = navController)
            }
            composable(UserRoute.Progress.route) {
                ProgressScreen()
            }
            composable(UserRoute.Profile.route) {
                ProfileScreen()
            }
            // exercise_detail/{fileName}/{isAsset}
            composable("exercise_detail/{fileName}/{isAsset}") { backStack ->
                val fileName = backStack.arguments?.getString("fileName") ?: ""
                val isAsset  = backStack.arguments?.getString("isAsset")?.toBoolean() ?: false
                ExerciseDetailScreen(
                    fileName = fileName,
                    isAsset  = isAsset,
                    onBack   = { navController.popBackStack() },
                )
            }
        }
    }
}

// ── Bottom nav bar ────────────────────────────────────────────────────────────

@Composable
private fun UserBottomNavBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route

    NavigationBar(containerColor = Color.White) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick  = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            popUpTo(UserRoute.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                },
                icon  = {
                    Icon(
                        if (selected) when (item) {
                            UserRoute.Home      -> Icons.Filled.Home
                            UserRoute.Exercises -> Icons.Filled.FitnessCenter
                            UserRoute.Progress  -> Icons.Filled.BarChart
                            UserRoute.Profile   -> Icons.Filled.Person
                            else                -> item.icon
                        } else item.icon,
                        contentDescription = item.label,
                    )
                },
                label  = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = Color(0xFFE85A2E),
                    selectedTextColor   = Color(0xFFE85A2E),
                    indicatorColor      = Color(0xFFF2EEE4),
                    unselectedIconColor = Color(0xFF888888),
                    unselectedTextColor = Color(0xFF888888),
                ),
            )
        }
    }
}
