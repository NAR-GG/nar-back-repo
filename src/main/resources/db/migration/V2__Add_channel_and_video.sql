CREATE TABLE channel (
                         channel_id INTEGER PRIMARY KEY AUTOINCREMENT,
                         youtube_channel_id VARCHAR(255) NOT NULL UNIQUE,
                         channel_name VARCHAR(255) NOT NULL,
                         profile_image_url VARCHAR(255),
                         upload_playlist_id VARCHAR(255),
                         channel_type VARCHAR(50)
);

-- Video 테이블 생성
CREATE TABLE video (
                       video_id INTEGER PRIMARY KEY AUTOINCREMENT,
                       channel_id INTEGER,
                       youtube_video_id VARCHAR(255) NOT NULL UNIQUE,
                       title VARCHAR(255),
                       thumbnail_url VARCHAR(255),
                       video_url VARCHAR(255),
                       published_at TIMESTAMP,
                       CONSTRAINT fk_channel FOREIGN KEY (channel_id) REFERENCES channel(channel_id)
);