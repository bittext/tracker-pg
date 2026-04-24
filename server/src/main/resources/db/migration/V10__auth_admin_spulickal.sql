-- Restore admin role for primary account (Logs tab + /api/admin/logs require ADMIN in JWT).
UPDATE auth_users
SET role = 'ADMIN'
WHERE lower(trim(username)) = lower(trim('spulickal'));
