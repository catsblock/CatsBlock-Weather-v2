CREATE TABLE IF NOT EXISTS news_reaction_votes (
  article_id TEXT NOT NULL,
  voter_id TEXT NOT NULL,
  reaction TEXT NOT NULL CHECK (reaction IN ('🤩', '😁', '😭', '⚡', '👍', '👏')),
  created_at INTEGER NOT NULL,
  PRIMARY KEY (article_id, voter_id)
);

CREATE TABLE IF NOT EXISTS news_reaction_counts (
  article_id TEXT NOT NULL,
  reaction TEXT NOT NULL CHECK (reaction IN ('🤩', '😁', '😭', '⚡', '👍', '👏')),
  votes INTEGER NOT NULL DEFAULT 0 CHECK (votes >= 0 AND votes <= 10000),
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (article_id, reaction)
);

CREATE INDEX IF NOT EXISTS news_reaction_votes_voter_idx ON news_reaction_votes(voter_id);
CREATE INDEX IF NOT EXISTS news_reaction_counts_article_idx ON news_reaction_counts(article_id);
