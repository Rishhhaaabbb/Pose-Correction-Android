package com.posemonitor2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.poseengine.PoseGraph
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.posemonitor2.R
import com.posemonitor2.settings.AppLocaleManager
import com.posemonitor2.ui.theme.PoseMonitorAppTheme
import com.presentation.graphviz.PoseGraphVisualizationScreen
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import com.posemonitor2.auth.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PoseMonitorAppTheme {
                HomeNav()
            }
        }
    }
}

private sealed class HomeScreen {
    object Home : HomeScreen()
    object GraphList : HomeScreen()
    data class GraphView(val entry: GraphEntry) : HomeScreen()
}

@Composable
private fun HomeNav() {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(authState.isAuthenticated) {
        if (!authState.isAuthenticated) {
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
            (context as? ComponentActivity)?.finish()
        }
    }

    var screen by remember { mutableStateOf<HomeScreen>(HomeScreen.Home) }
    val scope = rememberCoroutineScope()

    when (val s = screen) {
        is HomeScreen.Home -> HomeScreenContent(
            onViewGraphs = { screen = HomeScreen.GraphList },
            onLogout = {
                scope.launch {
                    runCatching {
                        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                        if (webClientId.isNotBlank()) {
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                                .requestIdToken(webClientId)
                                .build()
                            GoogleSignIn.getClient(context, gso).signOut().await()
                        }
                    }
                    authViewModel.signOut()
                }
            }
        )
        is HomeScreen.GraphList -> GraphListScreen(
            onBack = { screen = HomeScreen.Home },
            onViewGraph = { entry -> screen = HomeScreen.GraphView(entry) },
        )
        is HomeScreen.GraphView -> GraphViewScreen(
            entry = s.entry,
            onBack = { screen = HomeScreen.GraphList },
        )
    }
}

@Composable
private fun HomeScreenContent(onViewGraphs: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onLogout() }

    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.admin_home_title),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                    )
                    Text(
                        text = stringResource(R.string.admin_home_subtitle),
                        fontSize = 15.sp,
                        color = onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                IconButton(onClick = onLogout) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HomeCard(
                title = stringResource(R.string.admin_generate_graph_title),
                description = stringResource(R.string.admin_generate_graph_desc),
                buttonLabel = stringResource(R.string.gg_choose_video),
                buttonColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    context.startActivity(
                        Intent(context, GraphGenerationActivity::class.java)
                    )
                }
            )

            HomeCard(
                title = stringResource(R.string.admin_start_monitoring_title),
                description = stringResource(R.string.admin_start_monitoring_desc),
                buttonLabel = stringResource(R.string.admin_open_camera),
                buttonColor = MaterialTheme.colorScheme.tertiary,
                onClick = {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                    )
                }
            )

            HomeCard(
                title = stringResource(R.string.admin_view_graphs_title),
                description = stringResource(R.string.admin_view_graphs_desc),
                buttonLabel = stringResource(R.string.admin_view_graphs_title),
                buttonColor = MaterialTheme.colorScheme.secondary,
                onClick = onViewGraphs,
            )
        }
    }
}

@Composable
private fun GraphListScreen(
    onBack: () -> Unit,
    onViewGraph: (GraphEntry) -> Unit,
) {
    val context = LocalContext.current
    val graphs = remember { loadAvailableGraphs(context) }

    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.common_back_arrow), color = onSurfaceVariant, fontSize = 14.sp)
            }
            Text(
                stringResource(R.string.admin_pose_graphs_title),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface,
            )
        }

        if (graphs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("\uD83D\uDCC2", fontSize = 48.sp)
                    Text(
                        stringResource(R.string.admin_no_graphs_yet),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface,
                    )
                    Text(
                        stringResource(R.string.admin_generate_graph_first_home),
                        fontSize = 14.sp,
                        color = onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(graphs) { entry ->
                    GraphBrowseCard(entry = entry, onClick = { onViewGraph(entry) })
                }
            }
        }
    }
}

@Composable
private fun GraphBrowseCard(entry: GraphEntry, onClick: () -> Unit) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surfaceVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = if (entry.isAsset) "\uD83D\uDCE6" else "\uD83D\uDCCA",
            fontSize = 24.sp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.fileName.removeSuffix(".json"),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
            )
            Text(
                text = if (entry.isAsset) stringResource(R.string.gs_bundled) else stringResource(R.string.admin_generated),
                fontSize = 12.sp,
                color = onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.common_chevron_right), fontSize = 20.sp, color = onSurfaceVariant)
    }
}

@Composable
private fun GraphViewScreen(entry: GraphEntry, onBack: () -> Unit) {
    val context = LocalContext.current

    val graph = remember(entry) {
        runCatching {
            val json = if (entry.isAsset) {
                context.assets.open(entry.fileName).bufferedReader().readText()
            } else {
                File(entry.filePath).readText(Charsets.UTF_8)
            }
            PoseGraph.fromJson(json)
        }.getOrNull()
    }

    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val error = MaterialTheme.colorScheme.error
    val primary = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.common_back_arrow), color = primary, fontSize = 15.sp)
            }
            Text(
                text = entry.fileName.removeSuffix(".json"),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
            )
        }

        if (graph != null) {
            PoseGraphVisualizationScreen(graph = graph)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("\u26A0\uFE0F", fontSize = 48.sp)
                    Text(
                        stringResource(R.string.admin_failed_load_graph),
                        fontSize = 16.sp,
                        color = error,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        entry.filePath,
                        fontSize = 12.sp,
                        color = onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCard(
    title: String,
    description: String,
    buttonLabel: String,
    buttonColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = onSurfaceVariant,
                lineHeight = 20.sp,
            )
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            ) {
                Text(text = buttonLabel, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
