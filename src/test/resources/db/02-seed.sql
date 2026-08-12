-- 컨테이너의 character_set_client 기본값이 latin1이라 이 선언이 없으면 한글이 깨져 저장된다.
SET NAMES utf8mb4;

-- 시연용 시드. 원래 prototype/db/init/02-seed.sql의 사본으로 시작했으나 **더 이상 사본이 아니다** —
-- 2026-08-11 FEAT-W002에서 코디용 의류 8건이 여기에만 들어갔다. 이 파일이 백엔드 정본이고,
-- 함께 갱신할 대상은 prototype이 아니라 deploy/03-deploy-seed.sql이다.
--
-- 차이 하나 더: password_hash가 평문 '1234'가 아니라 BCrypt 해시다(ADR-013 결정 1의 이식 결과).
-- Next.js 프로토타입은 평문을 비교하므로 두 값이 같을 수 없다. 로그인 비밀번호는 양쪽 모두 1234다.
INSERT INTO member (id, name, phone, password_hash, birth_date, gender, sms_opt_in) VALUES
  (1, '테스터', '01012345678', '$2a$10$ru2r9OwcGSksJ8nDGOQtSerfR7Mcs11C6zXnyhqmv3NEzv3F2pCnm', '2000-01-01', 'NONE', FALSE),
  -- 수신함 검증용 두 번째 회원. friend 101의 전화번호와 일치시켜 회원 수신자 경로를 만든다
  (2, '수신자', '01099998888', '$2a$10$ru2r9OwcGSksJ8nDGOQtSerfR7Mcs11C6zXnyhqmv3NEzv3F2pCnm', '2000-05-05', 'NONE', FALSE);

INSERT INTO friend (id, owner_member_id, name, phone) VALUES
  (101, 1, '친구 2', '01099998888');

-- 수신자가 직접 답한 취향(FIX-W002). source가 INVITE_ANSWER인 유일한 행이다.
-- 설문 왕복(Start-01, Start-02)은 범위 밖이라 답변이 이미 도착한 상태를 시드로 만든다.
-- 읽는 키는 color와 style 둘뿐이다. 기대 순위는 RecommendTasteIntegrationTest가 고정한다.
INSERT INTO taste_profile (friend_id, source, answers) VALUES
  (101, 'INVITE_ANSWER', JSON_OBJECT('color', '블랙', 'style', '미니멀'));

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
-- image_url은 **전부 NULL이다.** 값이 있으면 화면이 그것을 이미지 주소로 쓰고, 없으면 자체 규약
-- (/products/{id}.webp)으로 폴백한다. 2026-08-12까지 여기에 이모지가 들어 있었는데, 그러면 화면이
-- <img src="👜">를 만들어 깨진 이미지가 되고 폴백도 안 탄다. **이모지는 계약상 최악의 값이다.**
-- 배포 쪽은 deploy/03-deploy-seed.sql이 이미 NULL이고 08-image-url-cleanup.sql이 남은 값을 지웠다.
-- **prototype/db/init/02-seed.sql은 따라 고치지 않는다** — Next.js 데모는 이 컬럼을 이미지 주소가 아니라
-- 이모지 문자 그대로 렌더하므로 NULL로 바꾸면 화면 상품이 전부 🎁로 퇴화한다(의도적으로 갈라진 값이다).
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

-- 코디용 의류다(FR-023 AI 스타일링). **선물 대상이 아니다** — 선물 적격은 가방과 지갑과 가죽 소품뿐이고
-- RecommendService와 GiftService가 카테고리로 그것을 강제한다. 여기 카테고리를 그 셋 중 하나로 바꾸면
-- "친구에게 슬랙스를 선물하세요"가 나온다.
--
-- demo_serial을 넣지 않는다: 의류는 FR-028 시리얼 등록 대상이 아니고, 넣으면 그 화면에 옷이 뜬다.
-- image_url도 넣지 않는다: 값이 없으면 화면이 /products/{id}.webp 규약으로 폴백한다.
-- **죽은 컬럼이 아니다** — 2026-08-12부터 API 응답에 실리고, 값이 있으면 화면이 그것을 그대로 쓴다.
--
-- **임시 데이터다.** F28(최은서)의 실제 MCM 의류 자료가 오면 이름과 가격과 색상을 교체하되
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

INSERT INTO store (id, name, address, lat, lng, open_time, close_time, repair_available) VALUES
  (1, 'MCM 강남 본점', '서울 강남구 압구정로', 37.5270000, 127.0280000, '10:30', '20:00', TRUE),
  (2, 'MCM 갤러리아 명품관', '서울 강남구 압구정로', 37.5275000, 127.0410000, '10:30', '20:30', TRUE),
  (3, 'MCM 서초 서비스센터', '서울 서초구 서초대로', 37.4950000, 127.0140000, '10:00', '19:00', TRUE),
  (4, 'MCM 잠실 롯데점', '서울 송파구 올림픽로', 37.5130000, 127.1030000, '10:30', '20:00', FALSE);
