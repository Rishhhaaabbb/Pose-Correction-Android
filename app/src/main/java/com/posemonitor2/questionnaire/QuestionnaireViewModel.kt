package com.posemonitor2.questionnaire

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domain.model.OnboardingResponse
import com.domain.repository.AuthRepository
import com.domain.repository.OnboardingRepository
import com.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

// ── Page index constants ──────────────────────────────────────────────────────
object QPage {
    const val AGE_GROUP = 0
    const val GENDER = 1
    const val GOAL = 2
    const val EXPERIENCE = 3
    const val YOGA_STYLE = 4
    const val FLEXIBILITY = 5
    const val FOCUS_AREAS = 6
    const val HEIGHT = 7
    const val WEIGHT = 8
    const val DAILY_TIME = 9
    const val WEEKLY_FREQ = 10
    const val INJURIES = 11
    const val MEDICAL = 12
    const val CAMERA = 13
    const val SUMMARY = 14
    const val TOTAL = 15
}

// ── State models ──────────────────────────────────────────────────────────────

data class AnswerState(
    val ageGroup: String? = null,
    val gender: String? = null,
    val goal: String? = null,
    val experienceLevel: String? = null,
    val yogaStyle: String? = null,
    val flexibilityLevel: String? = null,
    val focusAreas: Set<String> = emptySet(),
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val dailyPracticeMinutes: Int? = null,
    val weeklyFrequency: String? = null,
    val injuries: Set<String> = emptySet(),
    val medicalConditions: Set<String> = emptySet(),
    val cameraPermissionGranted: Boolean? = null,
    // UI display state for numeric inputs
    val heightRawInput: String = "",
    val heightIsMetric: Boolean = true,
    val weightRawInput: String = "",
    val weightIsMetric: Boolean = true,
)

data class QuestionnaireUiState(
    val mode: String = "first_run",  // matches QuestionnaireActivity.MODE_FIRST_RUN
    val answers: AnswerState = AnswerState(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val saveError: String? = null,
    val userId: String? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionnaireUiState())
    val uiState: StateFlow<QuestionnaireUiState> = _uiState.asStateFlow()

    fun setMode(mode: String) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setUserId(userId: String) {
        _uiState.update { it.copy(userId = userId) }
    }

    // ── Answer setters ────────────────────────────────────────────────────────

    fun setSingleChoice(page: Int, value: String) {
        _uiState.update { state ->
            val a = state.answers
            val updated = when (page) {
                QPage.AGE_GROUP   -> a.copy(ageGroup = value)
                QPage.GENDER      -> a.copy(gender = value)
                QPage.GOAL        -> a.copy(goal = value)
                QPage.EXPERIENCE  -> a.copy(experienceLevel = value)
                QPage.YOGA_STYLE  -> a.copy(yogaStyle = value)
                QPage.FLEXIBILITY -> a.copy(flexibilityLevel = value)
                QPage.DAILY_TIME  -> a.copy(dailyPracticeMinutes = value.filter { it.isDigit() }.toIntOrNull())
                QPage.WEEKLY_FREQ -> a.copy(weeklyFrequency = value)
                else -> a
            }
            state.copy(answers = updated)
        }
    }

    fun toggleMultiSelectOption(page: Int, option: String) {
        _uiState.update { state ->
            val a = state.answers
            val updated = when (page) {
                QPage.FOCUS_AREAS -> {
                    val newSet = a.focusAreas.toggle(option)
                    a.copy(focusAreas = newSet)
                }
                QPage.INJURIES -> {
                    val newSet = applyNoneMutualExclusion(a.injuries, option)
                    a.copy(injuries = newSet)
                }
                QPage.MEDICAL -> {
                    val newSet = applyNoneMutualExclusion(a.medicalConditions, option)
                    a.copy(medicalConditions = newSet)
                }
                else -> a
            }
            state.copy(answers = updated)
        }
    }

    private fun Set<String>.toggle(option: String): Set<String> =
        if (contains(option)) this - option else this + option

    private fun applyNoneMutualExclusion(current: Set<String>, option: String): Set<String> {
        return when {
            option == "None" -> setOf("None")
            current.contains("None") -> (current - "None") + option
            current.contains(option) -> current - option
            else -> current + option
        }
    }

    // ── Height / weight input ─────────────────────────────────────────────────

    fun setHeightRaw(input: String) {
        val cm = if (_uiState.value.answers.heightIsMetric) {
            input.toIntOrNull()?.takeIf { it > 0 }
        } else {
            // ft/in format: parse as decimal feet, e.g. "5.10" means 5 ft 10 in
            parseFtDecimalToCm(input)
        }
        _uiState.update { state ->
            state.copy(answers = state.answers.copy(heightRawInput = input, heightCm = cm))
        }
    }

    fun setWeightRaw(input: String) {
        val kg = if (_uiState.value.answers.weightIsMetric) {
            input.toDoubleOrNull()?.takeIf { it > 0 }
        } else {
            input.toDoubleOrNull()?.takeIf { it > 0 }?.let { lbToKg(it) }
        }
        _uiState.update { state ->
            state.copy(answers = state.answers.copy(weightRawInput = input, weightKg = kg))
        }
    }

    fun toggleHeightUnit() {
        _uiState.update { state ->
            val a = state.answers
            val newIsMetric = !a.heightIsMetric
            val newRaw = if (newIsMetric) {
                a.heightCm?.toString() ?: ""
            } else {
                a.heightCm?.let { cm ->
                    val (ft, inc) = cmToFtIn(cm)
                    "$ft.$inc"
                } ?: ""
            }
            state.copy(answers = a.copy(heightIsMetric = newIsMetric, heightRawInput = newRaw))
        }
    }

    fun toggleWeightUnit() {
        _uiState.update { state ->
            val a = state.answers
            val newIsMetric = !a.weightIsMetric
            val newRaw = if (newIsMetric) {
                a.weightKg?.let { "%.1f".format(it) } ?: ""
            } else {
                a.weightKg?.let { "%.1f".format(kgToLb(it)) } ?: ""
            }
            state.copy(answers = a.copy(weightIsMetric = newIsMetric, weightRawInput = newRaw))
        }
    }

    fun setCameraPermission(granted: Boolean) {
        _uiState.update { state ->
            state.copy(answers = state.answers.copy(cameraPermissionGranted = granted))
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    fun isNextEnabled(page: Int): Boolean {
        val a = _uiState.value.answers
        return when (page) {
            QPage.AGE_GROUP   -> a.ageGroup != null
            QPage.GENDER      -> a.gender != null
            QPage.GOAL        -> a.goal != null
            QPage.EXPERIENCE  -> a.experienceLevel != null
            QPage.YOGA_STYLE  -> a.yogaStyle != null
            QPage.FLEXIBILITY -> a.flexibilityLevel != null
            QPage.FOCUS_AREAS -> a.focusAreas.isNotEmpty()
            QPage.HEIGHT      -> a.heightCm != null && a.heightCm > 0
            QPage.WEIGHT      -> a.weightKg != null && a.weightKg > 0
            QPage.DAILY_TIME  -> a.dailyPracticeMinutes != null
            QPage.WEEKLY_FREQ -> a.weeklyFrequency != null
            QPage.INJURIES    -> a.injuries.isNotEmpty()
            QPage.MEDICAL     -> a.medicalConditions.isNotEmpty()
            QPage.CAMERA      -> a.cameraPermissionGranted != null
            QPage.SUMMARY     -> true
            else -> false
        }
    }

    // ── Load existing answers (edit mode) ─────────────────────────────────────

    fun loadExistingAnswers(userId: String) {
        viewModelScope.launch {
            val existing = onboardingRepository.getAnswers(userId) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    answers = AnswerState(
                        ageGroup = existing.ageGroup,
                        gender = existing.gender,
                        goal = existing.goal,
                        experienceLevel = existing.experienceLevel,
                        yogaStyle = existing.yogaStyle,
                        flexibilityLevel = existing.flexibilityLevel,
                        focusAreas = existing.focusAreas?.split(",")
                            ?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
                        heightCm = existing.heightCm,
                        weightKg = existing.weightKg,
                        dailyPracticeMinutes = existing.dailyPracticeMinutes,
                        weeklyFrequency = existing.weeklyFrequency,
                        injuries = existing.injuries?.split(",")
                            ?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
                        medicalConditions = existing.medicalConditions?.split(",")
                            ?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
                        cameraPermissionGranted = existing.cameraPermissionGranted,
                        heightRawInput = existing.heightCm?.toString() ?: "",
                        weightRawInput = existing.weightKg?.let { "%.1f".format(it) } ?: "",
                    )
                )
            }
        }
    }

    // ── Submit ────────────────────────────────────────────────────────────────
    //
    // Note: We intentionally do NOT call profileRepository.upsertProfile here.
    // All questionnaire fields are persisted in OnboardingResponse (both local Room
    // and Firestore). ProfileRepositoryImpl.refreshProfile() syncs from Firestore,
    // so the profile will pick up these fields on the next sync cycle. This avoids
    // the display-name-clobbering issue that would arise from passing an empty
    // displayName to upsertProfile (Room replaces the whole row, unlike Firestore's
    // SetOptions.merge()).

    fun submitAnswers(userId: String) {
        val answers = _uiState.value.answers
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveError = null) }
            val response = OnboardingResponse(
                userId = userId,
                ageGroup = answers.ageGroup,
                gender = answers.gender,
                goal = answers.goal,
                experienceLevel = answers.experienceLevel,
                yogaStyle = answers.yogaStyle,
                flexibilityLevel = answers.flexibilityLevel,
                focusAreas = answers.focusAreas.joinToString(",").ifEmpty { null },
                heightCm = answers.heightCm,
                weightKg = answers.weightKg,
                dailyPracticeMinutes = answers.dailyPracticeMinutes,
                weeklyFrequency = answers.weeklyFrequency,
                injuries = answers.injuries.joinToString(",").ifEmpty { null },
                medicalConditions = answers.medicalConditions.joinToString(",").ifEmpty { null },
                cameraPermissionGranted = answers.cameraPermissionGranted,
                isCompleted = true,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
            onboardingRepository.saveAnswers(response)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSaved = true) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, saveError = err.message ?: "Failed to save")
                    }
                }
        }
    }

    fun clearSaveError() {
        _uiState.update { it.copy(saveError = null) }
    }

    // ── Unit conversion utilities ─────────────────────────────────────────────

    fun ftInToCm(feet: Int, inches: Int): Int =
        ((feet * 30.48) + (inches * 2.54)).roundToInt()

    fun cmToFtIn(cm: Int): Pair<Int, Int> {
        val totalInches = cm / 2.54
        val feet = (totalInches / 12).toInt()
        val inches = (totalInches % 12).roundToInt()
        return feet to inches
    }

    fun lbToKg(lb: Double): Double = lb * 0.453592

    fun kgToLb(kg: Double): Double = kg * 2.20462

    private fun parseFtDecimalToCm(input: String): Int? {
        val d = input.toDoubleOrNull() ?: return null
        if (d <= 0) return null
        val feet = d.toInt()
        val inches = ((d - feet) * 10).roundToInt()
        return ftInToCm(feet, inches.coerceIn(0, 11))
    }
}
