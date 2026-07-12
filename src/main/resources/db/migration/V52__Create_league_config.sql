-- 리그별 라이브 수집/디스코드 알림/경기 동기화 토글. 백오피스(/league-configs)에서 관리.
-- 행 시드는 앱 기동 시 LeagueConfigService 가 TARGET_LEAGUES 기준 insert-if-missing 으로 수행.
CREATE TABLE league_config (
    league_name          VARCHAR(30) NOT NULL PRIMARY KEY,
    live_enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
    notification_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    sync_enabled         BOOLEAN     NOT NULL DEFAULT TRUE
);
