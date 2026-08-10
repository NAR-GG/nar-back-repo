-- 알림 잠자기(방해금지 시간). 이 시간대에는 푸시를 소리 없이 보내 알림함에만 쌓는다.
-- 기본값 OFF: Android 무음은 신버전 앱이 만드는 채널이 있어야 하고 서버는 앱 버전을 모른다.
-- 켜져 있다 = 신버전 앱이다 가 성립해야 구버전 기기에서 알림이 유실되지 않는다.
ALTER TABLE member
  ADD COLUMN quiet_hours_enabled TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN quiet_start_time TIME NOT NULL DEFAULT '01:00:00',
  ADD COLUMN quiet_end_time   TIME NOT NULL DEFAULT '08:00:00';
