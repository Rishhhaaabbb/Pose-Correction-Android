package com.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.data.local.dao.DailyStatDao
import com.data.local.dao.ExerciseDao
import com.data.local.dao.OnboardingResponseDao
import com.data.local.dao.PoseGraphDao
import com.data.local.dao.ProfileDao
import com.data.local.dao.SessionDao
import com.data.local.dao.SessionMetricDao
import com.data.local.dao.SyncQueueDao
import com.data.local.entity.DailyStatEntity
import com.data.local.entity.ExerciseEntity
import com.data.local.entity.OnboardingResponseEntity
import com.data.local.entity.PoseGraphEntity
import com.data.local.entity.ProfileEntity
import com.data.local.entity.SessionEntity
import com.data.local.entity.SessionMetricEntity
import com.data.local.entity.SyncQueueEntity

@Database(
    entities = [
        ProfileEntity::class,
        SessionEntity::class,
        SessionMetricEntity::class,
        ExerciseEntity::class,
        PoseGraphEntity::class,
        DailyStatEntity::class,
        SyncQueueEntity::class,
        OnboardingResponseEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class PoseDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sessionDao(): SessionDao
    abstract fun sessionMetricDao(): SessionMetricDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun poseGraphDao(): PoseGraphDao
    abstract fun dailyStatDao(): DailyStatDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun onboardingResponseDao(): OnboardingResponseDao
}