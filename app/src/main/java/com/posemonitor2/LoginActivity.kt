package com.posemonitor2

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

// ── Admin credentials (hardcoded) ─────────────────────────────────────────────
object AdminCredentials {
    const val EMAIL = "admin@poseapp.com"
    const val PASSWORD = "admin123"
}

// ── Design tokens ─────────────────────────────────────────────────────────────
private val Orange      = Color(0xFFE8622E)
private val Yellow      = Color(0xFFF4C430)
private val Teal        = Color(0xFF0A4A4E)
private val HeroBg      = Color(0xFFC4A882)   // yoga photo placeholder — swap when asset added
private val LabelColor  = Color(0xFFFFD0C0)
private val FieldBg     = Color(0xFFEF7A52)
private val FieldStroke = Color(0xFFD4563A)
private val TextOnCard  = Color.White

// ── Activity ──────────────────────────────────────────────────────────────────
class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { AuthNavigation() } }
    }
}

// ── Internal navigation ───────────────────────────────────────────────────────
private sealed class AuthScreen {
    object Landing        : AuthScreen()
    object SignIn         : AuthScreen()
    object SignUp         : AuthScreen()
    object ForgotPassword : AuthScreen()
}

@Composable
private fun AuthNavigation() {
    var screen by remember { mutableStateOf<AuthScreen>(AuthScreen.Landing) }
    when (screen) {
        AuthScreen.Landing        -> LandingScreen(
            onSignUpWithEmail = { screen = AuthScreen.SignUp },
            onSignIn          = { screen = AuthScreen.SignIn },
        )
        AuthScreen.SignIn         -> SignInScreen(
            onBack           = { screen = AuthScreen.Landing },
            onForgotPassword = { screen = AuthScreen.ForgotPassword },
            onCreateAccount  = { screen = AuthScreen.SignUp },
        )
        AuthScreen.SignUp         -> SignUpScreen(
            onBack  = { screen = AuthScreen.Landing },
            onLogin = { screen = AuthScreen.SignIn },
        )
        AuthScreen.ForgotPassword -> ForgotPasswordScreen(
            onBack = { screen = AuthScreen.SignIn },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared scaffold: beige hero area (top) + orange card (bottom, overlapping)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AuthScaffold(
    heroFraction: Float = 0.40f,
    cardRadius: Dp = 36.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // ── Background layers ─────────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(heroFraction),
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
                            .background(Orange),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⚡", fontSize = 16.sp)
                    }
                    Text(
                        "Aura Pose",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Teal,
                    )
                }
            }
            // Orange filler below hero so no gap shows
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Orange),
            )
        }

        // ── Orange card (overlaps hero bottom edge) ───────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(1f - heroFraction + 0.12f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = cardRadius, topEnd = cardRadius))
                .background(Orange)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .navigationBarsPadding(),
            content = content,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 1 — Landing  ("Join the Movement")
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LandingScreen(
    onSignUpWithEmail: () -> Unit,
    onSignIn: () -> Unit,
) {
    val context = LocalContext.current
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }

    val googleSignInClient = remember {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            isGoogleLoading = false
            googleError = "Google sign-in was cancelled."
            return@rememberLauncherForActivityResult
        }

        try {
            val accountTask = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = accountTask.getResult(ApiException::class.java)
            val idToken = account.idToken

            if (idToken.isNullOrBlank()) {
                isGoogleLoading = false
                googleError = "Google sign-in failed. Missing ID token."
                return@rememberLauncherForActivityResult
            }

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth
                .signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    isGoogleLoading = false
                    if (task.isSuccessful) {
                        val userEmail = firebaseAuth.currentUser?.email.orEmpty()
                        val target = if (userEmail.equals(AdminCredentials.EMAIL, ignoreCase = true)) {
                            HomeActivity::class.java
                        } else {
                            UserFlowActivity::class.java
                        }
                        context.startActivity(Intent(context, target))
                    } else {
                        googleError = task.exception?.localizedMessage ?: "Google sign-in failed."
                    }
                }
        } catch (e: ApiException) {
            isGoogleLoading = false
            googleError = "Google sign-in failed. (${e.statusCode})"
        }
    }

    AuthScaffold {
        Text(
            "Join the\nMovement",
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextOnCard,
            lineHeight = 44.sp,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Master your form with AI-powered\nreal-time corrections.",
            fontSize = 14.sp,
            color = LabelColor,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(32.dp))

        // Google button (white pill)
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(50.dp))
                .clickable {
                    if (!isGoogleLoading) {
                        googleError = null
                        isGoogleLoading = true
                        googleSignInClient.signOut().addOnCompleteListener {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        }
                    }
                },
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("G", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                Spacer(Modifier.width(10.dp))
                Text("Continue with Google", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
            }
        }

        if (isGoogleLoading) {
            Spacer(Modifier.height(10.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        }

        googleError?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Yellow, fontSize = 13.sp)
        }

        Spacer(Modifier.height(18.dp))

        // OR divider
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0x55FFFFFF))
            Text("  OR  ", fontSize = 12.sp, color = Color(0xAAFFFFFF))
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0x55FFFFFF))
        }

        Spacer(Modifier.height(18.dp))

        // Sign up with Email (yellow pill)
        Button(
            onClick = onSignUpWithEmail,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Yellow),
        ) {
            Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF1A1A1A), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign up with Email", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
        }

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "By continuing, you agree to our",
                fontSize = 11.sp,
                color = Color(0x99FFFFFF),
                textAlign = TextAlign.Center,
            )
            Row {
                Text(
                    "Terms of Service",
                    fontSize = 11.sp,
                    color = Color.White,
                    textDecoration = TextDecoration.Underline,
                )
                Text(" & ", fontSize = 11.sp, color = Color(0x99FFFFFF))
                Text(
                    "Privacy Policy",
                    fontSize = 11.sp,
                    color = Color.White,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Already have an account? ", fontSize = 13.sp, color = LabelColor)
            Text(
                "Log in",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextOnCard,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onSignIn() },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 2 — Sign In  ("Welcome Back")
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SignInScreen(
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val context = LocalContext.current
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error           by remember { mutableStateOf<String?>(null) }
    var isLoading       by remember { mutableStateOf(false) }

    AuthScaffold {
        Text("Welcome\nBack", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = TextOnCard, lineHeight = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text("Sign in to continue your AI-powered\nposture journey.", fontSize = 14.sp, color = LabelColor, lineHeight = 22.sp)

        Spacer(Modifier.height(28.dp))

        AuthLabel("EMAIL ADDRESS")
        AuthField(value = email, onValueChange = { email = it; error = null }, placeholder = "jane@example.com", keyboardType = KeyboardType.Email)

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AuthLabel("PASSWORD", modifier = Modifier.weight(1f))
            Text("FORGOT?", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextOnCard, letterSpacing = 0.5.sp, modifier = Modifier.clickable { onForgotPassword() })
        }
        AuthField(
            value = password, onValueChange = { password = it; error = null },
            placeholder = "••••••••", isPassword = true,
            passwordVisible = passwordVisible, onTogglePassword = { passwordVisible = !passwordVisible },
        )

        error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Yellow, fontSize = 13.sp) }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = {
                when {
                    email.isBlank() || password.isBlank() -> error = "Please fill in all fields"
                    else -> {
                        isLoading = true
                        error = null
                        firebaseAuth
                            .signInWithEmailAndPassword(email.trim(), password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    val userEmail = firebaseAuth.currentUser?.email.orEmpty()
                                    val target = if (
                                        userEmail.equals(AdminCredentials.EMAIL, ignoreCase = true) &&
                                        password == AdminCredentials.PASSWORD
                                    ) {
                                        HomeActivity::class.java
                                    } else {
                                        UserFlowActivity::class.java
                                    }
                                    context.startActivity(Intent(context, target))
                                } else {
                                    error = task.exception?.localizedMessage ?: "Sign in failed."
                                }
                            }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Yellow),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF1A1A1A),
                )
            } else {
                Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF1A1A1A), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("New here? ", fontSize = 13.sp, color = LabelColor)
            Text("Create Account", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextOnCard, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { onCreateAccount() })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 3 — Sign Up  ("Create Your Account")
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SignUpScreen(onBack: () -> Unit, onLogin: () -> Unit) {
    val context = LocalContext.current
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    var name            by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error           by remember { mutableStateOf<String?>(null) }
    var isLoading       by remember { mutableStateOf(false) }

    AuthScaffold {
        Text("Create Your\nAccount", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = TextOnCard, lineHeight = 42.sp)

        Spacer(Modifier.height(24.dp))

        AuthLabel("FULL NAME")
        AuthField(
            value = name, onValueChange = { name = it; error = null }, placeholder = "Jane Doe",
        )

        Spacer(Modifier.height(14.dp))

        AuthLabel("EMAIL ADDRESS")
        AuthField(value = email, onValueChange = { email = it; error = null }, placeholder = "jane@example.com", keyboardType = KeyboardType.Email)

        Spacer(Modifier.height(14.dp))

        AuthLabel("PASSWORD")
        AuthField(
            value = password, onValueChange = { password = it; error = null },
            placeholder = "••••••••", isPassword = true,
            passwordVisible = passwordVisible, onTogglePassword = { passwordVisible = !passwordVisible },
        )

        error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Yellow, fontSize = 13.sp) }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = {
                when {
                    name.isBlank() || email.isBlank() || password.isBlank() -> error = "Please fill in all fields"
                    password.length < 6 -> error = "Password must be at least 6 characters"
                    else -> {
                        isLoading = true
                        error = null
                        firebaseAuth
                            .createUserWithEmailAndPassword(email.trim(), password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    context.startActivity(Intent(context, UserFlowActivity::class.java))
                                } else {
                                    error = task.exception?.localizedMessage ?: "Sign up failed."
                                }
                            }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Yellow),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF1A1A1A),
                )
            } else {
                Text("Sign Up", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF1A1A1A), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Already have an account? ", fontSize = 13.sp, color = LabelColor)
            Text("Log in", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextOnCard, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { onLogin() })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 4 — Forgot Password  ("Reset Password")
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ForgotPasswordScreen(onBack: () -> Unit) {
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    var email by remember { mutableStateOf("") }
    var sent  by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    AuthScaffold {
        // Back link
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onBack() }) {
            Text("‹ ", fontSize = 18.sp, color = TextOnCard, fontWeight = FontWeight.Bold)
            Text("BACK TO LOGIN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextOnCard, letterSpacing = 1.sp)
        }

        Spacer(Modifier.height(24.dp))

        Text("Reset\nPassword", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = TextOnCard, lineHeight = 44.sp)

        Spacer(Modifier.height(12.dp))

        Text("Enter your email address and we'll send\nyou a link to reset your password.", fontSize = 14.sp, color = LabelColor, lineHeight = 22.sp)

        Spacer(Modifier.height(28.dp))

        AuthLabel("EMAIL ADDRESS")
        AuthField(value = email, onValueChange = { email = it; sent = false; error = null }, placeholder = "hello@aurapose.com", keyboardType = KeyboardType.Email)

        if (sent) {
            Spacer(Modifier.height(12.dp))
            Text("Reset link sent! Check your inbox.", color = Yellow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Yellow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(28.dp))

        // White outlined button
        OutlinedButton(
            onClick = {
                if (email.isBlank()) {
                    error = "Please enter your email"
                    return@OutlinedButton
                }

                isLoading = true
                error = null
                sent = false
                firebaseAuth
                    .sendPasswordResetEmail(email.trim())
                    .addOnCompleteListener { task ->
                        isLoading = false
                        if (task.isSuccessful) {
                            sent = true
                        } else {
                            error = task.exception?.localizedMessage ?: "Failed to send reset link."
                        }
                    }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(50.dp),
            border = BorderStroke(2.dp, Color.White),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Orange),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Orange,
                )
            } else {
                Text("SEND RESET LINK", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Orange, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Need help? ", fontSize = 13.sp, color = LabelColor)
            Text("Contact Support", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextOnCard, textDecoration = TextDecoration.Underline)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared micro-components
// ─────────────────────────────────────────────────────────────────────────────
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
        placeholder = { Text(placeholder, color = Color(0xAAFFFFFF), fontSize = 14.sp) },
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
