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
-- image_url에는 **주소를 넣거나 NULL로 둔다.** 값이 있으면 화면이 그대로 img 주소로 쓰고,
-- 없으면 /products/{id}.webp 규약으로 폴백한다. 이모지 같은 비주소 값을 넣으면 폴백도 안 타고 깨진다.
-- 아래 id 1-10은 NULL이고, 의류 id 11-18은 MCM CDN 주소다.
-- 이 파일은 deploy/03-deploy-seed.sql과 같은 값을 유지한다.
INSERT INTO product (id, name, category, color, price, image_url, official_url, style_tags, demo_serial) VALUES
  (1, 'Aren Nova Crossbody in Monogram ECONYL', '가방', '블랙', 890000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMRGATA07BK001_01/MMRGATA07BK001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/bags/shoulder-crossbody-bags/aren-nova-crossbody-in-monogram-econyl®/MMRGATA07BK001.html', JSON_ARRAY('미니멀','클래식'), 'MX2024A031'),
  (2, 'Aren East West Shoulder Bag in Visetos', '가방', '코냑', 750000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWSGSTA02CO001_01/MWSGSTA02CO001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/bags/shoulder-crossbody-bags/aren-east-west-shoulder-bag-in-visetos/MWSGSTA02CO001.html', JSON_ARRAY('클래식','미니멀'), 'MX2024B072'),
  (3, 'Aren Card Case in Visetos', '지갑', '코냑', 290000, 'https://images.mcmworldwide.com/i/mcmworldwide/MXADATA08CO001_01/MXADATA08CO001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/wallets-small-leather-goods/all-wallets/aren-card-case-in-visetos/MXADATA08CO001.html', JSON_ARRAY('미니멀'), 'MX2024C118'),
  (4, '미니 Aren 비세토스 카드 케이스', '가죽 소품', '카키', 290000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('미니멀'), 'MX2024D204'),
  (5, 'Aren Brass Plate Wallet in Visetos', '지갑', '블랙', 380000, 'https://images.mcmworldwide.com/i/mcmworldwide/MYSGSTA01BK001_01/MYSGSTA01BK001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/wallets-small-leather-goods/all-wallets/aren-brass-plate-wallet-in-visetos/MYSGSTA01BK001.html', JSON_ARRAY('클래식'), 'MX2024E317'),
  (6, '비세토스 백팩', '가방', '브라운', 1150000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('캐주얼','스트릿'), 'MX2024F440'),
  (7, '비세토스 라우드 토트백', '가방', '베이지', 690000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('포멀','클래식'), 'MX2024G551'),
  (8, '밀라 미니 크로스바디', '가방', '핑크', 620000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('러블리','캐주얼'), 'MX2024H663'),
  (9, '비세토스 체인 월렛', '지갑', '골드', 450000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('러블리','포멀'), 'MX2024I774'),
  (10, 'Aren 집업 파우치', '가죽 소품', '그레이', 320000, NULL, 'https://kr.mcmworldwide.com', JSON_ARRAY('미니멀','스트릿'), 'MX2024J885');

-- 코디용 의류다(FR-023 AI 스타일링). **선물 대상이 아니다** — 선물 적격은 가방과 지갑과 가죽 소품뿐이고
-- RecommendService와 GiftService가 카테고리로 그것을 강제한다. 여기 카테고리를 그 셋 중 하나로 바꾸면
-- "친구에게 슬랙스를 선물하세요"가 나온다.
--
-- **id와 카테고리를 바꾸지 않는다.** 카테고리 셋이 선물 부적격 판정의 근거이고,
-- 스타일링 정렬이 동점이면 id 순이라 새 id로 추가하면 옛 행이 계속 이긴다. 상품이 바뀌면 내용만 덮는다.
-- demo_serial은 넣지 않는다 — 의류는 시리얼 등록 대상이 아니고, 넣으면 그 화면에 옷이 뜬다.
INSERT INTO product (id, name, category, color, price, image_url, official_url, style_tags) VALUES
  (11, 'Washed Denim Jacket', 'WOMAN OUTER', '핑크', 1260000, 'https://images.mcmworldwide.com/i/mcmworldwide/MFJGAMM02PZ042_01/MFJGAMM02PZ042?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/washed-denim-jacket/MFJGAMM02PZ042.html', JSON_ARRAY('캐주얼','스트릿')),
  (12, 'Washed Denim Jeans', 'WOMAN BOTTOM', '핑크', 760000, 'https://images.mcmworldwide.com/i/mcmworldwide/MFPGAMM06PZ040_01/MFPGAMM06PZ040?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/washed-denim-jeans/MFPGAMM06PZ040.html', JSON_ARRAY('캐주얼','러블리')),
  (13, 'Monogram Print T-Shirt', 'WOMAN TOP', '코냑', 440000, 'https://images.mcmworldwide.com/i/mcmworldwide/MHTGSMM07CO00M_01/MHTGSMM07CO00M?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/monogram-print-t-shirt/MHTGSMM07CO00M.html', JSON_ARRAY('클래식')),
  (14, 'Silk Pajama Shirt', 'WOMAN TOP', '핑크', 1040000, 'https://images.mcmworldwide.com/i/mcmworldwide/MFHGAMM01PZ038_01/MFHGAMM01PZ038?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/silk-pajama-shirt/MFHGAMM01PZ038.html', JSON_ARRAY('러블리','클래식')),
  (15, 'Tweed Gilet', 'WOMAN OUTER', '블랙', 1830000, 'https://images.mcmworldwide.com/i/mcmworldwide/MFVGAMM01BK00M_01/MFVGAMM01BK00M?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/tweed-gilet/MFVGAMM01BK00M.html', JSON_ARRAY('포멀','클래식')),
  (16, 'Monogram Midi Skirt in ECONYL', 'WOMAN BOTTOM', '핑크', 830000, 'https://images.mcmworldwide.com/i/mcmworldwide/MFKFAMM01PZ00L_01/MFKFAMM01PZ00L?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/monogram-midi-skirt-in-econyl/MFKFAMM01PZ00L.html', JSON_ARRAY('러블리','미니멀')),
  (17, 'Disco Patch Ponte Hoodie', 'WOMAN TOP', '그레이', 690000, 'https://images.mcmworldwide.com/i/mcmworldwide/MHAGAMM01ET00L_01/MHAGAMM01ET00L?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/disco-patch-ponte-hoodie/MHAGAMM01ET00L.html', JSON_ARRAY('스트릿','캐주얼')),
  (18, 'Pants in Wool Twill and Monogram Print Leather', 'WOMAN BOTTOM', '블랙', 970000, 'https://images.mcmworldwide.com/i/mcmworldwide/MFPGSMM01BK038_01/MFPGSMM01BK038?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/pants-in-wool-twill-and-monogram-print-leather/MFPGSMM01BK038.html', JSON_ARRAY('미니멀','포멀'));

-- 배포 카탈로그에서 22건을 가져온 것이다(가방 10, 지갑 5, 가죽 소품 3, 핑크 2, 베이지 2).
--
-- **왜 테스트 시드에 있는가**: 선물 적격 판정이 사진 없는 상품을 제외하므로(Product.isGiftEligible),
-- 옛 시드 18건만으로는 추천 후보가 0이 되어 추천과 발송 테스트가 전부 죽는다. 배포에는 같은 행이
-- 07-product-catalog.sql과 09-color-variants.sql로 이미 들어가 있고, 여기 값은 그 파일에서 그대로 옮겼다.
--
-- **id를 바꾸지 말 것.** 배포와 같은 id여야 두 환경의 추천 결과를 대조할 수 있다.
INSERT INTO product (id, name, category, color, price, image_url, official_url, style_tags, demo_serial) VALUES
  (19, 'Stark Packable Backpack in Monogram Nylon', '가방', '블랙', 970000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMKGSVE09BK001_01/MMKGSVE09BK001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/backpacks/stark-packable-backpack-in-monogram-nylon/MMKGSVE09BK001.html', JSON_ARRAY('캐주얼','스트릿'), 'MX2024K001'),
  (24, 'Aren Sling Bag in Visetos', '가방', '코냑', 1110000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMLGATA04CO001_01/MMLGATA04CO001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/backpacks/aren-sling-bag-in-visetos/MMLGATA04CO001.html', JSON_ARRAY('미니멀','캐주얼'), 'MX2024K006'),
  (25, 'Aren Sling in Visetos', '가방', '코냑', 1330000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMLFSTA01CO001_01/MMLFSTA01CO001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/backpacks/aren-sling-in-visetos/MMLFSTA01CO001.html', JSON_ARRAY('캐주얼','미니멀'), 'MX2024K007'),
  (29, 'Aren East West Shoulder Bag in Visetos', '가방', '블랙', 1070000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWSGATA01BK001_01/MWSGATA01BK001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/bags/shoulder-crossbody-bags/aren-east-west-shoulder-bag-in-visetos/MWSGATA01BK001.html', JSON_ARRAY('미니멀','클래식'), 'MX2024L011'),
  (30, 'Aren Vanity Case in Visetos Leather Mix', '가방', '블랙', 1190000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWRGATA05BK001_01/MWRGATA05BK001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/bags/shoulder-crossbody-bags/aren-vanity-case-in-visetos-leather-mix/MWRGATA05BK001.html', JSON_ARRAY('러블리','캐주얼'), 'MX2024L012'),
  (34, 'Aren Vanity Case in Visetos Leather Mix', '가방', '코냑', 970000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWRGSTA02CO001_01/MWRGSTA02CO001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/bags/shoulder-crossbody-bags/aren-vanity-case-in-visetos-leather-mix/MWRGSTA02CO001.html', JSON_ARRAY('러블리','캐주얼'), 'MX2024L016'),
  (35, 'Rockstar Vanity Case in Visetos Original', '가방', '코냑', 990000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWRAAVI01CO001_01/MWRAAVI01CO001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/bags/shoulder-crossbody-bags/rockstar-vanity-case-in-visetos-original/MWRAAVI01CO001.html', JSON_ARRAY('스트릿','러블리'), 'MX2024L017'),
  (39, 'Aren Nova Tote in ECONYL', '가방', '블랙', 970000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMTGATA03BK001_01/MMTGATA03BK001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/bags/totes-shoppers/aren-nova-tote-in-econyl®/MMTGATA03BK001.html', JSON_ARRAY('캐주얼','미니멀'), 'MX2024M021'),
  (40, 'New Liz Shopper in Visetos', '가방', '블랙', 1110000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPGSLR05BK001_01/MWPGSLR05BK001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/bags/totes-shoppers/new-liz-shopper-in-visetos/MWPGSLR05BK001.html', JSON_ARRAY('클래식','포멀'), 'MX2024M022'),
  (45, 'Aren Continental Wallet in Visetos', '지갑', '코냑', 790000, 'https://images.mcmworldwide.com/i/mcmworldwide/MYLFATA04CO001_01/MYLFATA04CO001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/wallets-small-leather-goods/all-wallets/aren-continental-wallet-in-visetos/MYLFATA04CO001.html', JSON_ARRAY('포멀','클래식'), 'MX2024M027'),
  (46, 'Toni Top-Zip Shopper in Disco Visetos', '가방', '코냑', 1210000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPGAMT01CO001_01/MWPGAMT01CO001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/bags/totes-shoppers/toni-top-zip-shopper-in-disco-visetos/MWPGAMT01CO001.html', JSON_ARRAY('스트릿','캐주얼'), 'MX2024M028'),
  (49, 'Pina Wallet in Studded Calfskin', '지갑', '블랙', 690000, 'https://images.mcmworldwide.com/i/mcmworldwide/MYSGAAF01BK001_01/MYSGAAF01BK001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/wallets-small-leather-goods/all-wallets/pina-wallet-in-studded-calfskin/MYSGAAF01BK001.html', JSON_ARRAY('스트릿','러블리'), 'MX2024N031'),
  (50, 'Aren Chain Wallet in Embossed Monogram Leather', '지갑', '블랙', 760000, 'https://images.mcmworldwide.com/i/mcmworldwide/MYLGSTA02BK001_01/MYLGSTA02BK001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/wallets-small-leather-goods/all-wallets/aren-chain-wallet-in-embossed-monogram-leather/MYLGSTA02BK001.html', JSON_ARRAY('미니멀','러블리'), 'MX2024N032'),
  (55, 'Zip Wallet in Visetos Original', '지갑', '코냑', 540000, 'https://images.mcmworldwide.com/i/mcmworldwide/MYLAAVI03CO001_01/MYLAAVI03CO001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/wallets-small-leather-goods/all-wallets/zip-wallet-in-visetos-original/MYLAAVI03CO001.html', JSON_ARRAY('클래식'), 'MX2024N037'),
  (56, 'Aren Trifold Wallet in Visetos', '지갑', '코냑', 540000, 'https://images.mcmworldwide.com/i/mcmworldwide/MYSFSTA02CO001_01/MYSFSTA02CO001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/wallets-small-leather-goods/all-wallets/aren-trifold-wallet-in-visetos/MYSFSTA02CO001.html', JSON_ARRAY('미니멀','캐주얼'), 'MX2024N038'),
  (61, 'Aren Card Case in Visetos', '가죽 소품', '블랙', 300000, 'https://images.mcmworldwide.com/i/mcmworldwide/MXADATA08BK001_01/MXADATA08BK001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/wallets-small-leather-goods/all-wallets/aren-card-case-in-visetos/MXADATA08BK001.html', JSON_ARRAY('미니멀'), 'MX2024P043'),
  (65, 'Lanyard ID Card Holder in Visetos', '가죽 소품', '코냑', 330000, 'https://images.mcmworldwide.com/i/mcmworldwide/MXZGATA01CO001_01/MXZGATA01CO001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/wallets-small-leather-goods/all-wallets/lanyard-id-card-holder-in-visetos/MXZGATA01CO001.html', JSON_ARRAY('캐주얼','스트릿'), 'MX2024P047'),
  (66, 'Key Pouch in Visetos Original', '가죽 소품', '코냑', 360000, 'https://images.mcmworldwide.com/i/mcmworldwide/MXKCSVI01CO001_01/MXKCSVI01CO001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/women/wallets-small-leather-goods/all-wallets/key-pouch-in-visetos-original/MXKCSVI01CO001.html', JSON_ARRAY('클래식','러블리'), 'MX2024P048'),
  (80, 'Leni Shopper in Visetos', '가방', '핑크', 1500000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPGSTA01PZ001_01/MWPGSTA01PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/leni-shopper-in-visetos/MWPGSTA01PZ001.html', JSON_ARRAY('클래식','포멀'), 'MX2024R062'),
  (81, 'Aren Duo Hobo in Visetos', '가방', '핑크', 1400000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWHGSTA04PZ001_01/MWHGSTA04PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-duo-hobo-in-visetos/MWHGSTA04PZ001.html', JSON_ARRAY('캐주얼','미니멀'), 'MX2024R063'),
  (108, 'New Liz Shopper in Visetos', '가방', '베이지', 1190000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPGSLR02I8001_01/MWPGSLR02I8001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/new-liz-shopper-in-visetos/MWPGSLR02I8001.html', JSON_ARRAY('클래식','포멀'), 'MX2024T090'),
  (109, 'Aren Sling in Visetos', '가방', '베이지', 1190000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMLGATA05K8001_01/MMLGATA05K8001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-sling-in-visetos/MMLGATA05K8001.html', JSON_ARRAY('캐주얼','미니멀'), 'MX2024U091');

INSERT INTO store (id, name, address, lat, lng, open_time, close_time, repair_available) VALUES
  (1, 'MCM 강남 본점', '서울 강남구 압구정로', 37.5270000, 127.0280000, '10:30', '20:00', TRUE),
  (2, 'MCM 갤러리아 명품관', '서울 강남구 압구정로', 37.5275000, 127.0410000, '10:30', '20:30', TRUE),
  (3, 'MCM 서초 서비스센터', '서울 서초구 서초대로', 37.4950000, 127.0140000, '10:00', '19:00', TRUE),
  (4, 'MCM 잠실 롯데점', '서울 송파구 올림픽로', 37.5130000, 127.1030000, '10:30', '20:00', FALSE);
