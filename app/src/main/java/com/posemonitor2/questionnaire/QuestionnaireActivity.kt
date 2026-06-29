package com.posemonitor2.questionnaire

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.posemonitor2.UserFlowActivity
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

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.setCameraPermission(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FIRST_RUN
        viewModel.setMode(mode)

        // Fetch userId from auth session and load existing answers if in edit mode
        lifecycleScope.launch {
            val authUser = authRepository.observeSession().firstOrNull()
            val userId = authUser?.id ?: return@launch
            viewModel.setUserId(userId)
            if (mode == MODE_EDIT) {
                viewModel.loadExistingAnswers(userId)
            }
        }

        setContent {
            PoseMonitorAppTheme {
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(uiState.isSaved) {
                    if (uiState.isSaved) {
                        if (mode == MODE_FIRST_RUN) {
                            startActivity(
                                Intent(this@QuestionnaireActivity, UserFlowActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                        }
                        finish()
                    }
                }

                QuestionnaireFlow(
                    viewModel = viewModel,
                    mode = mode,
                    onRequestCameraPermission = {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                    onFinish = { finish() },
                )
            }
        }
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
