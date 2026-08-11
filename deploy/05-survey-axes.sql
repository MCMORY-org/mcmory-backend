-- 운영 DB에 friend.survey_axes 컬럼을 더하는 증분이다(FEAT-W003 질문 선별).
--
-- **`main` 머지보다 먼저 적용할 것.** ddl-auto가 validate라 컬럼이 없으면 배포 서버가 기동 자체를 실패한다.
-- 컨테이너 init은 볼륨이 빌 때만 돌므로 기동 중인 DB에는 손으로 한 번 적용한다(FEAT-W001 선례).
--
-- 적용: docker compose exec -T mysql mysql --default-character-set=utf8mb4 -u root -p"$DB_ROOT_PASSWORD" mcmory_java < 05-survey-axes.sql

SET NAMES utf8mb4;

-- NULL은 세 축 전부라는 뜻이다. 기존 행을 채우지 않는 것이 정상이며 채우면 옛 링크의 설문이 갑자기 좁아진다.
ALTER TABLE friend ADD COLUMN survey_axes VARCHAR(32) NULL AFTER survey_token;
