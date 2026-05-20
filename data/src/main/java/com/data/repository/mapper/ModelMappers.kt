package com.data.repository.mapper

import com.data.local.entity.ProfileEntity
import com.data.local.entity.DailyStatEntity
import com.data.local.entity.PoseGraphEntity
import com.data.local.entity.SessionEntity
import com.data.local.entity.SessionMetricEntity
import com.data.remote.firebase.FirestoreDailyStatDto
import com.data.remote.firebase.FirestorePoseGraphDto
import com.data.remote.firebase.FirestoreProfileDto
import com.data.remote.firebase.FirestoreSessionDto
import com.data.remote.firebase.FirestoreSessionMetricDto
import com.domain.model.DailyStat
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
