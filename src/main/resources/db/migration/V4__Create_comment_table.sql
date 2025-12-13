CREATE TABLE comment (
    comment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    youtube_comment_id VARCHAR(255) NOT NULL UNIQUE,
    author_display_name VARCHAR(255),
    author_profile_image_url VARCHAR(1024),
    text_display TEXT,
    like_count BIGINT,
    published_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comment_video FOREIGN KEY (video_id) REFERENCES video (video_id)
);

CREATE INDEX idx_comment_video_id ON comment(video_id);
