-- 옛 시드 상품의 image_url에 남아 있던 이모지를 NULL로 지우는 일회성 증분이다.
--
-- **왜 필요한가**: image_url이 응답에 실리기 전에는 DB 안에만 있어 해가 없었다.
-- imageUrl을 API에 노출하면 화면이 `🎒`를 이미지 주소로 받아 그대로 깨진다.
-- NULL이면 화면이 `/products/{id}.webp` 규약으로 폴백하므로 지우는 것이 맞다.
--
-- 대상은 아래 여섯 행뿐이다. **길이나 패턴 조건으로 훑지 않는다** — 나중에 짧은 상대 경로를
-- 정상 값으로 넣으면 그것까지 지운다. 시드가 이모지를 심던 시절의 알려진 행만 명시한다.
-- 시드 원본(03-deploy-seed.sql)은 이미 NULL로 고쳤으므로 새 인스턴스에는 이 파일이 필요 없다.
--
-- 적용(비밀번호는 컨테이너 env로만 넘긴다):
--   docker compose exec -T mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" mcmory_java' < 08-image-url-cleanup.sql

SET NAMES utf8mb4;

-- id 1,2,3,5는 06-real-products.sql이 실제 CDN 주소로 이미 덮었다. 남은 여섯이 이모지다
UPDATE product SET image_url = NULL WHERE id IN (4, 6, 7, 8, 9, 10) AND image_url NOT LIKE 'http%';
