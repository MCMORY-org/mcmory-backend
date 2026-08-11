-- 운영 DB에 코디용 의류 8건만 넣는 증분이다(FEAT-W002).
--
-- **03-deploy-seed.sql을 통째로 다시 돌리지 말 것** — 단일 INSERT라 id 1에서 10이 중복 키로 실패한다.
-- 이 파일은 기동 중인 운영 DB에 손으로 한 번 적용한다. 컨테이너 init은 볼륨이 빌 때만 돈다.
--
-- 적용: docker compose exec -T mysql mysql --default-character-set=utf8mb4 -u root -p"$DB_ROOT_PASSWORD" mcmory_java < 04-clothing-increment.sql

SET NAMES utf8mb4;

-- **id 11에서 18은 유지한다** — 프론트 이미지 규약이 /products/{id}.webp라 id가 바뀌면 전부 다시 매핑해야 한다.
INSERT INTO product (id, name, category, color, price, official_url, style_tags) VALUES
  (11, '워싱 데님 재킷', 'WOMAN OUTER', '블루', 1250000, 'https://kr.mcmworldwide.com', JSON_ARRAY('캐주얼','스트릿')),
  (12, '루렉스 데님 플레어 팬츠', 'WOMAN BOTTOM', '블루', 830000, 'https://kr.mcmworldwide.com', JSON_ARRAY('캐주얼','러블리')),
  (13, '로고 자카드 니트', 'WOMAN TOP', '베이지', 690000, 'https://kr.mcmworldwide.com', JSON_ARRAY('클래식','미니멀')),
  (14, '비세토스 실크 블라우스', 'WOMAN TOP', '화이트', 580000, 'https://kr.mcmworldwide.com', JSON_ARRAY('포멀','클래식')),
  (15, '테일러드 울 코트', 'WOMAN OUTER', '블랙', 1890000, 'https://kr.mcmworldwide.com', JSON_ARRAY('포멀','클래식')),
  (16, '플리츠 미디 스커트', 'WOMAN BOTTOM', '핑크', 620000, 'https://kr.mcmworldwide.com', JSON_ARRAY('러블리','미니멀')),
  (17, '오버핏 후디', 'WOMAN TOP', '그레이', 450000, 'https://kr.mcmworldwide.com', JSON_ARRAY('스트릿','캐주얼')),
  (18, '스트레이트 슬랙스', 'WOMAN BOTTOM', '블랙', 520000, 'https://kr.mcmworldwide.com', JSON_ARRAY('미니멀','포멀'));
