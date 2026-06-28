-- 백오피스 admin 권한을 DB에서 관리. 기본 USER, admin 부여는 UPDATE member SET role='ADMIN' WHERE id=?
ALTER TABLE member
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
