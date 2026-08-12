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
  (1, 'Tracy 비세토스 크로스바디', '가방', '블랙', 890000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('미니멀','클래식'), 'MX2024A031'),
  (2, '비세토스 숄더백', '가방', '코냑', 750000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('클래식','미니멀'), 'MX2024B072'),
  (3, '비세토스 오리지널 카드 반지갑', '지갑', '코냑', 290000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('미니멀'), 'MX2024C118'),
  (4, '미니 Aren 비세토스 카드 케이스', '가죽 소품', '카키', 290000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('미니멀'), 'MX2024D204'),
  (5, 'Aren 브라스 플레이트 월렛', '지갑', '블랙', 380000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('클래식'), 'MX2024E317'),
  (6, '비세토스 백팩', '가방', '브라운', 1150000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('캐주얼','스트릿'), 'MX2024F440'),
  (7, '비세토스 라우드 토트백', '가방', '베이지', 690000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('포멀','클래식'), 'MX2024G551'),
  (8, '밀라 미니 크로스바디', '가방', '핑크', 620000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('러블리','캐주얼'), 'MX2024H663'),
  (9, '비세토스 체인 월렛', '지갑', '골드', 450000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('러블리','포멀'), 'MX2024I774'),
  (10, 'Aren 집업 파우치', '가죽 소품', '그레이', 320000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('미니멀','스트릿'), 'MX2024J885');

INSERT INTO store (id, name, address, lat, lng, open_time, close_time, repair_available) VALUES
  (1, 'MCM 강남 본점', '서울 강남구 압구정로', 37.5270000, 127.0280000, '10:30', '20:00', TRUE),
  (2, 'MCM 갤러리아 명품관', '서울 강남구 압구정로', 37.5275000, 127.0410000, '10:30', '20:30', TRUE),
  (3, 'MCM 서초 서비스센터', '서울 서초구 서초대로', 37.4950000, 127.0140000, '10:00', '19:00', TRUE),
  (4, 'MCM 잠실 롯데점', '서울 송파구 올림픽로', 37.5130000, 127.1030000, '10:30', '20:00', FALSE);

-- 코디용 의류다(FR-023 AI 스타일링). **선물 대상이 아니다** — 선물 적격은 가방과 지갑과 가죽 소품뿐이고
-- RecommendService와 GiftService가 카테고리로 그것을 강제한다. 여기 카테고리를 그 셋 중 하나로 바꾸면
-- "친구에게 슬랙스를 선물하세요"가 나온다.
--
-- 코디용 의류다. **선물 대상이 아니다** — 카테고리 셋(WOMAN OUTER·TOP·BOTTOM)이 그 판정의 근거라 바꾸면 안 된다.
-- id도 바꾸지 않는다: 프론트 이미지 규약이 /products/{id}.webp이고, 스타일링 정렬이 동점이면 id 순이다.
-- demo_serial은 넣지 않는다 — 의류는 시리얼 등록 대상이 아니고, 넣으면 그 화면에 옷이 뜬다.
INSERT INTO product (id, name, category, color, price, image_url, official_url, style_tags) VALUES
  (11, 'Washed Denim Jacket', 'WOMAN OUTER', '핑크', 1260000, 'https://images.mcmworldwide.com/i/mcmworldwide/MFJGAMM02PZ042_01/MFJGAMM02PZ042?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/washed-denim-jacket/MFJGAMM02PZ042.html', JSON_ARRAY('캐주얼','스트릿')),
  (12, 'Washed Denim Jeans', 'WOMAN BOTTOM', '핑크', 760000, 'https://images.mcmworldwide.com/i/mcmworldwide/MFPGAMM06PZ040_01/MFPGAMM06PZ040?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/washed-denim-jeans/MFPGAMM06PZ040.html', JSON_ARRAY('캐주얼','러블리')),
  (13, 'Monogram Print T-Shirt', 'WOMAN TOP', '코냑', 440000, 'https://images.mcmworldwide.com/i/mcmworldwide/MHTGSMM07CO00M_01/MHTGSMM07CO00M?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/monogram-print-t-shirt/MHTGSMM07CO00M.html', JSON_ARRAY('클래식','미니멀')),
  (14, 'Silk Pajama Shirt', 'WOMAN TOP', '핑크', 1040000, 'https://images.mcmworldwide.com/i/mcmworldwide/MFHGAMM01PZ038_01/MFHGAMM01PZ038?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/silk-pajama-shirt/MFHGAMM01PZ038.html', JSON_ARRAY('러블리','클래식')),
  (15, 'Tweed Gilet', 'WOMAN OUTER', '블랙', 1830000, 'https://images.mcmworldwide.com/i/mcmworldwide/MFVGAMM01BK00M_01/MFVGAMM01BK00M?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/tweed-gilet/MFVGAMM01BK00M.html', JSON_ARRAY('포멀','클래식')),
  (16, 'Monogram Midi Skirt in ECONYL', 'WOMAN BOTTOM', '핑크', 830000, 'https://images.mcmworldwide.com/i/mcmworldwide/MFKFAMM01PZ00L_01/MFKFAMM01PZ00L?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/monogram-midi-skirt-in-econyl/MFKFAMM01PZ00L.html', JSON_ARRAY('러블리','미니멀')),
  (17, 'Disco Patch Ponte Hoodie', 'WOMAN TOP', '그레이', 690000, 'https://images.mcmworldwide.com/i/mcmworldwide/MHAGAMM01ET00L_01/MHAGAMM01ET00L?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/disco-patch-ponte-hoodie/MHAGAMM01ET00L.html', JSON_ARRAY('스트릿','캐주얼')),
  (18, 'Pants in Wool Twill and Monogram Print Leather', 'WOMAN BOTTOM', '블랙', 970000, 'https://images.mcmworldwide.com/i/mcmworldwide/MFPGSMM01BK038_01/MFPGSMM01BK038?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/pants-in-wool-twill-and-monogram-print-leather/MFPGSMM01BK038.html', JSON_ARRAY('미니멀','포멀'));
