CREATE TABLE esports_news (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title VARCHAR(255) NOT NULL,
    sub_content TEXT,
    thumbnail VARCHAR(500),
    post_url VARCHAR(500) NOT NULL,
    office_name VARCHAR(100),
    created_at DATETIME,
    last_updated DATETIME
);

CREATE UNIQUE INDEX idx_esports_news_url ON esports_news(post_url);
CREATE INDEX idx_esports_news_date ON esports_news(created_at DESC);
