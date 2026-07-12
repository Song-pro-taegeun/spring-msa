-- Flyway는 기본적으로 멱등적
-- 하지만 migration 중간 장애까지 안전하게 보려면 DDL도 IF NOT EXISTS로 작성하는게 좋음
CREATE TABLE IF NOT EXISTS users(
   user_id varchar(50) NOT NULL COMMENT '사용자 ID',
   user_name varchar(100) NOT NULL COMMENT '사용자 이름',
   reg_dtm timestamp NULL DEFAULT current_timestamp() COMMENT '등록일자',
   PRIMARY KEY (user_id)
);