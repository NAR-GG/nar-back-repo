-- #20 프로필 저장 연동: 닉네임을 이름(name) + 태그(tag)로 분리한다.
-- 롤처럼 같은 이름이라도 태그가 다르면 공존할 수 있도록 (name, tag) 복합 UNIQUE 로 전환.

ALTER TABLE member ADD COLUMN name VARCHAR(50);
ALTER TABLE member ADD COLUMN tag  VARCHAR(10);

-- 기존 nickname("이름#태그")을 마지막 '#' 기준으로 분리해 백필.
-- '#'이 없던 레거시 닉네임은 전체를 이름으로 두고 태그는 id 기반으로 채워 유일성 보장.
UPDATE member
SET name = CASE WHEN nickname LIKE '%#%' THEN SUBSTRING_INDEX(nickname, '#', 1) ELSE nickname END,
    tag  = CASE WHEN nickname LIKE '%#%' THEN SUBSTRING_INDEX(nickname, '#', -1) ELSE LPAD(id, 4, '0') END;

ALTER TABLE member MODIFY COLUMN name VARCHAR(50) NOT NULL;
ALTER TABLE member MODIFY COLUMN tag  VARCHAR(10) NOT NULL;

-- 단일 nickname UNIQUE 컬럼 제거(컬럼 삭제 시 단일 컬럼 UNIQUE 인덱스도 함께 제거됨).
ALTER TABLE member DROP COLUMN nickname;

ALTER TABLE member ADD CONSTRAINT uq_member_name_tag UNIQUE (name, tag);
