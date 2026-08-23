-- 스케줄러 리더 리스. "지금 @Scheduled 를 돌려도 되는 파드는 누구인가"의 단일 진실.
--
-- 스케줄러 파드는 중복 실행 가드가 없어 두 벌이 돌면 라이브 폴링·푸시가 이중으로 나간다.
-- 지금은 replicas 1 + Recreate 로 "물리적으로 하나"를 보장하는데, 그 대가가 배포마다
-- 38~48초의 폴링 공백이다(그 사이 세트가 시작되면 알림·라이브위젯이 통째로 누락된다).
--
-- 이 리스가 "논리적으로 하나"를 보장하면 Recreate 를 RollingUpdate 로 바꿀 수 있다 —
-- 새 파드가 떠서 대기하다가 구 파드가 리스를 놓는 순간 이어받으므로 공백이 사라진다.
-- 전환은 두 단계다: 이 리스를 먼저 넣어 Recreate 아래에서 검증하고(파드가 하나라 무해),
-- 리스 로그가 깨끗한 것을 본 뒤에 RollingUpdate 로 바꾼다.
--
-- k8s Lease(coordination.k8s.io)가 아니라 DB 행인 이유 — kubernetes-client 의존과
-- ServiceAccount RBAC 가 새로 필요한데, MySQL 은 이미 두 파드가 공유한다. 원자성은
-- UPDATE ... WHERE 조건절이 준다.
--
-- 행은 항상 정확히 하나(id=1)다. 여기서 미리 심어 두면 획득 경로가 INSERT 경쟁 없이
-- UPDATE 한 문장이 된다. holder='' + 과거 시각이라 첫 파드가 즉시 가져간다.
CREATE TABLE scheduler_lease (
    id         TINYINT      NOT NULL,
    holder     VARCHAR(64)  NOT NULL,
    expires_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO scheduler_lease (id, holder, expires_at)
VALUES (1, '', NOW(3) - INTERVAL 1 SECOND);
