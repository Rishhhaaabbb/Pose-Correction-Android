package com.posemonitor2.questionnaire

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@Composable
fun QuestionnaireFlow(
    viewModel: QuestionnaireViewModel,
    mode: String,
    onRequestCameraPermission: () -> Unit,
    onFinish: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val answers = uiState.answers
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { QPage.TOTAL })
    val currentPage = pagerState.currentPage

    // Back handler
    BackHandler {
        when {
            currentPage > 0 -> scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
            mode == QuestionnaireActivity.MODE_EDIT -> onFinish()
            // first-run on page 0: consume, do nothing
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val progress = if (currentPage >= QPage.SUMMARY) 1f else currentPage / 14f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFE8622E),
            trackColor = Color(0xFF2A4A4C),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
        ) { page ->
            val onNext: () -> Unit = { scope.launch { pagerState.animateScrollToPage(page + 1) } }

            when (page) {
                QPage.AGE_GROUP -> SingleChoiceQuestionScreen(
                    question = "Which age group do you belong to?",
                    options = listOf("Under 18", "18–24", "25–34", "35–44", "45–54", "55+"),
                    selected = answers.ageGroup,
                    onSelect = { viewModel.setSingleChoice(page, it) },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.GENDER -> SingleChoiceQuestionScreen(
                    question = "What is your gender?",
                    options = listOf("Male", "Female", "Prefer not to say"),
                    selected = answers.gender,
                    onSelect = { viewModel.setSingleChoice(page, it) },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.GOAL -> SingleChoiceQuestionScreen(
                    question = "What is your main goal?",
                    options = listOf(
                        "Improve Flexibility", "Improve Posture", "Reduce Stress",
                        "Lose Weight", "Build Strength", "Increase Mobility", "General Fitness"
                    ),
                    selected = answers.goal,
                    onSelect = { viewModel.setSingleChoice(page, it) },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.EXPERIENCE -> SingleChoiceQuestionScreen(
                    question = "How experienced are you with yoga?",
                    options = listOf("Complete Beginner", "Tried a Few Times", "Regular Practitioner", "Advanced"),
                    selected = answers.experienceLevel,
                    onSelect = { viewModel.setSingleChoice(page, it) },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.YOGA_STYLE -> SingleChoiceQuestionScreen(
                    question = "Which type of yoga interests you most?",
                    options = listOf("Hatha Yoga", "Vinyasa Yoga", "Yin Yoga", "Power Yoga", "Chair Yoga", "Meditation & Breathing"),
                    selected = answers.yogaStyle,
                    onSelect = { viewModel.setSingleChoice(page, it) },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.FLEXIBILITY -> SingleChoiceQuestionScreen(
                    question = "How would you describe your flexibility?",
                    options = listOf("Very Stiff", "Slightly Stiff", "Average", "Flexible", "Very Flexible"),
                    selected = answers.flexibilityLevel,
                    onSelect = { viewModel.setSingleChoice(page, it) },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.FOCUS_AREAS -> MultiSelectQuestionScreen(
                    question = "Which areas would you like to improve?",
                    options = listOf("Neck", "Shoulders", "Back", "Core", "Hips", "Legs", "Balance", "Full Body"),
                    selected = answers.focusAreas,
                    onToggle = { viewModel.toggleMultiSelectOption(page, it) },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.HEIGHT -> HeightInputScreen(
                    heightRawInput = answers.heightRawInput,
                    isMetric = answers.heightIsMetric,
                    onInputChange = { viewModel.setHeightRaw(it) },
                    onToggleUnit = { viewModel.toggleHeightUnit() },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.WEIGHT -> WeightInputScreen(
                    weightRawInput = answers.weightRawInput,
                    isMetric = answers.weightIsMetric,
                    onInputChange = { viewModel.setWeightRaw(it) },
                    onToggleUnit = { viewModel.toggleWeightUnit() },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.DAILY_TIME -> SingleChoiceQuestionScreen(
                    question = "How much time can you dedicate daily?",
                    options = listOf("10 Minutes", "20 Minutes", "30 Minutes", "45 Minutes", "60+ Minutes"),
                    selected = answers.dailyPracticeMinutes?.let {
                        when (it) { 10 -> "10 Minutes"; 20 -> "20 Minutes"; 30 -> "30 Minutes"; 45 -> "45 Minutes"; else -> "60+ Minutes" }
                    },
                    onSelect = { viewModel.setSingleChoice(page, it) },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.WEEKLY_FREQ -> SingleChoiceQuestionScreen(
                    question = "How many days per week can you practice?",
                    options = listOf("1–2 Days", "3–4 Days", "5–6 Days", "Daily"),
                    selected = answers.weeklyFrequency,
                    onSelect = { viewModel.setSingleChoice(page, it) },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.INJURIES -> MultiSelectQuestionScreen(
                    question = "Do you currently have any injuries?",
                    options = listOf("None", "Neck", "Spine", "Shoulder", "Wrist", "Hip", "Knee", "Ankle"),
                    selected = answers.injuries,
                    onToggle = { viewModel.toggleMultiSelectOption(page, it) },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.MEDICAL -> MultiSelectQuestionScreen(
                    question = "Do any of these apply to you?",
                    options = listOf("None", "High Blood Pressure", "Heart Condition", "Asthma", "Diabetes", "Arthritis", "Pregnancy", "Other"),
                    selected = answers.medicalConditions,
                    onToggle = { viewModel.toggleMultiSelectOption(page, it) },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.CAMERA -> CameraPermissionScreen(
                    onEnablePoseCorrection = onRequestCameraPermission,
                    onSkip = { viewModel.setCameraPermission(false) },
                    onNext = onNext, nextEnabled = viewModel.isNextEnabled(page),
                )
                QPage.SUMMARY -> QuestionnaireSummaryScreen(
                    answers = answers,
                    isLoading = uiState.isLoading,
                    saveError = uiState.saveError,
                    userId = uiState.userId ?: "",
                    onSubmit = { userId -> viewModel.submitAnswers(userId) },
                    onRetry = { viewModel.clearSaveError() },
                )
            }
        }
    }

    // Auto-advance camera page once permission result arrives
    LaunchedEffect(answers.cameraPermissionGranted) {
        if (answers.cameraPermissionGranted != null && pagerState.currentPage == QPage.CAMERA) {
            pagerState.animateScrollToPage(QPage.SUMMARY)
        }
    }
}
