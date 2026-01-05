CREATE TABLE news_post (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title VARCHAR(255) NOT NULL,
    sub_content TEXT,
    thumbnail VARCHAR(500),
    post_url VARCHAR(500) NOT NULL,
    office_name VARCHAR(100),
    created_at DATETIME,
    last_updated DATETIME
);

CREATE UNIQUE INDEX idx_news_post_url ON news_post(post_url);
CREATE INDEX idx_news_post_date ON news_post(created_at DESC);