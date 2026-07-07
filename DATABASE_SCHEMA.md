# Database Schema Documentation
**Project:** Pose Correction App  
**Database Version:** 5  
**Database Type:** Room (SQLite)  
**Total Tables:** 7

---

## Table 1: `profiles`
**Purpose:** Store user profile information including personal details and fitness goals

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `id` | String | PRIMARY KEY | Unique profile identifier |
| `display_name` | String | NOT NULL | User's display name |
| `email` | String | NULLABLE | User's email address |
| `profile_image_url` | String | NULLABLE | URL to profile image |
| `height_cm` | Integer | NULLABLE | User height in centimeters |
| `weight_kg` | Double | NULLABLE | User weight in kilograms |
| `fitness_goal` | String | NULLABLE | User's fitness goal (e.g., "Lose Weight", "Build Muscle") |
| `target_weight_kg` | Double | NULLABLE | Target weight in kilograms |
| `weekly_goal_sessions` | Integer | NULLABLE | Target number of workout sessions per week |
| `updated_at` | Long | NOT NULL | Last update timestamp (epoch milliseconds) |

---

## Table 2: `exercises`
**Purpose:** Store exercise definitions and metadata

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `id` | String | PRIMARY KEY | Unique exercise identifier |
| `name` | String | NOT NULL | Exercise name |
| `category` | String | NULLABLE | Exercise category (e.g., "Strength", "Cardio", "Generated") |
| `difficulty` | String | NULLABLE | Difficulty level (e.g., "Beginner", "Intermediate", "Advanced") |
| `description` | String | NULLABLE | Exercise description |
| `calories_per_rep` | Double | NOT NULL | Estimated calories burned per repetition |
| `thumbnail_url` | String | NULLABLE | URL to exercise thumbnail/preview image |

**Notes:**
- Generated exercises from videos are automatically added with `category = "Generated"`
- Default values for generated exercises: `difficulty = "Intermediate"`, `calories_per_rep = 3.0`

---

## Table 3: `sessions`
**Purpose:** Store workout session records with performance metrics

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `id` | String | PRIMARY KEY | Unique session identifier |
| `user_id` | String | NOT NULL | Foreign key to profiles.id |
| `exercise_name` | String | NULLABLE | Name of exercise performed |
| `target_reps` | Integer | NULLABLE | Target repetitions for this session |
| `total_reps` | Integer | NULLABLE | Actual repetitions completed |
| `accuracy_percent` | Double | NULLABLE | Overall form accuracy percentage (0-100) |
| `total_calories` | Double | NULLABLE | Total calories burned in session |
| `started_at` | Long | NOT NULL | Session start timestamp (epoch milliseconds) |
| `ended_at` | Long | NULLABLE | Session end timestamp (epoch milliseconds) |
| `duration_sec` | Integer | NOT NULL | Session duration in seconds |
| `status` | String | NOT NULL | Session status (e.g., "completed", "in_progress", "cancelled") |
| `updated_at` | Long | NOT NULL | Last update timestamp (epoch milliseconds) |

**Relationships:**
- `user_id` → `profiles.id` (Many-to-One)

---

## Table 4: `session_metrics`
**Purpose:** Store detailed metrics for individual sessions

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `id` | String | PRIMARY KEY | Unique metric identifier |
| `session_id` | String | NOT NULL | Foreign key to sessions.id |
| `user_id` | String | NOT NULL | Foreign key to profiles.id |
| `metric_name` | String | NOT NULL | Name of the metric (e.g., "avg_hold_time", "peak_angle") |
| `metric_value` | Double | NOT NULL | Value of the metric |
| `updated_at` | Long | NOT NULL | Last update timestamp (epoch milliseconds) |

**Relationships:**
- `session_id` → `sessions.id` (Many-to-One)
- `user_id` → `profiles.id` (Many-to-One)

**Notes:**
- Allows flexible metric tracking without schema changes
- Multiple metrics can be associated with a single session

---

## Table 5: `pose_graphs`
**Purpose:** Store metadata and references to pose graph JSON files

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `id` | String | PRIMARY KEY | Unique pose graph identifier |
| `user_id` | String | NOT NULL | Foreign key to profiles.id |
| `graph_name` | String | NOT NULL | Human-readable name of the graph |
| `storage_path` | String | NOT NULL | File path to the JSON graph file |
| `metadata_json` | String | NULLABLE | Additional metadata in JSON format |
| `updated_at` | Long | NOT NULL | Last update timestamp (epoch milliseconds) |

**Relationships:**
- `user_id` → `profiles.id` (Many-to-One)

**Notes:**
- Actual pose graph data stored in separate JSON files (not in database)
- `storage_path` typically points to: `<app_data>/files/graphs/<graph_name>.json`
- Generated graphs are created in graph generation workflow

---

## Table 6: `daily_stats`
**Purpose:** Store aggregated daily statistics for users

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `id` | String | PRIMARY KEY | Unique daily stat identifier |
| `user_id` | String | NOT NULL | Foreign key to profiles.id |
| `stat_date` | String | NOT NULL | Date in format (e.g., "2024-01-15") |
| `sessions_count` | Integer | NOT NULL | Number of sessions completed on this date |
| `total_reps` | Integer | NOT NULL | Total repetitions across all sessions |
| `avg_accuracy` | Double | NOT NULL | Average accuracy across all sessions (0-100) |
| `updated_at` | Long | NOT NULL | Last update timestamp (epoch milliseconds) |

**Relationships:**
- `user_id` → `profiles.id` (Many-to-One)

**Indexes:**
- Should have index on `(user_id, stat_date)` for efficient daily queries

---

## Table 7: `sync_queue`
**Purpose:** Queue for synchronizing local data with remote server

| Column Name | Data Type | Constraints | Description |
|-------------|-----------|-------------|-------------|
| `id` | Long | PRIMARY KEY (AUTO) | Auto-generated queue item ID |
| `entity_type` | String | NOT NULL | Type of entity (e.g., "session", "profile", "daily_stat") |
| `entity_id` | String | NOT NULL | ID of the entity to sync |
| `operation` | String | NOT NULL | Operation type (e.g., "INSERT", "UPDATE", "DELETE") |
| `payload` | String | NOT NULL | JSON payload of data to sync |
| `retry_count` | Integer | DEFAULT 0 | Number of retry attempts made |
| `last_error` | String | NULLABLE | Last error message if sync failed |
| `created_at` | Long | NOT NULL | Queue item creation timestamp (epoch milliseconds) |

**Notes:**
- Enables offline-first architecture
- Items are processed sequentially
- Failed items can be retried based on `retry_count`

---

## Entity Relationships Diagram (ERD)

```
profiles (1) ──< (M) sessions
    │
    │
    ├──< (M) session_metrics
    │
    ├──< (M) pose_graphs
    │
    └──< (M) daily_stats

sessions (1) ──< (M) session_metrics

sync_queue (independent - references other tables by entity_id)
exercises (independent - referenced by sessions via exercise_name string)
```

---

## Database Access Objects (DAOs)

The database provides the following DAO interfaces:

1. **ProfileDao** - CRUD operations for user profiles
2. **SessionDao** - CRUD operations for workout sessions
3. **SessionMetricDao** - CRUD operations for session metrics
4. **ExerciseDao** - CRUD operations for exercises
5. **PoseGraphDao** - CRUD operations for pose graph metadata
6. **DailyStatDao** - CRUD operations for daily statistics
7. **SyncQueueDao** - Queue management operations

---

## Notes & Conventions

### Timestamps
- All timestamps stored as `Long` in epoch milliseconds
- Column naming: `*_at` (e.g., `updated_at`, `started_at`, `created_at`)

### Foreign Keys
- Currently using String-based references (no enforced FK constraints)
- Relationship maintained at application level
- Column naming: `*_id` (e.g., `user_id`, `session_id`)

### Naming Conventions
- Tables: lowercase with underscores (`snake_case`)
- Columns: lowercase with underscores (`snake_case`)
- Multi-word column names use `@ColumnInfo` annotation
- Entity classes use camelCase in Kotlin

### Data Types
- **String**: Text, IDs, enum values (stored as strings)
- **Integer**: Counts, whole numbers
- **Double**: Decimals, percentages, metrics
- **Long**: Timestamps (epoch milliseconds)

### Nullable vs Non-Nullable
- Primary keys and foreign keys: NOT NULL
- Timestamps: Generally NOT NULL
- User-provided data: Often NULLABLE
- Computed/aggregated values: Generally NOT NULL (use 0 as default)

---

## Migration History

**Current Version:** 5

Previous migrations should be documented here when database schema changes occur.
