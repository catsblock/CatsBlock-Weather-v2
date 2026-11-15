CREATE TABLE IF NOT EXISTS news_likes (
  article_id TEXT NOT NULL,
  voter_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (article_id, voter_id)
);

INSERT OR IGNORE INTO news_likes(article_id, voter_id, created_at)
SELECT article_id, voter_id, created_at FROM news_reactions;

CREATE INDEX IF NOT EXISTS news_likes_article_idx ON news_likes(article_id);
CREATE INDEX IF NOT EXISTS news_reaction_votes_article_idx ON news_reaction_votes(article_id);

CREATE TABLE IF NOT EXISTS news_action_limits (
  key TEXT PRIMARY KEY,
  window_start INTEGER NOT NULL,
  used INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS news_action_limits_window_idx ON news_action_limits(window_start);

DROP TABLE IF EXISTS news_reactions;
DROP TABLE IF EXISTS news_article_counts;
DROP TABLE IF EXISTS news_reaction_counts;
