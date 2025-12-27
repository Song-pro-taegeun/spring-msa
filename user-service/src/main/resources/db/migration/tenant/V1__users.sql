CREATE TABLE users(
   user_id varchar(50) NOT NULL COMMENT '사용자 ID',
   user_name varchar(100) NOT NULL COMMENT '사용자 이름',
   reg_dtm timestamp NULL DEFAULT current_timestamp() COMMENT '등록일자',
   PRIMARY KEY (user_id)
);