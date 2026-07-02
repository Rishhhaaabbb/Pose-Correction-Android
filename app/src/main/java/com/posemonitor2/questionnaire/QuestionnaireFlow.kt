package com.posemonitor2.questionnaire

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ΓöÇΓöÇ Design tokens (mirrored here so this file compiles standalone) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
private val BgDark    = Color(0xFF0E1F20)
private val Orange    = Color(0xFFE8622E)
private val TrackTeal = Color(0xFF173436)

/**
 * Root composable that hosts the 15-page questionnaire pager
 * (pages 0–13 = questions, page 14 = summary).
 *
 * @param viewModel                  The [QuestionnaireViewModel] driving state.
 * @param isEditMode                 `true` when launched from the Profile screen; `false` for first-run.
 * @param userId                     The authenticated user's ID, passed to [viewModel.submitAnswers].
 * @param onDismiss                  Called to close the activity (edit mode: page 0 back press).
 * @param onSkipped                  Called when the user taps Skip and confirms (first-run only).
 * @param onCompleted                Called when [QuestionnaireUiState.isSaved] becomes `true`.
 * @param onRequestCameraPermission  Triggers the system camera permission dialog.
 */
@Composable
fun QuestionnaireFlow(
    viewModel: QuestionnaireViewModel,
    isEditMode: Boolean,
    userId: String,
    onDismiss: () -> Unit,
    onSkipped: () -> Unit,
    onCompleted: () -> Unit,
    onRequestCameraPermission: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val answers = uiState.answers
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { QPage.TOTAL })
    val currentPage = pagerState.currentPage
    val snackbarHostState = remember { SnackbarHostState() }

    // Controls visibility of the skip-confirmation dialog
    var showSkipDialog by remember { mutableStateOf(false) }

    // In edit mode, load existing answers once when userId is available.
    if (isEditMode) {
        LaunchedEffect(userId) {
            if (userId.isNotBlank()) {
                viewModel.loadExistingAnswers(userId)
            }
        }
    }

    // Navigate to onCompleted when save succeeds
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onCompleted()
        }
    }

    // Show snackbar on save error with Retry action
    LaunchedEffect(uiState.saveError) {
        val error = uiState.saveError ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = error,
            actionLabel = "Retry",
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.clearSaveError()
            viewModel.submitAnswers(userId)
        } else {
            viewModel.clearSaveError()
        }
    }

    // Back-press logic:
    //   page 0 + first-run → consume (do nothing — no back out of questionnaire)
    //   page 0 + edit mode → dismiss
    //   page 1..13         → go to previous page
    //   page 14 (summary)  → go to page 13
    BackHandler {
        when {
            currentPage == 0 && isEditMode -> onDismiss()
            currentPage == 0              -> { /* first-run: consume */ }
            else -> scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
        }
    }

    // Skip confirmation dialog
    if (showSkipDialog) {
        AlertDialog(
            onDismissRequest = { showSkipDialog = false },
            containerColor = Color(0xFF173436),
            title = {
                Text(
                    "Skip questionnaire?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            },
            text = {
                Text(
                    "Your answers help us personalise exercise recommendations for you.\n\n" +
                    "You can always complete or update this later by going to your Profile.",
                    color = Color(0xFFCEE7E9),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Start,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSkipDialog = false
                    onSkipped()
                }) {
                    Text("Skip for now", color = Orange, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipDialog = false }) {
                    Text("Continue setup", color = Color(0xFF9EC5C8))
                }
            },
        )
    }

    Scaffold(
        containerColor = BgDark,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF173436),
                    contentColor = Color(0xFFFFFFFF),
                    actionColor = Orange,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar: progress on left, Skip on right (first-run only, not on summary page)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = { (currentPage.toFloat() / 14f).coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f),
                        color = Orange,
                        trackColor = TrackTeal,
                    )
                    // Show Skip only in first-run mode and before the summary page
                    if (!isEditMode && currentPage < QPage.SUMMARY) {
                        TextButton(
                            onClick = { showSkipDialog = true },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            Text(
                                "Skip",
                                color = Color(0xFF9EC5C8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = false,
                ) { page ->
                    val onNext: () -> Unit = {
                        scope.launch { pagerState.animateScrollToPage(page + 1) }
                    }

                    when (page) {
                        QPage.AGE_GROUP -> SingleChoiceQuestionScreen(
                            title = "How old are you?",
                            options = listOf("Under 18", "18–24", "25–34", "35–44", "45–54", "55+"),
                            selectedValue = answers.ageGroup,
                            onSelect = { v -> viewModel.setSingleChoice(0, v) },
                            onNext = onNext,
                        )
                        QPage.GENDER -> SingleChoiceQuestionScreen(
                            title = "What is your gender?",
                            options = listOf("Male", "Female", "Prefer not to say"),
                            selectedValue = answers.gender,
                            onSelect = { v -> viewModel.setSingleChoice(1, v) },
                            onNext = onNext,
                        )
                        QPage.GOAL -> SingleChoiceQuestionScreen(
                            title = "What is your primary goal?",
                            options = listOf(
                                "Improve Flexibility",
                                "Improve Posture",
                                "Reduce Stress",
                                "Lose Weight",
                                "Build Strength",
                                "Increase Mobility",
                                "General Fitness",
                            ),
                            selectedValue = answers.goal,
                            onSelect = { v -> viewModel.setSingleChoice(2, v) },
                            onNext = onNext,
                        )
                        QPage.EXPERIENCE -> SingleChoiceQuestionScreen(
                            title = "What is your yoga experience?",
                            options = listOf(
                                "Complete Beginner",
                                "Tried a Few Times",
                                "Regular Practitioner",
                                "Advanced",
                            ),
                            selectedValue = answers.experienceLevel,
                            onSelect = { v -> viewModel.setSingleChoice(3, v) },
                            onNext = onNext,
                        )
                        QPage.YOGA_STYLE -> SingleChoiceQuestionScreen(
                            title = "Which yoga style interests you most?",
                            options = listOf(
                                "Hatha Yoga",
                                "Vinyasa Yoga",
                                "Yin Yoga",
                                "Power Yoga",
                                "Chair Yoga",
                                "Meditation & Breathing",
                            ),
                            selectedValue = answers.yogaStyle,
                            onSelect = { v -> viewModel.setSingleChoice(4, v) },
                            onNext = onNext,
                        )
                        QPage.FLEXIBILITY -> SingleChoiceQuestionScreen(
                            title = "How would you describe your flexibility?",
                            options = listOf(
                                "Very Stiff",
                                "Slightly Stiff",
                                "Average",
                                "Flexible",
                                "Very Flexible",
                            ),
                            selectedValue = answers.flexibilityLevel,
                            onSelect = { v -> viewModel.setSingleChoice(5, v) },
                            onNext = onNext,
                        )
                        QPage.FOCUS_AREAS -> MultiSelectQuestionScreen(
                            title = "Which areas do you want to focus on?",
                            subtitle = "Select all that apply.",
                            options = listOf(
                                "Neck", "Shoulders", "Back", "Core",
                                "Hips", "Legs", "Balance", "Full Body",
                            ),
                            selectedValues = answers.focusAreas,
                            onToggle = { v -> viewModel.toggleMultiSelectOption(6, v) },
                            onNext = onNext,
                        )
                        QPage.HEIGHT -> HeightInputScreen(
                            heightRawInput = answers.heightRawInput,
                            isMetric = answers.heightIsMetric,
                            onInputChange = { viewModel.setHeightRaw(it) },
                            onToggleUnit = { viewModel.toggleHeightUnit() },
                            onNext = onNext,
                            nextEnabled = viewModel.isNextEnabled(page),
                        )
                        QPage.WEIGHT -> WeightInputScreen(
                            weightRawInput = answers.weightRawInput,
                            isMetric = answers.weightIsMetric,
                            onInputChange = { viewModel.setWeightRaw(it) },
                            onToggleUnit = { viewModel.toggleWeightUnit() },
                            onNext = onNext,
                            nextEnabled = viewModel.isNextEnabled(page),
                        )
                        QPage.DAILY_TIME -> SingleChoiceQuestionScreen(
                            title = "How much time can you dedicate daily?",
                            options = listOf(
                                "10 Minutes",
                                "20 Minutes",
                                "30 Minutes",
                                "45 Minutes",
                                "60+ Minutes",
                            ),
                            selectedValue = answers.dailyPracticeMinutes?.let { mins ->
                                when (mins) {
                                    10 -> "10 Minutes"
                                    20 -> "20 Minutes"
                                    30 -> "30 Minutes"
                                    45 -> "45 Minutes"
                                    else -> "60+ Minutes"
                                }
                            },
                            onSelect = { v -> viewModel.setSingleChoice(9, v) },
                            onNext = onNext,
                        )
                        QPage.WEEKLY_FREQ -> SingleChoiceQuestionScreen(
                            title = "How many days per week will you practice?",
                            options = listOf("1–2 Days", "3–4 Days", "5–6 Days", "Daily"),
                            selectedValue = answers.weeklyFrequency,
                            onSelect = { v -> viewModel.setSingleChoice(10, v) },
                            onNext = onNext,
                        )
                        QPage.INJURIES -> MultiSelectQuestionScreen(
                            title = "Do you have any injuries?",
                            subtitle = "Select all that apply. We'll adapt your plan.",
                            options = listOf(
                                "None", "Neck", "Spine", "Shoulder",
                                "Wrist", "Hip", "Knee", "Ankle",
                            ),
                            selectedValues = answers.injuries,
                            onToggle = { v -> viewModel.toggleMultiSelectOption(11, v) },
                            onNext = onNext,
                        )
                        QPage.MEDICAL -> MultiSelectQuestionScreen(
                            title = "Any medical conditions we should know about?",
                            subtitle = "This helps us keep your sessions safe.",
                            options = listOf(
                                "None",
                                "High Blood Pressure",
                                "Heart Condition",
                                "Asthma",
                                "Diabetes",
                                "Arthritis",
                                "Pregnancy",
                                "Other",
                            ),
                            selectedValues = answers.medicalConditions,
                            onToggle = { v -> viewModel.toggleMultiSelectOption(12, v) },
                            onNext = onNext,
                        )
                        QPage.CAMERA -> CameraPermissionScreen(
                            cameraPermissionGranted = answers.cameraPermissionGranted,
                            onEnablePoseCorrection = onRequestCameraPermission,
                            onContinueWithout = { viewModel.setCameraPermission(false) },
                            onNext = onNext,
                        )
                        QPage.SUMMARY -> QuestionnaireSummaryScreen(
                            answers = uiState.answers,
                            isLoading = uiState.isLoading,
                            onSubmit = { viewModel.submitAnswers(userId) },
                        )
                        else -> Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    // Auto-advance camera page once the system permission result arrives
    LaunchedEffect(answers.cameraPermissionGranted) {
        if (answers.cameraPermissionGranted != null && pagerState.currentPage == QPage.CAMERA) {
            pagerState.animateScrollToPage(QPage.SUMMARY)
        }
    }
}