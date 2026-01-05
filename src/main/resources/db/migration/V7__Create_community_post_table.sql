CREATE TABLE community_post (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    community_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(100) NOT NULL,
    post_url VARCHAR(500) NOT NULL,
    created_at DATETIME,
    view_count INTEGER DEFAULT 0,
    vote_count INTEGER DEFAULT 0,
    comment_count INTEGER DEFAULT 0,
    last_updated DATETIME
);

CREATE UNIQUE INDEX idx_community_post_url ON community_post(post_url);
CREATE INDEX idx_community_type_date ON community_post(community_type, created_at DESC);
