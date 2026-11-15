ALTER TABLE news_action_limits ADD COLUMN blocked_until INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS news_action_limits_block_idx ON news_action_limits(blocked_until);
