package com.posemonitor2

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.posemonitor2.R
import com.posemonitor2.screen.*
import com.posemonitor2.onboarding.OnboardingActivity
import com.posemonitor2.onboarding.OnboardingManager
import com.posemonitor2.settings.AppLocaleManager
import com.posemonitor2.ui.theme.PoseMonitorAppTheme
import com.posemonitor2.auth.AuthViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.compose.BackHandler
import android.content.Intent

// ── Bottom-nav destinations ───────────────────────────────────────────────────

sealed class UserRoute(val route: String, val labelResId: Int, val icon: ImageVector) {
    object Home      : UserRoute("home",      R.string.nav_home,    Icons.Outlined.Home)
    object Exercises : UserRoute("exercises", R.string.nav_workout, Icons.Outlined.FitnessCenter)
    object CustomWorkout : UserRoute("custom_workout", R.string.nav_custom_workout, Icons.Outlined.EmojiEvents)
    object Progress  : UserRoute("progress",  R.string.nav_stats,   Icons.Outlined.BarChart)
    object Profile   : UserRoute("profile",   R.string.nav_profile, Icons.Outlined.Person)
    object Library   : UserRoute("library",   R.string.nav_library, Icons.Outlined.VideoLibrary)
    object Graphs    : UserRoute("graphs",    R.string.nav_graphs,  Icons.Outlined.AutoGraph)
    object AdminStats : UserRoute("admin_stats", R.string.nav_stats, Icons.Outlined.Analytics)
}

private val userBottomNavItems = listOf(
    UserRoute.Home,
    UserRoute.Exercises,
    UserRoute.Progress,
    UserRoute.Profile,
)

private val adminBottomNavItems = listOf(
    UserRoute.Home,
    UserRoute.Exercises,
    UserRoute.Progress,
    UserRoute.Graphs,
    UserRoute.Profile,
)

// ── Activity ──────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class UserFlowActivity : ComponentActivity() {

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoseMonitorAppTheme {
                UserFlowNavigation()
            }
        }
    }
}

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun UserFlowNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsState()

    val isAdmin = remember(authState.userEmail) {
        authState.userEmail?.let {
            val normalized = it.trim().lowercase()
            normalized == "admin" || normalized == "admin@poseapp.com"
        } ?: false
    }

    LaunchedEffect(authState.isAuthenticated) {
        if (!authState.isAuthenticated) {
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
            activity?.finish()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    
    // If on Home and press back, logout
    BackHandler(enabled = currentRoute == UserRoute.Home.route) {
        authViewModel.signOut()
    }

    LaunchedEffect(Unit) {
        val manager = OnboardingManager(context)
        if (manager.shouldShowOnboarding()) {
            context.startActivity(OnboardingActivity.createIntent(context, OnboardingActivity.MODE_INTRO))
        }
    }

    // Handle deep links from MainActivity
    LaunchedEffect(activity?.intent) {
        val targetRoute = activity?.intent?.getStringExtra("TARGET_ROUTE")
        if (targetRoute != null) {
            activity.intent.removeExtra("TARGET_ROUTE")
            navController.navigate(targetRoute) {
                popUpTo(UserRoute.Home.route)
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFFF2EEE4),
        bottomBar = {
            UserBottomNavBar(navController, if (isAdmin) adminBottomNavItems else userBottomNavItems)
        },
    ) { innerPadding ->

        NavHost(
            navController    = navController,
            startDestination = UserRoute.Home.route,
            modifier         = Modifier.padding(innerPadding),
        ) {
            composable(UserRoute.Home.route) {
                UserHomeScreen(
                    navController = navController,
                    onOpenHelp = {
                        context.startActivity(
                            OnboardingActivity.createIntent(context, OnboardingActivity.MODE_FULL_TOUR)
                        )
                    },
                )
            }
            composable(UserRoute.Exercises.route) {
                ExercisesScreen(navController = navController)
            }
            composable(UserRoute.CustomWorkout.route) {
                CustomWorkoutScreen(navController = navController)
            }
            composable(CUSTOM_WORKOUT_EDITOR_ROUTE_PATTERN) {
                CustomWorkoutEditorScreen(navController = navController)
            }
            composable(UserRoute.Progress.route) {
                ProgressScreen(navController = navController)
            }
            composable(UserRoute.Library.route) {
                LibraryScreen(navController = navController)
            }
            composable(UserRoute.Graphs.route) {
                GraphsScreen(navController = navController)
            }
            composable(UserRoute.AdminStats.route) {
                AdminStatsScreen(navController = navController)
            }
            composable(UserRoute.Profile.route) {
                ProfileScreen(navController = navController)
            }
            // ... remainder same
            composable(
                "exercise_detail/{fileName}/{isAsset}?workoutId={workoutId}&index={index}&exerciseList={exerciseList}",
                enterTransition = { fadeIn(animationSpec = tween(200)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) },
            ) { backStack ->
                val fileName = backStack.arguments?.getString("fileName") ?: ""
                val isAsset  = backStack.arguments?.getString("isAsset")?.toBoolean() ?: false
                val workoutId = backStack.arguments?.getString("workoutId")
                val index = backStack.arguments?.getString("index")?.toIntOrNull() ?: 0
                val exerciseList = backStack.arguments?.getString("exerciseList")
                
                ExerciseDetailScreen(
                    fileName = fileName,
                    isAsset  = isAsset,
                    workoutId = workoutId,
                    exerciseIndex = index,
                    exerciseList = exerciseList,
                    onBack   = { navController.popBackStack() },
                )
            }
        }
    }
}

// ── Bottom nav bar ────────────────────────────────────────────────────────────

@Composable
private fun UserBottomNavBar(navController: NavController, items: List<UserRoute>) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route
    
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceMs = 500L

    NavigationBar(containerColor = Color.White) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick  = {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime < debounceMs) return@NavigationBarItem
                    lastClickTime = currentTime
                    
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
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
                            UserRoute.Library   -> Icons.Filled.VideoLibrary
                            UserRoute.Graphs    -> Icons.Filled.AutoGraph
                            else                -> item.icon
                        } else item.icon,
                        contentDescription = stringResource(item.labelResId),
                    )
                },
                label  = { Text(stringResource(item.labelResId)) },
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
