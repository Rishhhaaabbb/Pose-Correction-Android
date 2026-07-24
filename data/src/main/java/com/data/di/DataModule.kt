package com.data.di

import android.content.Context
import androidx.room.Room
import com.data.local.DatabaseMigrations
import com.data.local.PoseDatabase
import com.data.local.SessionTokenStore
import com.data.local.dao.CustomWorkoutDao
import com.data.local.dao.DailyStatDao
import com.data.local.dao.ExerciseDao
import com.data.local.dao.OnboardingResponseDao
import com.data.local.dao.PoseGraphDao
import com.data.local.dao.ProfileDao
import com.data.local.dao.SessionDao
import com.data.local.dao.SessionMetricDao
import com.data.local.dao.SyncQueueDao
import com.data.remote.firebase.FirebaseRemoteClient
import com.data.repository.AdminStatsRepositoryImpl
import com.data.repository.AuthRepositoryImpl
import com.data.repository.CustomWorkoutRepositoryImpl
import com.data.repository.DailyStatsRepositoryImpl
import com.data.repository.ExerciseRepositoryImpl
import com.data.repository.OnboardingRepositoryImpl
import com.data.repository.PoseGraphRepositoryImpl
import com.data.repository.ProfileRepositoryImpl
import com.data.repository.SessionMetricsRepositoryImpl
import com.data.repository.WorkoutSessionRepositoryImpl
import com.domain.repository.AdminStatsRepository
import com.domain.repository.AuthRepository
import com.domain.repository.CustomWorkoutRepository
import com.domain.repository.DailyStatsRepository
import com.domain.repository.ExerciseRepository
import com.domain.repository.OnboardingRepository
import com.domain.repository.PoseGraphRepository
import com.domain.repository.ProfileRepository
import com.domain.repository.SessionMetricsRepository
import com.domain.repository.WorkoutSessionRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseRemoteClient(firestore: FirebaseFirestore): FirebaseRemoteClient =
        FirebaseRemoteClient(firestore)

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): SessionTokenStore =
        SessionTokenStore(context)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PoseDatabase {
        return Room.databaseBuilder(context, PoseDatabase::class.java, "pose_local.db")
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .addMigrations(DatabaseMigrations.MIGRATION_2_3)
            .addMigrations(DatabaseMigrations.MIGRATION_3_4)
            .addMigrations(DatabaseMigrations.MIGRATION_4_5)
            .addMigrations(DatabaseMigrations.MIGRATION_5_6)
            .addMigrations(DatabaseMigrations.MIGRATION_6_7)
            .addMigrations(DatabaseMigrations.MIGRATION_7_8)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProfileDao(db: PoseDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideSessionDao(db: PoseDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideSessionMetricDao(db: PoseDatabase): SessionMetricDao = db.sessionMetricDao()

    @Provides
    fun provideExerciseDao(db: PoseDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun providePoseGraphDao(db: PoseDatabase): PoseGraphDao = db.poseGraphDao()

    @Provides
    fun provideDailyStatDao(db: PoseDatabase): DailyStatDao = db.dailyStatDao()

    @Provides
    fun provideSyncQueueDao(db: PoseDatabase): SyncQueueDao = db.syncQueueDao()

    @Provides
    fun provideOnboardingResponseDao(db: PoseDatabase): OnboardingResponseDao = db.onboardingResponseDao()

    @Provides
    fun provideCustomWorkoutDao(db: PoseDatabase): CustomWorkoutDao = db.customWorkoutDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutSessionRepository(impl: WorkoutSessionRepositoryImpl): WorkoutSessionRepository

    @Binds
    @Singleton
    abstract fun bindSessionMetricsRepository(impl: SessionMetricsRepositoryImpl): SessionMetricsRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindPoseGraphRepository(impl: PoseGraphRepositoryImpl): PoseGraphRepository

    @Binds
    @Singleton
    abstract fun bindDailyStatsRepository(impl: DailyStatsRepositoryImpl): DailyStatsRepository

    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository

    @Binds
    @Singleton
    abstract fun bindAdminStatsRepository(impl: AdminStatsRepositoryImpl): AdminStatsRepository

    @Binds
    @Singleton
    abstract fun bindCustomWorkoutRepository(impl: CustomWorkoutRepositoryImpl): CustomWorkoutRepository
}