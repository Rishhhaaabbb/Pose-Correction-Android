-- Milestone 1-3 schema: profiles, sessions, session_metrics, pose_graphs, daily_stats.
-- Run this in Supabase SQL editor.
--
-- Overview
-- 1) profiles: user-level profile and fitness preferences.
-- 2) sessions: one workout session summary per completed/started workout.
-- 3) session_metrics: per-session KPI values (accuracy, streak, etc).
-- 4) pose_graphs: graph metadata references used by training/analysis flows.
-- 5) daily_stats: per-user daily aggregates for dashboards/trends.
--
-- Security model
-- - All tables use RLS.
-- - Users can only read/insert/update rows where auth.uid() matches row ownership.
-- - No delete policies are defined in this script.

create extension if not exists pgcrypto;

-- Stores app profile fields for each authenticated user.
-- PK is auth.users.id to enforce one profile row per user.
create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null,
    email text,
    profile_image_url text,
    height_cm integer,
    weight_kg double precision,
    fitness_goal text,
    target_weight_kg double precision,
    weekly_goal_sessions integer,
    updated_at bigint not null default (extract(epoch from now()) * 1000)::bigint
);

-- Backward-safe migration lines: keep these for existing environments.
alter table public.profiles add column if not exists target_weight_kg double precision;
alter table public.profiles add column if not exists weekly_goal_sessions integer;
alter table public.profiles add column if not exists profile_image_url text;

-- Stores workout session-level summary data.
-- Time fields are epoch milliseconds to align with mobile client storage.
create table if not exists public.sessions (
    id uuid primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    exercise_name text,
    total_reps integer,
    accuracy_percent double precision,
    started_at bigint not null,
    ended_at bigint,
    duration_sec integer not null default 0,
    status text not null,
    updated_at bigint not null default (extract(epoch from now()) * 1000)::bigint
);

-- Backward-safe migration lines: keep these for existing environments.
alter table public.sessions add column if not exists exercise_name text;
alter table public.sessions add column if not exists total_reps integer;
alter table public.sessions add column if not exists accuracy_percent double precision;

-- Stores session KPIs (name/value pairs) for flexible metric expansion.
create table if not exists public.session_metrics (
    id uuid primary key,
    session_id uuid not null references public.sessions(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    metric_name text not null,
    metric_value double precision not null,
    updated_at bigint not null default (extract(epoch from now()) * 1000)::bigint
);

-- Stores graph metadata and source path references.
-- Note: metadata_json is stored as text for flexible payload shape.
create table if not exists public.pose_graphs (
    id uuid primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    graph_name text not null,
    storage_path text not null,
    metadata_json text,
    updated_at bigint not null default (extract(epoch from now()) * 1000)::bigint
);

-- Stores one aggregate row per user per date.
-- Unique constraint prevents duplicate aggregate rows for same user/day.
create table if not exists public.daily_stats (
    id uuid primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    stat_date date not null,
    sessions_count integer not null default 0,
    total_reps integer not null default 0,
    avg_accuracy double precision not null default 0,
    updated_at bigint not null default (extract(epoch from now()) * 1000)::bigint,
    unique (user_id, stat_date)
);

-- Read-path indexes for user timeline queries.
create index if not exists idx_sessions_user_started
    on public.sessions(user_id, started_at desc);
create index if not exists idx_session_metrics_user
    on public.session_metrics(user_id, updated_at desc);
create index if not exists idx_pose_graphs_user
    on public.pose_graphs(user_id, updated_at desc);
create index if not exists idx_daily_stats_user_date
    on public.daily_stats(user_id, stat_date desc);

-- Enable row-level security on all app tables.
alter table public.profiles enable row level security;
alter table public.sessions enable row level security;
alter table public.session_metrics enable row level security;
alter table public.pose_graphs enable row level security;
alter table public.daily_stats enable row level security;

-- profiles policies: users operate only on their own profile row.
drop policy if exists "profiles_select_own" on public.profiles;
create policy "profiles_select_own"
    on public.profiles for select
    using (auth.uid() = id);

drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own"
    on public.profiles for insert
    with check (auth.uid() = id);

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own"
    on public.profiles for update
    using (auth.uid() = id)
    with check (auth.uid() = id);

-- sessions policies: users operate only on their own session rows.
drop policy if exists "sessions_select_own" on public.sessions;
create policy "sessions_select_own"
    on public.sessions for select
    using (auth.uid() = user_id);

drop policy if exists "sessions_insert_own" on public.sessions;
create policy "sessions_insert_own"
    on public.sessions for insert
    with check (auth.uid() = user_id);

drop policy if exists "sessions_update_own" on public.sessions;
create policy "sessions_update_own"
    on public.sessions for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- session_metrics policies: users operate only on their own metric rows.
drop policy if exists "session_metrics_select_own" on public.session_metrics;
create policy "session_metrics_select_own"
    on public.session_metrics for select
    using (auth.uid() = user_id);

drop policy if exists "session_metrics_insert_own" on public.session_metrics;
create policy "session_metrics_insert_own"
    on public.session_metrics for insert
    with check (auth.uid() = user_id);

drop policy if exists "session_metrics_update_own" on public.session_metrics;
create policy "session_metrics_update_own"
    on public.session_metrics for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- pose_graphs policies: users operate only on their own graph metadata rows.
drop policy if exists "pose_graphs_select_own" on public.pose_graphs;
create policy "pose_graphs_select_own"
    on public.pose_graphs for select
    using (auth.uid() = user_id);

drop policy if exists "pose_graphs_insert_own" on public.pose_graphs;
create policy "pose_graphs_insert_own"
    on public.pose_graphs for insert
    with check (auth.uid() = user_id);

drop policy if exists "pose_graphs_update_own" on public.pose_graphs;
create policy "pose_graphs_update_own"
    on public.pose_graphs for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- daily_stats policies: users operate only on their own daily aggregate rows.
drop policy if exists "daily_stats_select_own" on public.daily_stats;
create policy "daily_stats_select_own"
    on public.daily_stats for select
    using (auth.uid() = user_id);

drop policy if exists "daily_stats_insert_own" on public.daily_stats;
create policy "daily_stats_insert_own"
    on public.daily_stats for insert
    with check (auth.uid() = user_id);

drop policy if exists "daily_stats_update_own" on public.daily_stats;
create policy "daily_stats_update_own"
    on public.daily_stats for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);
