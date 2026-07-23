package com.posemonitor2.questionnaire

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.posemonitor2.UserFlowActivity
import com.posemonitor2.onboarding.OnboardingManager
import com.posemonitor2.ui.components.DisclaimerDialog
import com.posemonitor2.ui.theme.PoseMonitor2Theme
import com.posemonitor2.ui.theme.PoseMonitorAppTheme
import dagger.hilt.android.AndroidEntryPoint
import com.domain.repository.AuthRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class QuestionnaireActivity : ComponentActivity() {

    private val viewModel: QuestionnaireViewModel by viewModels()

    @Inject
    lateinit var authRepository: AuthRepository

    private var showDisclaimer by mutableStateOf(false)
    private var pendingMode by mutableStateOf("")

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.setCameraPermission(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FIRST_RUN
        viewModel.setMode(mode)

        // Fetch userId from auth session
        lifecycleScope.launch {
            val authUser = authRepository.observeSession().firstOrNull()
            val userId = authUser?.id ?: return@launch
            viewModel.setUserId(userId)
        }

        setContent {
            PoseMonitor2Theme(darkTheme = false, dynamicColor = false) {
                val uiState by viewModel.uiState.collectAsState()
                val currentUserId = uiState.userId ?: ""

                if (showDisclaimer) {
                    DisclaimerDialog(
                        title = getString(com.posemonitor2.R.string.disclaimer_title),
                        body = getString(com.posemonitor2.R.string.disclaimer_body),
                        confirmText = getString(com.posemonitor2.R.string.disclaimer_acknowledge),
                        onConfirm = {
                            showDisclaimer = false
                            navigateToMain(currentUserId)
                        },
                    )
                }

                QuestionnaireFlow(
                    viewModel = viewModel,
                    isEditMode = (mode == MODE_EDIT),
                    userId = currentUserId,
                    onDismiss = { finish() },
                    onSkipped = {
                        if (mode == MODE_FIRST_RUN) {
                            pendingMode = "skip"
                            showDisclaimer = true
                        } else {
                            finish()
                        }
                    },
                    onCompleted = {
                        if (mode == MODE_FIRST_RUN) {
                            pendingMode = "completed"
                            showDisclaimer = true
                        } else {
                            finish()
                        }
                    },
                    onRequestCameraPermission = {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                )
            }
        }
    }

    private fun navigateToMain(currentUserId: String) {
        if (currentUserId.isNotBlank()) {
            OnboardingManager(this@QuestionnaireActivity)
                .markQuestionnaireCompleted(currentUserId)
        }
        OnboardingManager(this@QuestionnaireActivity).resetOnboarding()
        startActivity(
            Intent(this@QuestionnaireActivity, UserFlowActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    companion object {
        const val EXTRA_MODE = "extra_questionnaire_mode"
        const val MODE_FIRST_RUN = "first_run"
        const val MODE_EDIT = "edit"

        fun firstRunIntent(context: Context): Intent =
            Intent(context, QuestionnaireActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_FIRST_RUN)

        fun editIntent(context: Context): Intent =
            Intent(context, QuestionnaireActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_EDIT)
    }
}