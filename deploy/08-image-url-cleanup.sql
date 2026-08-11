-- 옛 시드 상품의 image_url에 남아 있던 이모지를 NULL로 지우는 증분이다(id 4,6,7,8,9,10).
--
-- **왜 지금인가**: `image_url`이 응답에 노출되기 전에는 DB 안에만 있어 해가 없었다.
-- imageUrl을 API에 실으면 화면이 `🎒`를 이미지 주소로 받아 그대로 깨진다.
-- NULL이면 화면이 기존 규약(`/products/{id}.webp`)으로 폴백하므로 지우는 것이 맞다.
--
-- FIX-W003이 응답에서 `emoji` 필드를 걷어낼 때 이 컬럼은 노출되지 않아 남겨뒀던 값이다.
--
-- 적용(비밀번호는 컨테이너 env로만 넘긴다):
--   docker compose exec -T mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" mcmory_java' < 08-image-url-cleanup.sql

SET NAMES utf8mb4;

-- 길이로 고른다. 실제 URL은 100자를 넘고 이모지는 1자다 — id를 나열하면 시드가 바뀔 때 어긋난다
UPDATE product SET image_url = NULL WHERE image_url IS NOT NULL AND CHAR_LENGTH(image_url) < 10;
