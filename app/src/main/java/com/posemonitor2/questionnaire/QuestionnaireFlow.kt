package com.posemonitor2.questionnaire

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Root composable that hosts the 15-page questionnaire pager
 * (pages 0–13 = questions, page 14 = summary).
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

    var showSkipDialog by remember { mutableStateOf(false) }

    if (isEditMode) {
        LaunchedEffect(userId) {
            if (userId.isNotBlank()) viewModel.loadExistingAnswers(userId)
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onCompleted()
    }

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
            containerColor = Color.White,
            title = {
                Text("Skip questionnaire?", color = QText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "Your answers help us personalise exercise recommendations for you.\n\n" +
                    "You can always complete or update this later by going to your Profile.",
                    color = QSubtext,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Start,
                )
            },
            confirmButton = {
                TextButton(onClick = { showSkipDialog = false; onSkipped() }) {
                    Text("Skip for now", color = QPurple, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipDialog = false }) {
                    Text("Continue setup", color = QSubtext)
                }
            },
        )
    }

    Scaffold(
        containerColor = QBg,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color.White,
                    contentColor = QText,
                    actionColor = QPurple,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(QBgGradient)
                .padding(innerPadding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false,
            ) { page ->
                val onNext: () -> Unit = {
                    scope.launch { pagerState.animateScrollToPage(page + 1) }
                }
                val onBack: (() -> Unit) = {
                    scope.launch {
                        if (page > 0) pagerState.animateScrollToPage(page - 1)
                        else if (isEditMode) onDismiss()
                    }
                }
                val onSkip: () -> Unit = { showSkipDialog = true }

                when (page) {
                    QPage.AGE_GROUP -> AgeGroupScreen(
                        selectedValue = answers.ageGroup,
                        onSelect = { v -> viewModel.setSingleChoice(QPage.AGE_GROUP, v) },
                        onNext = onNext,
                        currentPage = page,
                        onBack = if (page > 0 || isEditMode) onBack else null,
                        onSkip = if (!isEditMode) onSkip else null,
                    )
                    QPage.GENDER -> GenderScreen(
                        selectedValue = answers.gender,
                        onSelect = { v -> viewModel.setSingleChoice(QPage.GENDER, v) },
                        onNext = onNext,
                        currentPage = page,
                        onBack = onBack,
                        onSkip = if (!isEditMode) onSkip else null,
                    )
                    QPage.GOAL -> GoalScreen(
                        selectedValue = answers.goal,
                        onSelect = { v -> viewModel.setSingleChoice(QPage.GOAL, v) },
                        onNext = onNext,
                        currentPage = page,
                        onBack = onBack,
                        onSkip = if (!isEditMode) onSkip else null,
                    )
                    QPage.EXPERIENCE -> SingleChoiceQuestionScreen(
                        title = "Have you experienced yoga before?",
                        options = listOf(
                            "Never tried",
                            "Tried a few times",
                            "Practiced for a while",
                            "Experienced, ready for advanced poses",
                        ),
                        selectedValue = answers.experienceLevel,
                        onSelect = { v -> viewModel.setSingleChoice(QPage.EXPERIENCE, v) },
                        onNext = onNext,
                        currentPage = page,
                        onBack = onBack,
                        showSkip = !isEditMode,
                        onSkip = onSkip,
                        icons = listOf(
                            Icons.Filled.SentimentNeutral,
                            Icons.Filled.SelfImprovement,
                            Icons.Filled.Star,
                            Icons.Filled.EmojiEvents,
                        ),
                    )
                    QPage.YOGA_STYLE -> YogaStyleScreen(
                        selectedValue = answers.yogaStyle,
                        onSelect = { v -> viewModel.setSingleChoice(QPage.YOGA_STYLE, v) },
                        onNext = onNext,
                        currentPage = page,
                        onBack = onBack,
                        onSkip = if (!isEditMode) onSkip else null,
                    )
                    QPage.FLEXIBILITY -> SingleChoiceQuestionScreen(
                        title = "How would you describe your flexibility?",
                        options = listOf("Very Stiff", "Slightly Stiff", "Average", "Flexible", "Very Flexible"),
                        selectedValue = answers.flexibilityLevel,
                        onSelect = { v -> viewModel.setSingleChoice(QPage.FLEXIBILITY, v) },
                        onNext = onNext,
                        currentPage = page,
                        onBack = onBack,
                        showSkip = !isEditMode,
                        onSkip = onSkip,
                        icons = listOf(
                            Icons.Filled.SentimentVeryDissatisfied,
                            Icons.Filled.SentimentDissatisfied,
                            Icons.Filled.SentimentNeutral,
                            Icons.Filled.SentimentSatisfied,
                            Icons.Filled.SentimentVerySatisfied,
                        ),
                    )
                    QPage.FOCUS_AREAS -> MultiSelectQuestionScreen(
                        title = "Which areas do you want to focus on?",
                        subtitle = "Select all that apply",
                        options = listOf("Neck", "Shoulders", "Back", "Core", "Hips", "Legs", "Balance", "Full Body"),
                        selectedValues = answers.focusAreas,
                        onToggle = { v -> viewModel.toggleMultiSelectOption(QPage.FOCUS_AREAS, v) },
                        onNext = onNext,
                        currentPage = page,
                        onBack = onBack,
                        showSkip = !isEditMode,
                        onSkip = onSkip,
                    )
                    QPage.HEIGHT -> HeightInputScreen(
                        heightRawInput = answers.heightRawInput,
                        isMetric = answers.heightIsMetric,
                        onInputChange = { viewModel.setHeightRaw(it) },
                        onToggleUnit = { viewModel.toggleHeightUnit() },
                        onNext = onNext,
                        nextEnabled = viewModel.isNextEnabled(page),
                        currentPage = page,
                        onBack = onBack,
                        onSkip = if (!isEditMode) onSkip else null,
                    )
                    QPage.WEIGHT -> WeightInputScreen(
                        weightRawInput = answers.weightRawInput,
                        isMetric = answers.weightIsMetric,
                        onInputChange = { viewModel.setWeightRaw(it) },
                        onToggleUnit = { viewModel.toggleWeightUnit() },
                        onNext = onNext,
                        nextEnabled = viewModel.isNextEnabled(page),
                        currentPage = page,
                        onBack = onBack,
                        onSkip = if (!isEditMode) onSkip else null,
                        heightCm = answers.heightCm,
                    )
                    QPage.DAILY_TIME -> SingleChoiceQuestionScreen(
                        title = "How much time can you dedicate daily?",
                        options = listOf("10 Minutes", "20 Minutes", "30 Minutes", "45 Minutes", "60+ Minutes"),
                        selectedValue = answers.dailyPracticeMinutes?.let { mins ->
                            when (mins) {
                                10   -> "10 Minutes"
                                20   -> "20 Minutes"
                                30   -> "30 Minutes"
                                45   -> "45 Minutes"
                                else -> "60+ Minutes"
                            }
                        },
                        onSelect = { v -> viewModel.setSingleChoice(QPage.DAILY_TIME, v) },
                        onNext = onNext,
                        currentPage = page,
                        onBack = onBack,
                        showSkip = !isEditMode,
                        onSkip = onSkip,
                        icons = listOf(
                            Icons.Filled.Timer,
                            Icons.Filled.Timer,
                            Icons.Filled.Timer,
                            Icons.Filled.Timer,
                            Icons.Filled.TimerOff,
                        ),
                    )
                    QPage.WEEKLY_FREQ -> SingleChoiceQuestionScreen(
                        title = "How often do you practice per week?",
                        options = listOf("Not much", "1–2 Days", "3–5 Days", "6–7 Days"),
                        selectedValue = answers.weeklyFrequency,
                        onSelect = { v -> viewModel.setSingleChoice(QPage.WEEKLY_FREQ, v) },
                        onNext = onNext,
                        currentPage = page,
                        onBack = onBack,
                        showSkip = !isEditMode,
                        onSkip = onSkip,
                        icons = listOf(
                            Icons.Filled.Star,
                            Icons.Filled.Star,
                            Icons.Filled.Star,
                            Icons.Filled.Star,
                        ),
                    )
                    QPage.INJURIES -> MultiSelectQuestionScreen(
                        title = "Any injuries in these areas?",
                        subtitle = "Select all that apply. We'll adapt your plan.",
                        options = listOf("None", "Spine", "Shoulder", "Wrist", "Knee", "Ankle", "Neck", "Hip"),
                        selectedValues = answers.injuries,
                        onToggle = { v -> viewModel.toggleMultiSelectOption(QPage.INJURIES, v) },
                        onNext = onNext,
                        currentPage = page,
                        onBack = onBack,
                        showSkip = !isEditMode,
                        onSkip = onSkip,
                    )
                    QPage.MEDICAL -> MultiSelectQuestionScreen(
                        title = "Any medical conditions we should know about?",
                        subtitle = "This helps us keep your sessions safe.",
                        options = listOf("None", "High Blood Pressure", "Heart Condition", "Asthma", "Diabetes", "Arthritis", "Pregnancy", "Other"),
                        selectedValues = answers.medicalConditions,
                        onToggle = { v -> viewModel.toggleMultiSelectOption(QPage.MEDICAL, v) },
                        onNext = onNext,
                        currentPage = page,
                        onBack = onBack,
                        showSkip = !isEditMode,
                        onSkip = onSkip,
                    )
                    QPage.CAMERA -> CameraPermissionScreen(
                        cameraPermissionGranted = answers.cameraPermissionGranted,
                        onEnablePoseCorrection = onRequestCameraPermission,
                        onContinueWithout = { viewModel.setCameraPermission(false) },
                        onNext = onNext,
                        currentPage = page,
                        onBack = onBack,
                        onSkip = if (!isEditMode) onSkip else null,
                    )
                    QPage.SUMMARY -> QuestionnaireSummaryScreen(
                        answers = uiState.answers,
                        isLoading = uiState.isLoading,
                        onSubmit = { viewModel.submitAnswers(userId) },
                        currentPage = page,
                    )
                    else -> Box(modifier = Modifier.fillMaxSize())
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
