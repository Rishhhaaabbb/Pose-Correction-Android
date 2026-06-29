package com.data.repository.mapper

import com.data.local.entity.ProfileEntity
import com.data.local.entity.DailyStatEntity
import com.data.local.entity.OnboardingResponseEntity
import com.data.local.entity.PoseGraphEntity
import com.data.local.entity.SessionEntity
import com.data.local.entity.SessionMetricEntity
import com.data.remote.firebase.FirestoreDailyStatDto
import com.data.remote.firebase.FirestoreOnboardingDto
import com.data.remote.firebase.FirestorePoseGraphDto
import com.data.remote.firebase.FirestoreProfileDto
import com.data.remote.firebase.FirestoreSessionDto
import com.data.remote.firebase.FirestoreSessionMetricDto
import com.domain.model.DailyStat
import com.domain.model.OnboardingResponse
import com.domain.model.PoseGraphRecord
import com.domain.model.SessionStatus
import com.domain.model.SessionMetric
import com.domain.model.UserProfile
import com.domain.model.WorkoutSession

fun ProfileEntity.toDomain(): UserProfile = UserProfile(
    id = id,
    displayName = displayName,
    email = email,
    profileImageUrl = profileImageUrl,
    heightCm = heightCm,
    weightKg = weightKg,
    fitnessGoal = fitnessGoal,
    targetWeightKg = targetWeightKg,
    weeklyGoalSessions = weeklyGoalSessions,
    ageGroup = ageGroup,
    gender = gender,
    experienceLevel = experienceLevel,
    yogaStyle = yogaStyle,
    flexibilityLevel = flexibilityLevel,
    focusAreas = focusAreas,
    dailyPracticeMinutes = dailyPracticeMinutes,
    weeklyFrequency = weeklyFrequency,
    injuries = injuries,
    medicalConditions = medicalConditions,
    cameraPermissionGranted = cameraPermissionGranted?.let { it == 1 },
)

fun UserProfile.toEntity(now: Long): ProfileEntity = ProfileEntity(
    id = id,
    displayName = displayName,
    email = email,
    profileImageUrl = profileImageUrl,
    heightCm = heightCm,
    weightKg = weightKg,
    fitnessGoal = fitnessGoal,
    targetWeightKg = targetWeightKg,
    weeklyGoalSessions = weeklyGoalSessions,
    updatedAtEpochMs = now,
    ageGroup = ageGroup,
    gender = gender,
    experienceLevel = experienceLevel,
    yogaStyle = yogaStyle,
    flexibilityLevel = flexibilityLevel,
    focusAreas = focusAreas,
    dailyPracticeMinutes = dailyPracticeMinutes,
    weeklyFrequency = weeklyFrequency,
    injuries = injuries,
    medicalConditions = medicalConditions,
    cameraPermissionGranted = cameraPermissionGranted?.let { if (it) 1 else 0 },
)

fun UserProfile.toFirestoreDto(now: Long): FirestoreProfileDto = FirestoreProfileDto(
    id = id,
    display_name = displayName,
    email = email,
    profile_image_url = profileImageUrl,
    height_cm = heightCm,
    weight_kg = weightKg,
    fitness_goal = fitnessGoal,
    target_weight_kg = targetWeightKg,
    weekly_goal_sessions = weeklyGoalSessions,
    updated_at = now,
    age_group = ageGroup,
    gender = gender,
    experience_level = experienceLevel,
    yoga_style = yogaStyle,
    flexibility_level = flexibilityLevel,
    focus_areas = focusAreas,
    daily_practice_minutes = dailyPracticeMinutes,
    weekly_frequency = weeklyFrequency,
    injuries = injuries,
    medical_conditions = medicalConditions,
    camera_permission_granted = cameraPermissionGranted,
)

fun FirestoreProfileDto.toEntity(): ProfileEntity = ProfileEntity(
    id = id,
    displayName = display_name,
    email = email,
    profileImageUrl = profile_image_url,
    heightCm = height_cm,
    weightKg = weight_kg,
    fitnessGoal = fitness_goal,
    targetWeightKg = target_weight_kg,
    weeklyGoalSessions = weekly_goal_sessions,
    updatedAtEpochMs = updated_at,
    ageGroup = age_group,
    gender = gender,
    experienceLevel = experience_level,
    yogaStyle = yoga_style,
    flexibilityLevel = flexibility_level,
    focusAreas = focus_areas,
    dailyPracticeMinutes = daily_practice_minutes,
    weeklyFrequency = weekly_frequency,
    injuries = injuries,
    medicalConditions = medical_conditions,
    cameraPermissionGranted = camera_permission_granted?.let { if (it) 1 else 0 },
)

fun SessionEntity.toDomain(): WorkoutSession = WorkoutSession(
    id = id,
    userId = userId,
    exerciseName = exerciseName,
    targetReps = targetReps,
    totalReps = totalReps,
    accuracyPercent = accuracyPercent,
    totalCalories = totalCalories,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    durationSec = durationSec,
    status = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.DRAFT),
)

fun WorkoutSession.toEntity(now: Long): SessionEntity = SessionEntity(
    id = id,
    userId = userId,
    exerciseName = exerciseName,
    targetReps = targetReps,
    totalReps = totalReps,
    accuracyPercent = accuracyPercent,
    totalCalories = totalCalories,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    durationSec = durationSec,
    status = status.name,
    updatedAtEpochMs = now,
)

fun WorkoutSession.toFirestoreDto(now: Long): FirestoreSessionDto = FirestoreSessionDto(
    id = id,
    user_id = userId,
    exercise_name = exerciseName,
    target_reps = targetReps,
    total_reps = totalReps,
    accuracy_percent = accuracyPercent,
    total_calories = totalCalories,
    started_at = startedAtEpochMs,
    ended_at = endedAtEpochMs,
    duration_sec = durationSec,
    status = status.name,
    updated_at = now,
)

fun SessionMetricEntity.toDomain(): SessionMetric = SessionMetric(
    id = id,
    sessionId = sessionId,
    userId = userId,
    metricName = metricName,
    metricValue = metricValue,
)

fun SessionMetric.toEntity(now: Long): SessionMetricEntity = SessionMetricEntity(
    id = id,
    sessionId = sessionId,
    userId = userId,
    metricName = metricName,
    metricValue = metricValue,
    updatedAtEpochMs = now,
)

fun SessionMetric.toFirestoreDto(now: Long): FirestoreSessionMetricDto = FirestoreSessionMetricDto(
    id = id,
    session_id = sessionId,
    user_id = userId,
    metric_name = metricName,
    metric_value = metricValue,
    updated_at = now,
)

fun PoseGraphEntity.toDomain(): PoseGraphRecord = PoseGraphRecord(
    id = id,
    userId = userId,
    graphName = graphName,
    storagePath = storagePath,
    metadataJson = metadataJson,
)

fun PoseGraphRecord.toEntity(now: Long): PoseGraphEntity = PoseGraphEntity(
    id = id,
    userId = userId,
    graphName = graphName,
    storagePath = storagePath,
    metadataJson = metadataJson,
    updatedAtEpochMs = now,
)

fun PoseGraphRecord.toFirestoreDto(now: Long): FirestorePoseGraphDto = FirestorePoseGraphDto(
    id = id,
    user_id = userId,
    graph_name = graphName,
    storage_path = storagePath,
    metadata_json = metadataJson,
    updated_at = now,
)

fun DailyStatEntity.toDomain(): DailyStat = DailyStat(
    id = id,
    userId = userId,
    statDate = statDate,
    sessionsCount = sessionsCount,
    totalReps = totalReps,
    avgAccuracy = avgAccuracy,
)

fun DailyStat.toEntity(now: Long): DailyStatEntity = DailyStatEntity(
    id = id,
    userId = userId,
    statDate = statDate,
    sessionsCount = sessionsCount,
    totalReps = totalReps,
    avgAccuracy = avgAccuracy,
    updatedAtEpochMs = now,
)

fun DailyStat.toFirestoreDto(now: Long): FirestoreDailyStatDto = FirestoreDailyStatDto(
    id = id,
    user_id = userId,
    stat_date = statDate,
    sessions_count = sessionsCount,
    total_reps = totalReps,
    avg_accuracy = avgAccuracy,
    updated_at = now,
)

// ── OnboardingResponse mappers ────────────────────────────────────────────────

fun OnboardingResponseEntity.toDomain(): OnboardingResponse = OnboardingResponse(
    userId = userId,
    ageGroup = ageGroup,
    gender = gender,
    goal = goal,
    experienceLevel = experienceLevel,
    yogaStyle = yogaStyle,
    flexibilityLevel = flexibilityLevel,
    focusAreas = focusAreas,
    heightCm = heightCm,
    weightKg = weightKg,
    dailyPracticeMinutes = dailyPracticeMinutes,
    weeklyFrequency = weeklyFrequency,
    injuries = injuries,
    medicalConditions = medicalConditions,
    cameraPermissionGranted = cameraPermissionGranted?.let { it == 1 },
    isCompleted = isCompleted == 1,
    updatedAtEpochMs = updatedAt,
)

fun OnboardingResponse.toEntity(): OnboardingResponseEntity = OnboardingResponseEntity(
    userId = userId,
    ageGroup = ageGroup,
    gender = gender,
    goal = goal,
    experienceLevel = experienceLevel,
    yogaStyle = yogaStyle,
    flexibilityLevel = flexibilityLevel,
    focusAreas = focusAreas,
    heightCm = heightCm,
    weightKg = weightKg,
    dailyPracticeMinutes = dailyPracticeMinutes,
    weeklyFrequency = weeklyFrequency,
    injuries = injuries,
    medicalConditions = medicalConditions,
    cameraPermissionGranted = cameraPermissionGranted?.let { if (it) 1 else 0 },
    isCompleted = if (isCompleted) 1 else 0,
    updatedAt = updatedAtEpochMs,
)

fun OnboardingResponse.toFirestoreDto(): FirestoreOnboardingDto = FirestoreOnboardingDto(
    user_id = userId,
    age_group = ageGroup,
    gender = gender,
    goal = goal,
    experience_level = experienceLevel,
    yoga_style = yogaStyle,
    flexibility_level = flexibilityLevel,
    focus_areas = focusAreas,
    height_cm = heightCm,
    weight_kg = weightKg,
    daily_practice_minutes = dailyPracticeMinutes,
    weekly_frequency = weeklyFrequency,
    injuries = injuries,
    medical_conditions = medicalConditions,
    camera_permission_granted = cameraPermissionGranted,
    is_completed = isCompleted,
    updated_at = updatedAtEpochMs,
)

fun FirestoreOnboardingDto.toEntity(): OnboardingResponseEntity = OnboardingResponseEntity(
    userId = user_id,
    ageGroup = age_group,
    gender = gender,
    goal = goal,
    experienceLevel = experience_level,
    yogaStyle = yoga_style,
    flexibilityLevel = flexibility_level,
    focusAreas = focus_areas,
    heightCm = height_cm,
    weightKg = weight_kg,
    dailyPracticeMinutes = daily_practice_minutes,
    weeklyFrequency = weekly_frequency,
    injuries = injuries,
    medicalConditions = medical_conditions,
    cameraPermissionGranted = camera_permission_granted?.let { if (it) 1 else 0 },
    isCompleted = if (is_completed) 1 else 0,
    updatedAt = updated_at,
)
