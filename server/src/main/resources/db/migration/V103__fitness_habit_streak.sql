-- 50-day habit streaks on Exercise: min 20 min workout, and 2 hr self/work study.

CREATE TABLE fitness_habit_streak_window (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    start_date      DATE   NOT NULL,
    day_count       INT    NOT NULL DEFAULT 50,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fitness_habit_streak_window_owner UNIQUE (owner_user_id),
    CONSTRAINT chk_fitness_habit_streak_day_count CHECK (day_count BETWEEN 1 AND 365)
);

CREATE TABLE fitness_habit_streak_mark (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT      NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    habit_kind      VARCHAR(32) NOT NULL,
    activity_date   DATE        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fitness_habit_streak_mark UNIQUE (owner_user_id, habit_kind, activity_date),
    CONSTRAINT chk_fitness_habit_streak_kind CHECK (habit_kind IN ('EXERCISE_20MIN', 'STUDY_2HR'))
);

CREATE INDEX idx_fitness_habit_streak_mark_owner
    ON fitness_habit_streak_mark (owner_user_id, habit_kind, activity_date);
