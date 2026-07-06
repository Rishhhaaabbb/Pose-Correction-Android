package com.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN exercise_name TEXT")
            db.execSQL("ALTER TABLE sessions ADD COLUMN total_reps INTEGER")
            db.execSQL("ALTER TABLE sessions ADD COLUMN accuracy_percent REAL")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS session_metrics (
                    id TEXT NOT NULL,
                    session_id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    metric_name TEXT NOT NULL,
                    metric_value REAL NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pose_graphs (
                    id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    graph_name TEXT NOT NULL,
                    storage_path TEXT NOT NULL,
                    metadata_json TEXT,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_stats (
                    id TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    stat_date TEXT NOT NULL,
                    sessions_count INTEGER NOT NULL,
                    total_reps INTEGER NOT NULL,
                    avg_accuracy REAL NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )

            db.execSQL("CREATE INDEX IF NOT EXISTS index_session_metrics_user_id_updated_at ON session_metrics(user_id, updated_at DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pose_graphs_user_id_updated_at ON pose_graphs(user_id, updated_at DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_stats_user_id_stat_date ON daily_stats(user_id, stat_date DESC)")
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN target_weight_kg REAL")
            db.execSQL("ALTER TABLE profiles ADD COLUMN weekly_goal_sessions INTEGER")
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN target_reps INTEGER")
            db.execSQL("ALTER TABLE sessions ADD COLUMN total_calories REAL")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS exercises (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    category TEXT,
                    difficulty TEXT,
                    description TEXT,
                    calories_per_rep REAL NOT NULL,
                    thumbnail_url TEXT
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN profile_image_url TEXT")
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create the new onboarding_responses table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS onboarding_responses (
                    user_id TEXT NOT NULL PRIMARY KEY,
                    age_group TEXT,
                    gender TEXT,
                    goal TEXT,
                    experience_level TEXT,
                    yoga_style TEXT,
                    flexibility_level TEXT,
                    focus_areas TEXT,
                    height_cm INTEGER,
                    weight_kg REAL,
                    daily_practice_minutes INTEGER,
                    weekly_frequency TEXT,
                    injuries TEXT,
                    medical_conditions TEXT,
                    camera_permission_granted INTEGER,
                    is_completed INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Extend profiles table with questionnaire columns (all nullable for backward compat)
            db.execSQL("ALTER TABLE profiles ADD COLUMN age_group TEXT")
            db.execSQL("ALTER TABLE profiles ADD COLUMN gender TEXT")
            db.execSQL("ALTER TABLE profiles ADD COLUMN experience_level TEXT")
            db.execSQL("ALTER TABLE profiles ADD COLUMN yoga_style TEXT")
            db.execSQL("ALTER TABLE profiles ADD COLUMN flexibility_level TEXT")
            db.execSQL("ALTER TABLE profiles ADD COLUMN focus_areas TEXT")
            db.execSQL("ALTER TABLE profiles ADD COLUMN daily_practice_minutes INTEGER")
            db.execSQL("ALTER TABLE profiles ADD COLUMN weekly_frequency TEXT")
            db.execSQL("ALTER TABLE profiles ADD COLUMN injuries TEXT")
            db.execSQL("ALTER TABLE profiles ADD COLUMN medical_conditions TEXT")
            db.execSQL("ALTER TABLE profiles ADD COLUMN camera_permission_granted INTEGER")
        }
    }
}