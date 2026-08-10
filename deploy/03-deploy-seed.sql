-- 배포 DB 전용 시드. 상품 카탈로그와 매장만 담는다.
--
-- 회원과 친구 시드는 넣지 않는다. 그쪽 로그인 계정이 README에 공개돼 있어 배포 DB에 넣으면
-- 누구나 그 계정으로 들어올 수 있음. 배포 환경의 회원과 친구는 실제 가입으로 만든다.
--
-- 상품과 매장을 빼면 추천(FR-009)과 매장 예약이 빈 결과가 되어 시연이 막히므로 이 둘은 반드시 넣는다.
-- 내용 정본은 src/test/resources/db/02-seed.sql이고 그쪽 상품과 매장이 바뀌면 이 파일도 같은 커밋에서 갱신할 것.

-- 컨테이너의 character_set_client 기본값이 latin1이라 이 선언이 없으면 한글이 깨져 저장된다.
SET NAMES utf8mb4;

-- 스타일 태그가 추천 대역을 덮어야 개인화가 성립한다. 현재 대역: 클래식(1,2,7), 캐주얼(6,8),
-- 러블리(8), 포멀(7). 상품을 늘릴 때 빈 태그가 생기면 그 취향은 전체 카탈로그 폴백으로 떨어진다.
INSERT INTO product (id, name, category, color, price, image_url, official_url, style_tags, demo_serial) VALUES
  (1, 'Tracy 비세토스 크로스바디', '가방', '블랙', 890000, '👜', 'https://kr.mcmworldwide.com', JSON_ARRAY('미니멀','클래식'), 'MX2024A031'),
  (2, '비세토스 숄더백', '가방', '코냑', 750000, '👝', 'https://kr.mcmworldwide.com', JSON_ARRAY('클래식','미니멀'), 'MX2024B072'),
  (3, '비세토스 오리지널 카드 반지갑', '지갑', '코냑', 290000, '💳', 'https://kr.mcmworldwide.com', JSON_ARRAY('미니멀'), 'MX2024C118'),
  (4, '미니 Aren 비세토스 카드 케이스', '가죽 소품', '카키', 290000, '🪪', 'https://kr.mcmworldwide.com', JSON_ARRAY('미니멀'), 'MX2024D204'),
  (5, 'Aren 브라스 플레이트 월렛', '지갑', '블랙', 380000, '👛', 'https://kr.mcmworldwide.com', JSON_ARRAY('클래식'), 'MX2024E317'),
  (6, '비세토스 백팩', '가방', '브라운', 1150000, '🎒', 'https://kr.mcmworldwide.com', JSON_ARRAY('캐주얼','스트릿'), 'MX2024F440'),
  (7, '비세토스 라우드 토트백', '가방', '베이지', 690000, '👜', 'https://kr.mcmworldwide.com', JSON_ARRAY('포멀','클래식'), 'MX2024G551'),
  (8, '밀라 미니 크로스바디', '가방', '핑크', 620000, '👛', 'https://kr.mcmworldwide.com', JSON_ARRAY('러블리','캐주얼'), 'MX2024H663'),
  (9, '비세토스 체인 월렛', '지갑', '골드', 450000, '💰', 'https://kr.mcmworldwide.com', JSON_ARRAY('러블리','포멀'), 'MX2024I774'),
  (10, 'Aren 집업 파우치', '가죽 소품', '그레이', 320000, '🧳', 'https://kr.mcmworldwide.com', JSON_ARRAY('미니멀','스트릿'), 'MX2024J885');

INSERT INTO store (id, name, address, lat, lng, open_time, close_time, repair_available) VALUES
  (1, 'MCM 강남 본점', '서울 강남구 압구정로', 37.5270000, 127.0280000, '10:30', '20:00', TRUE),
  (2, 'MCM 갤러리아 명품관', '서울 강남구 압구정로', 37.5275000, 127.0410000, '10:30', '20:30', TRUE),
  (3, 'MCM 서초 서비스센터', '서울 서초구 서초대로', 37.4950000, 127.0140000, '10:00', '19:00', TRUE),
  (4, 'MCM 잠실 롯데점', '서울 송파구 올림픽로', 37.5130000, 127.1030000, '10:30', '20:00', FALSE);
