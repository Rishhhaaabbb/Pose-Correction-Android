-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =========================
-- PROFILES (with added fields)
-- =========================
CREATE TABLE profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    display_name TEXT NOT NULL UNIQUE,
    email TEXT,
    profile_image_url TEXT,
    height_cm INTEGER,
    weight_kg DECIMAL,
    fitness_goal TEXT,
    target_weight_kg DECIMAL,
    weekly_goal_sessions INTEGER,
    level INTEGER DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- =========================
-- EXERCISES (seeded from assets)
-- =========================
CREATE TABLE exercises (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL UNIQUE,
    category TEXT,
    difficulty TEXT,
    description TEXT,
    thumbnail_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Seed exercises from assets folder
INSERT INTO exercises (name, category, difficulty, description) VALUES
-- Legs
('squat', 'Legs', 'Beginner', 'Stand with feet shoulder-width apart, lower your body by bending knees and hips, then stand back up.'),
('wide-squat', 'Legs', 'Beginner', 'Stand with feet wider than shoulder-width, lower body by bending knees, keeping chest up.'),
('squat-hold', 'Legs', 'Intermediate', 'Hold the squat position for a specified duration to build endurance.'),
('lunge', 'Legs', 'Intermediate', 'Step forward with one leg, lowering hips until both knees are bent at 90 degrees.'),
('high-knees', 'Cardio', 'Intermediate', 'Run in place, lifting knees as high as possible toward chest.'),
('side-leg-raise', 'Legs', 'Intermediate', 'Lie on your side, lift the top leg toward the ceiling while keeping it straight.'),
('lying-leg-raise', 'Legs', 'Intermediate', 'Lie on your back, lift both legs toward the ceiling while keeping them straight.'),
('bridge', 'Core', 'Beginner', 'Lie on your back with knees bent, lift hips toward ceiling, squeezing glutes.'),

-- Chest
('pushup', 'Chest', 'Beginner', 'Start in plank position, lower chest to ground by bending elbows, push back up.'),
('knee-pushup', 'Chest', 'Beginner', 'Perform pushup with knees on ground to reduce difficulty.'),
('chair-dip', 'Chest', 'Intermediate', 'Sit on edge of chair, lower body by bending elbows, then push back up.'),

-- Core
('plank', 'Core', 'Beginner', 'Hold push-up position with arms straight, body forming a straight line.'),
('side-plank', 'Core', 'Intermediate', 'Balance on one arm and side of foot, body forming a straight line sideways.'),
('side-plank-raise', 'Core', 'Intermediate', 'From side plank, raise top leg toward ceiling for added challenge.'),
('deadbug', 'Core', 'Intermediate', 'Lie on back, alternate extending opposite arm and leg while keeping back flat.'),
('bird-dog', 'Core', 'Beginner', 'On hands and knees, extend opposite arm and leg simultaneously, hold, then switch.'),
('toe-touch-crunch', 'Core', 'Beginner', 'Lie on back, reach toward toes while lifting shoulders off ground.'),
('side-touch', 'Core', 'Beginner', 'Sit with knees bent, lean to sides touching hands to floor beside hips.'),
('knee-hug', 'Core', 'Beginner', 'Lie on back, hug knees to chest, gently rock side to side.'),
('torso-twist', 'Core', 'Beginner', 'Sit with knees bent, rotate torso left and right to engage obliques.'),

-- Stretch
('tree', 'Stretch', 'Beginner', 'Stand on one leg, place other foot on inner thigh, hands in prayer position.'),
('downward-dog', 'Stretch', 'Intermediate', 'Form an inverted V shape with body, hands and feet on ground, hips lifted high.'),
('child', 'Stretch', 'Beginner', 'Kneel, sit back on heels, extend arms forward on ground, rest forehead down.'),
('cobrapose', 'Stretch', 'Beginner', 'Lie on stomach, push up on hands, arching back while hips stay on ground.'),
('tricep-stretch', 'Stretch', 'Beginner', 'Raise arm overhead, bend elbow, use other hand to gently push elbow down.'),
('shoulder-stretch', 'Stretch', 'Beginner', 'Cross one arm across body, use other arm to press it toward chest.'),
('neck-stretch', 'Stretch', 'Beginner', 'Gently tilt head to one side, hold, then switch to other side.'),
('leg-cradle', 'Stretch', 'Beginner', 'Lie on back, bring one knee to chest, use hands to cradle and stretch.');

-- =========================
-- SESSIONS
-- =========================
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    exercise_id UUID NOT NULL,

    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,

    status TEXT NOT NULL DEFAULT 'completed',
    created_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT fk_sessions_user
        FOREIGN KEY (user_id)
        REFERENCES profiles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_sessions_exercise
        FOREIGN KEY (exercise_id)
        REFERENCES exercises(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_exercise_id ON sessions(exercise_id);

-- =========================
-- FEEDBACK LOGS
-- =========================
CREATE TABLE feedback_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT fk_feedback_session
        FOREIGN KEY (session_id)
        REFERENCES sessions(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_feedback_session_id ON feedback_logs(session_id);

-- =========================
-- DAILY STATS
-- =========================
CREATE TABLE daily_stats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    stat_date DATE NOT NULL,

    total_sessions INTEGER DEFAULT 0,
    total_reps INTEGER DEFAULT 0,
    avg_accuracy DECIMAL DEFAULT 0,

    CONSTRAINT fk_daily_user
        FOREIGN KEY (user_id)
        REFERENCES profiles(id)
        ON DELETE CASCADE,

    CONSTRAINT unique_user_date UNIQUE (user_id, stat_date)
);

CREATE INDEX idx_daily_user_date ON daily_stats(user_id, stat_date);

-- =========================
-- ROW LEVEL SECURITY
-- =========================
ALTER TABLE profiles     ENABLE ROW LEVEL SECURITY;
ALTER TABLE exercises    ENABLE ROW LEVEL SECURITY;
ALTER TABLE sessions    ENABLE ROW LEVEL SECURITY;
ALTER TABLE feedback_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE daily_stats  ENABLE ROW LEVEL SECURITY;

-- profiles: users manage their own row
CREATE POLICY "profiles_select" ON profiles FOR SELECT USING (auth.uid() = id);
CREATE POLICY "profiles_insert" ON profiles FOR INSERT WITH CHECK (auth.uid() = id);
CREATE POLICY "profiles_update" ON profiles FOR UPDATE USING (auth.uid() = id);

-- exercises: readable by all authenticated users (shared catalogue)
CREATE POLICY "exercises_select" ON exercises FOR SELECT USING (auth.role() = 'authenticated');

-- sessions: scoped to the owning user
CREATE POLICY "sessions_select" ON sessions FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "sessions_insert" ON sessions FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "sessions_update" ON sessions FOR UPDATE USING (auth.uid() = user_id);

-- feedback_logs: accessible via session ownership
CREATE POLICY "feedback_select" ON feedback_logs FOR SELECT
    USING (EXISTS (SELECT 1 FROM sessions s WHERE s.id = feedback_logs.session_id AND s.user_id = auth.uid()));
CREATE POLICY "feedback_insert" ON feedback_logs FOR INSERT
    WITH CHECK (EXISTS (SELECT 1 FROM sessions s WHERE s.id = feedback_logs.session_id AND s.user_id = auth.uid()));

-- daily_stats: scoped to the owning user
CREATE POLICY "daily_stats_select" ON daily_stats FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "daily_stats_insert" ON daily_stats FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "daily_stats_update" ON daily_stats FOR UPDATE USING (auth.uid() = user_id);