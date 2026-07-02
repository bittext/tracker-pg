-- nisha was connected with pulickal-agentic OAuth; remove connection so she must paste nisha-agentic tokens.

DELETE FROM robinhood_agentic_synced_orders o
WHERE o.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) = 'nisha'
);

DELETE FROM robinhood_agentic_positions p
WHERE p.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) = 'nisha'
);

DELETE FROM robinhood_agentic_connections c
WHERE c.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) = 'nisha'
);
