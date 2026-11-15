CREATE TABLE IF NOT EXISTS news_article_counts (
  article_id TEXT PRIMARY KEY,
  likes INTEGER NOT NULL DEFAULT 0 CHECK (likes >= 0 AND likes <= 10000),
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS news_reactions (
  article_id TEXT NOT NULL,
  voter_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (article_id, voter_id),
  FOREIGN KEY (article_id) REFERENCES news_article_counts(article_id)
);

CREATE INDEX IF NOT EXISTS news_reactions_voter_idx ON news_reactions(voter_id);
