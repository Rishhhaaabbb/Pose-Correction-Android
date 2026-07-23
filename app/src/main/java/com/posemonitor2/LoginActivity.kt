package com.posemonitor2

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.Image
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.posemonitor2.auth.AuthViewModel
import com.posemonitor2.onboarding.OnboardingManager
import com.posemonitor2.questionnaire.QuestionnaireActivity
import com.posemonitor2.settings.AppLocaleManager
import com.posemonitor2.ui.theme.PoseMonitor2Theme
import com.posemonitor2.ui.theme.PoseMonitorAppTheme
import com.domain.repository.OnboardingRepository
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// ΓöÇΓöÇ Design tokens ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
private val Orange      = Color(0xFFE8622E)
private val Yellow      = Color(0xFFF4C430)
private val Teal        = Color(0xFF0A4A4E)
private val HeroBg      = Color(0xFFC4A882)   // yoga photo placeholder ΓÇö swap when asset added
private val LabelColor  = Color(0xFFFFD0C0)
private val FieldBg     = Color(0xFFEF7A52)
private val FieldStroke = Color(0xFFD4563A)
private val TextOnCard  = Color.White

// ΓöÇΓöÇ Activity ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@AndroidEntryPoint
class LoginActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var onboardingRepository: OnboardingRepository

    // Tracks whether we are performing the post-auth questionnaire check.
    // Exposed as mutable state so the Compose UI can show a blocking spinner.
    private val isCheckingOnboarding = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Login/onboarding screens always use light theme — the user's dark mode
            // preference must not affect pre-authentication screens
            PoseMonitor2Theme(darkTheme = false, dynamicColor = false) {
                val checking by isCheckingOnboarding
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    AuthNavigation(
                        authViewModel = authViewModel,
                        isCheckingOnboarding = checking,
                        onAuthenticated = { _ ->
                            val userId = authViewModel.state.value.userId
                            if (userId.isNullOrBlank()) {
                                // Fallback: userId missing, go straight to main app
                                startActivity(Intent(this@LoginActivity, UserFlowActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                })
                                finish()
                                return@AuthNavigation
                            }
                            lifecycleScope.launch {
                                navigateAfterAuth(userId)
                            }
                        }
                    )
                    // Blocking overlay while the questionnaire completion check runs
                    if (checking) {
                        Box(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Orange)
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks whether [userId] has completed the questionnaire and navigates
     * to the appropriate destination.
     *
     * Strategy:
     * 1. Check per-user local SharedPreferences flag first (fast, offline-safe).
     *    This key is scoped to [userId] so different accounts on the same device
     *    each get their own flag.
     * 2. If local flag is not set, check Room DB / Firestore.
     *    This handles reinstalls or new-device sign-ins for existing accounts.
     * 3. If completed anywhere, mark the local flag and go to UserFlowActivity.
     * 4. Otherwise show the questionnaire (first-run flow).
     *
     * Clear data / uninstall — local prefs wiped → falls through to Firestore.
     * New account — Firestore has no record → questionnaire shown.
     * Different account on same device — per-user key is empty → questionnaire shown.
     */
    private suspend fun navigateAfterAuth(userId: String) {
        isCheckingOnboarding.value = true

        val onboardingManager = OnboardingManager(this)

        val completed = when {
            // Per-user local flag is the fastest check
            onboardingManager.hasCompletedQuestionnaire(userId) -> true
            else -> {
                // No local flag — check DB/Firestore (handles reinstalls or new devices)
                try {
                    val remoteCompleted = onboardingRepository.isCompleted(userId)
                    if (remoteCompleted) {
                        // Sync local flag so future logins on this account are instant
                        onboardingManager.markQuestionnaireCompleted(userId)
                    }
                    remoteCompleted
                } catch (e: Exception) {
                    false // fail-safe: show questionnaire on error
                }
            }
        }

        isCheckingOnboarding.value = false
        val intent = if (completed) {
            Intent(this, UserFlowActivity::class.java)
        } else {
            QuestionnaireActivity.firstRunIntent(this)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

// ΓöÇΓöÇ Internal navigation ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
private sealed class AuthScreen {
    object Landing        : AuthScreen()
    object SignIn         : AuthScreen()
    object SignUp         : AuthScreen()
    object ForgotPassword : AuthScreen()
}

@Composable
private fun AuthNavigation(
    authViewModel: AuthViewModel,
    isCheckingOnboarding: Boolean = false,
    onAuthenticated: (String?) -> Unit,
) {
    val context = LocalContext.current
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(authState.isAuthenticated, authState.userEmail) {
        if (authState.isAuthenticated) {
            onAuthenticated(authState.userEmail)
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        runCatching { task.getResult(ApiException::class.java) }
            .onSuccess { account ->
                val idToken = account.idToken.orEmpty()
                authViewModel.signInWithGoogle(idToken, account.displayName, account.email)
            }
            .onFailure { err ->
                val message = when (val api = err as? ApiException) {
                    null -> err.message ?: "Google sign-in failed"
                    else -> when (api.statusCode) {
                        CommonStatusCodes.CANCELED -> "Google sign-in canceled by user"
                        CommonStatusCodes.DEVELOPER_ERROR -> "Google sign-in misconfiguration (client ID/SHA/provider settings)"
                        CommonStatusCodes.NETWORK_ERROR -> "Network error during Google sign-in"
                        else -> "Google sign-in failed (code ${api.statusCode})"
                    }
                }
                authViewModel.onGoogleSignInFailed(message)
            }
    }

    var screen by remember { mutableStateOf<AuthScreen>(AuthScreen.Landing) }
    // Combine authState.isLoading with the post-auth onboarding check so all
    // buttons stay disabled while either operation is in progress.
    val isLoading = authState.isLoading || isCheckingOnboarding
    when (screen) {
        AuthScreen.Landing        -> LandingScreen(
            onSignUpWithEmail = { authViewModel.clearError(); screen = AuthScreen.SignUp },
            onSignIn          = { authViewModel.clearError(); screen = AuthScreen.SignIn },
            isLoading = isLoading,
            errorMessage = authState.errorMessage,
            onGoogleSignIn = {
                authViewModel.clearError()
                val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                if (webClientId.isBlank()) {
                    authViewModel.onGoogleSignInFailed("Missing GOOGLE_WEB_CLIENT_ID in BuildConfig")
                } else {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestIdToken(webClientId)
                        .build()
                    val client = GoogleSignIn.getClient(context, gso)
                    googleLauncher.launch(client.signInIntent)
                }
            },
        )
        AuthScreen.SignIn         -> SignInScreen(
            onBack           = { authViewModel.clearError(); screen = AuthScreen.Landing },
            onForgotPassword = { authViewModel.clearError(); screen = AuthScreen.ForgotPassword },
            onCreateAccount  = { authViewModel.clearError(); screen = AuthScreen.SignUp },
            onSignIn         = { email, password -> authViewModel.signInWithEmail(email, password) },
            isLoading        = isLoading,
            errorMessage     = authState.errorMessage,
        )
        AuthScreen.SignUp         -> SignUpScreen(
            onBack    = { authViewModel.clearError(); screen = AuthScreen.Landing },
            onLogin   = { authViewModel.clearError(); screen = AuthScreen.SignIn },
            onSignUp  = { name, email, password -> authViewModel.signUpWithEmail(name, email, password) },
            isLoading = isLoading,
            errorMessage = authState.errorMessage,
        )
        AuthScreen.ForgotPassword -> ForgotPasswordScreen(
            onBack          = { authViewModel.clearError(); screen = AuthScreen.SignIn },
            onSendReset     = { email -> authViewModel.sendPasswordReset(email) {} },
            isLoading       = isLoading,
            errorMessage    = authState.errorMessage,
            isSent          = authState.resetEmailSent
        )
    }
}

// ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
// Shared scaffold: beige hero area (top) + orange card (bottom, overlapping)
// ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun AuthScaffold(
    heroFraction: Float = 0.40f,
    cardRadius: Dp = 36.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val themeBg = MaterialTheme.colorScheme.background
    // Orange card below the hero photo — matches the brand color on login/signup
    val cardBg = Orange

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Orange)  // matches the card — no white bleed on scroll
            .imePadding()
            .verticalScroll(scrollState)
    ) {
        // ── Background layers (Hero Image) ───────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Image(
                painter = painterResource(R.drawable.homepageyogapose),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Logo
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("💡", fontSize = 16.sp)
                }
                Text(
                    stringResource(R.string.login_brand_name),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // ── Card (Content) ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-30).dp) // Overlap the hero
                .clip(RoundedCornerShape(topStart = cardRadius, topEnd = cardRadius))
                .background(cardBg)
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 40.dp),
            content = content,
        )
    }
}

// ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
// Screen 1 ΓÇö Landing  ("Join the Movement")
// ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun LandingScreen(
    onSignUpWithEmail: () -> Unit,
    onSignIn: () -> Unit,
    onGoogleSignIn: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
) {
    AuthScaffold {
        Text(
            stringResource(R.string.login_join_movement_title),
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextOnCard,
            lineHeight = 44.sp,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            stringResource(R.string.login_join_movement_subtitle),
            fontSize = 14.sp,
            color = LabelColor,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(32.dp))

        // Google button (pill)
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(50.dp))
                .clickable {
                    if (!isLoading) onGoogleSignIn()
                },
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("G", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isLoading) stringResource(R.string.login_signing_in) else stringResource(R.string.login_continue_google),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isLoading) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = errorMessage,
                color = Yellow,
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(18.dp))

        // OR divider
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0x55FFFFFF))
            Text(stringResource(R.string.login_or), fontSize = 12.sp, color = Color(0xAAFFFFFF))
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0x55FFFFFF))
        }

        Spacer(Modifier.height(18.dp))

        // Sign up with Email (pill)
        Button(
            onClick = onSignUpWithEmail,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color(0xFF1A1A1A)),
        ) {
            Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF1A1A1A), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.login_signup_with_email), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
        }

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.login_terms_prefix),
                fontSize = 11.sp,
                color = Color(0x99FFFFFF),
                textAlign = TextAlign.Center,
            )
            Row {
                Text(
                    stringResource(R.string.login_terms_of_service),
                    fontSize = 11.sp,
                    color = Color.White,
                    textDecoration = TextDecoration.Underline,
                )
                Text(stringResource(R.string.login_and), fontSize = 11.sp, color = Color(0x99FFFFFF))
                Text(
                    stringResource(R.string.login_privacy_policy),
                    fontSize = 11.sp,
                    color = Color.White,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.login_already_have_account), fontSize = 13.sp, color = LabelColor)
            Text(
                stringResource(R.string.login_log_in),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextOnCard,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onSignIn() },
            )
        }
    }
}

// ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
// Screen 2 ΓÇö Sign In  ("Welcome Back")
// ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun SignInScreen(
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    onSignIn: (String, String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
) {
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localError      by remember { mutableStateOf<String?>(null) }
    val errorFillFields = stringResource(R.string.login_error_fill_fields)

    val displayError = localError ?: errorMessage
    androidx.activity.compose.BackHandler { onBack() }

    AuthScaffold {
        Text(stringResource(R.string.login_welcome_back_title), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = TextOnCard, lineHeight = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.login_welcome_back_subtitle), fontSize = 14.sp, color = LabelColor, lineHeight = 22.sp)

        Spacer(Modifier.height(28.dp))

        AuthLabel(stringResource(R.string.login_email_address))
        AuthField(value = email, onValueChange = { email = it; localError = null }, placeholder = stringResource(R.string.login_email_placeholder), keyboardType = KeyboardType.Email)

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AuthLabel(stringResource(R.string.login_password), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.login_forgot), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LabelColor, letterSpacing = 0.5.sp, modifier = Modifier.clickable { onForgotPassword() })
        }
        AuthField(
            value = password, onValueChange = { password = it; localError = null },
            placeholder = stringResource(R.string.login_password_placeholder), isPassword = true,
            passwordVisible = passwordVisible, onTogglePassword = { passwordVisible = !passwordVisible },
        )

        displayError?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Yellow, fontSize = 13.sp) }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = {
                when {
                    email.isBlank() || password.isBlank() -> localError = errorFillFields
                    else -> onSignIn(email.trim(), password)
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color(0xFF1A1A1A)),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF1A1A1A))
            } else {
                Text(stringResource(R.string.login_sign_in), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF1A1A1A), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.login_new_here), fontSize = 13.sp, color = LabelColor)
            Text(stringResource(R.string.login_create_account), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextOnCard, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { onCreateAccount() })
        }
    }
}

// ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
// Screen 3 ΓÇö Sign Up  ("Create Your Account")
// ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun SignUpScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onSignUp: (name: String, email: String, password: String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
) {
    var name            by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localError      by remember { mutableStateOf<String?>(null) }
    val errorFillFields = stringResource(R.string.login_error_fill_fields)
    val errorPasswordLength = stringResource(R.string.login_error_password_length)

    val displayError = localError ?: errorMessage
    androidx.activity.compose.BackHandler { onBack() }

    AuthScaffold {
        Text(stringResource(R.string.login_create_account_title), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = TextOnCard, lineHeight = 42.sp)

        Spacer(Modifier.height(24.dp))

        AuthLabel(stringResource(R.string.login_full_name))
        AuthField(
            value = name, onValueChange = { name = it; localError = null }, placeholder = stringResource(R.string.login_full_name_placeholder),
        )

        Spacer(Modifier.height(14.dp))

        AuthLabel(stringResource(R.string.login_email_address))
        AuthField(value = email, onValueChange = { email = it; localError = null }, placeholder = stringResource(R.string.login_email_placeholder), keyboardType = KeyboardType.Email)

        Spacer(Modifier.height(14.dp))

        AuthLabel(stringResource(R.string.login_password))
        AuthField(
            value = password, onValueChange = { password = it; localError = null },
            placeholder = stringResource(R.string.login_password_placeholder), isPassword = true,
            passwordVisible = passwordVisible, onTogglePassword = { passwordVisible = !passwordVisible },
        )

        displayError?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Yellow, fontSize = 13.sp) }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = {
                when {
                    name.isBlank() || email.isBlank() || password.isBlank() -> localError = errorFillFields
                    password.length < 6 -> localError = errorPasswordLength
                    else -> onSignUp(name.trim(), email.trim(), password)
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color(0xFF1A1A1A)),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF1A1A1A))
            } else {
                Text(stringResource(R.string.login_sign_up), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF1A1A1A), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.login_already_have_account), fontSize = 13.sp, color = LabelColor)
            Text(stringResource(R.string.login_log_in), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextOnCard, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { onLogin() })
        }
    }
}

// ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
// Screen 4 ΓÇö Forgot Password  ("Reset Password")
// ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onSendReset: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    isSent: Boolean,
) {
    var email by remember { mutableStateOf("") }
    androidx.activity.compose.BackHandler { onBack() }

    AuthScaffold {
        // Back link
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onBack() }) {
            Text("\u2190 ", fontSize = 18.sp, color = TextOnCard, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.login_back_to_login), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextOnCard, letterSpacing = 1.sp)
        }

        Spacer(Modifier.height(24.dp))

        Text(stringResource(R.string.login_reset_password_title), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = TextOnCard, lineHeight = 44.sp)

        Spacer(Modifier.height(12.dp))

        Text(stringResource(R.string.login_reset_password_subtitle), fontSize = 14.sp, color = LabelColor, lineHeight = 22.sp)

        Spacer(Modifier.height(28.dp))

        AuthLabel(stringResource(R.string.login_email_address))
        AuthField(value = email, onValueChange = { email = it }, placeholder = stringResource(R.string.login_reset_email_placeholder), keyboardType = KeyboardType.Email)

        if (isSent) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.login_reset_link_sent), color = Yellow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Yellow, fontSize = 13.sp)
        }

        Spacer(Modifier.height(28.dp))

        OutlinedButton(
            onClick = {
                if (email.isNotBlank() && !isLoading) {
                    onSendReset(email.trim())
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50.dp),
            border = BorderStroke(2.dp, Orange),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Orange, contentColor = Color.White),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Orange)
            } else {
                Text(stringResource(R.string.login_send_reset_link), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Orange, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.login_need_help), fontSize = 13.sp, color = LabelColor)
            Text(stringResource(R.string.login_contact_support), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextOnCard, textDecoration = TextDecoration.Underline)
        }
    }
}

// ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
// Shared micro-components
// ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun AuthLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LabelColor, letterSpacing = 1.sp, modifier = modifier.padding(bottom = 6.dp))
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: () -> Unit = {},
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = LabelColor.copy(alpha = 0.7f), fontSize = 14.sp) },
        leadingIcon = leadingIcon,
        trailingIcon = if (isPassword) ({
            IconButton(onClick = onTogglePassword) {
                Icon(
                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = LabelColor,
                )
            }
        }) else null,
        singleLine = true,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = FieldBg,
            unfocusedContainerColor = FieldBg,
            focusedBorderColor      = Color.White,
            unfocusedBorderColor    = FieldStroke,
            focusedTextColor        = Color.White,
            unfocusedTextColor      = Color.White,
            cursorColor             = Color.White,
        ),
    )
}
