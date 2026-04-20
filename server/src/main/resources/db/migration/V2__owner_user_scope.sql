-- Scope domain rows to auth_users: regular users see only their rows; ADMIN sees all (application layer).

ALTER TABLE management_task_categories ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES auth_users (id);
ALTER TABLE management_task_types ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES auth_users (id);
ALTER TABLE management_tasks ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES auth_users (id);
ALTER TABLE fitness_exercises ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES auth_users (id);
ALTER TABLE fitness_exercise_day_logs ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES auth_users (id);
ALTER TABLE fitness_body_weight ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES auth_users (id);
ALTER TABLE robinhood_transactions ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES auth_users (id);

-- Assign legacy rows to the first existing user (typically bootstrap admin) when possible.
UPDATE management_task_categories
SET owner_user_id = (SELECT id FROM auth_users ORDER BY id LIMIT 1)
WHERE owner_user_id IS NULL
  AND EXISTS (SELECT 1 FROM auth_users);

UPDATE management_task_types
SET owner_user_id = (SELECT id FROM auth_users ORDER BY id LIMIT 1)
WHERE owner_user_id IS NULL
  AND EXISTS (SELECT 1 FROM auth_users);

UPDATE management_tasks
SET owner_user_id = (SELECT id FROM auth_users ORDER BY id LIMIT 1)
WHERE owner_user_id IS NULL
  AND EXISTS (SELECT 1 FROM auth_users);

UPDATE fitness_exercises
SET owner_user_id = (SELECT id FROM auth_users ORDER BY id LIMIT 1)
WHERE owner_user_id IS NULL
  AND EXISTS (SELECT 1 FROM auth_users);

UPDATE fitness_exercise_day_logs
SET owner_user_id = (SELECT id FROM auth_users ORDER BY id LIMIT 1)
WHERE owner_user_id IS NULL
  AND EXISTS (SELECT 1 FROM auth_users);

UPDATE fitness_body_weight
SET owner_user_id = (SELECT id FROM auth_users ORDER BY id LIMIT 1)
WHERE owner_user_id IS NULL
  AND EXISTS (SELECT 1 FROM auth_users);

UPDATE robinhood_transactions
SET owner_user_id = (SELECT id FROM auth_users ORDER BY id LIMIT 1)
WHERE owner_user_id IS NULL
  AND EXISTS (SELECT 1 FROM auth_users);

CREATE INDEX IF NOT EXISTS idx_mgmt_categories_owner ON management_task_categories (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_mgmt_types_owner ON management_task_types (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_mgmt_tasks_owner ON management_tasks (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_fitness_exercises_owner ON fitness_exercises (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_fitness_day_logs_owner ON fitness_exercise_day_logs (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_fitness_body_weight_owner ON fitness_body_weight (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_robinhood_owner_activity ON robinhood_transactions (owner_user_id, activity_date);

-- Enforce NOT NULL when every row has an owner (skipped if legacy NULLs remain).
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM management_task_categories WHERE owner_user_id IS NULL) THEN
            ALTER TABLE management_task_categories ALTER COLUMN owner_user_id SET NOT NULL;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM management_task_types WHERE owner_user_id IS NULL) THEN
            ALTER TABLE management_task_types ALTER COLUMN owner_user_id SET NOT NULL;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM management_tasks WHERE owner_user_id IS NULL) THEN
            ALTER TABLE management_tasks ALTER COLUMN owner_user_id SET NOT NULL;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM fitness_exercises WHERE owner_user_id IS NULL) THEN
            ALTER TABLE fitness_exercises ALTER COLUMN owner_user_id SET NOT NULL;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM fitness_exercise_day_logs WHERE owner_user_id IS NULL) THEN
            ALTER TABLE fitness_exercise_day_logs ALTER COLUMN owner_user_id SET NOT NULL;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM fitness_body_weight WHERE owner_user_id IS NULL) THEN
            ALTER TABLE fitness_body_weight ALTER COLUMN owner_user_id SET NOT NULL;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM robinhood_transactions WHERE owner_user_id IS NULL) THEN
            ALTER TABLE robinhood_transactions ALTER COLUMN owner_user_id SET NOT NULL;
        END IF;
    END
$$;
