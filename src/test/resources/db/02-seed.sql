-- 컨테이너의 character_set_client 기본값이 latin1이라 이 선언이 없으면 한글이 깨져 저장된다.
SET NAMES utf8mb4;

-- 시연용 시드. prototype/db/init/02-seed.sql의 사본이며 password_hash만 다르다.
-- 원본이 바뀌면 이 사본도 같은 커밋에서 갱신할 것.
--
-- 차이 하나: password_hash가 평문 '1234'가 아니라 BCrypt 해시다(ADR-013 결정 1의 이식 결과).
-- Next.js 프로토타입은 평문을 비교하므로 두 값이 같을 수 없다. 로그인 비밀번호는 양쪽 모두 1234다.
INSERT INTO member (id, name, phone, password_hash, birth_date, gender, sms_opt_in) VALUES
  (1, '테스터', '01012345678', '$2a$10$ru2r9OwcGSksJ8nDGOQtSerfR7Mcs11C6zXnyhqmv3NEzv3F2pCnm', '2000-01-01', 'NONE', FALSE),
  -- 수신함 검증용 두 번째 회원. friend 101의 전화번호와 일치시켜 회원 수신자 경로를 만든다
  (2, '수신자', '01099998888', '$2a$10$ru2r9OwcGSksJ8nDGOQtSerfR7Mcs11C6zXnyhqmv3NEzv3F2pCnm', '2000-05-05', 'NONE', FALSE);

INSERT INTO friend (id, owner_member_id, name, phone) VALUES
  (101, 1, '친구 2', '01099998888');

-- demo_serial은 FR-028 시연 매칭 키다. 실서비스 의미 없음.
--
-- style_tags는 v1.1 실측 옷 스타일 6종(캐주얼, 미니멀, 스트릿, 클래식, 러블리, 포멀)만 쓴다.
-- 2026-08-10 이전에는 여기에 관계 태그(데일리, 실용, 합리적)가 섞여 있었다 — RELATION_TAG의
-- 값이지 옷 스타일이 아니라서 두 축이 한 컬럼에서 뒤엉켰고, 러블리와 포멀은 아예 없어
-- 그 관계를 고르면 매칭이 0건이 됐다(FIX-W001 T2).
--
-- color는 취향 색상 6종과 **다른 축**이다. 실제 상품 속성이라 6종에 억지로 맞추지 않고,
-- 대신 6종이 각각 최소 1건씩 나오도록 상품을 늘렸다. 카키와 브라운은 6종 밖이지만 실재하는
-- 상품 색이라 남긴다. 커버리지는 SeedCoverageTest가 지킨다.
--
-- 기본 예산 대역(50에서 150만원)에 관계 태그 넷이 모두 들어가야 한다 — 그 대역 밖으로 밀리면
-- 전체 카탈로그 폴백이 나가 개인화가 우연에 기댄다. 현재 대역 안: 클래식(1,2,7), 캐주얼(6,8),
-- 러블리(8), 포멀(7).
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
