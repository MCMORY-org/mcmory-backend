-- MCM 색상 변형 45건을 카탈로그에 추가하는 증분이다(id 69-113).
--
-- 왜 필요한가: HOME-02 취향 화면이 색상 여섯(코냑·블랙·베이지·핑크·골드·그레이)을 보여주는데,
-- 07까지의 카탈로그 68건은 코냑과 블랙뿐이고 나머지 넷은 옛 더미가 각 1건씩이었다.
-- 그래서 핑크나 베이지를 고른 수신자에게 "핑크 계열을 좋아하신다고 하셔서 골랐어요" 같은
-- PERSONAL 근거가 사실상 나오지 않았다. 이 45건이 그 둘을 실물로 채운다.
--
-- 출처: MCM 미국 공식몰 컬러 필터(us.mcmworldwide.com/en_US/bags/all-bags/{color}) 전수, 수집일 2026-08-12.
--       핑크 35 + 베이지 10. 원본은 팀원이 만든 products_by_color.xlsx의 '색상별_공식목록' 시트다.
--
-- 함정 1: **골드 2건은 일부러 뺐다.** 둘 다 USD 1290(약 184만원)이라 기본 예산 대역(50-150만원) 밖이고
--         같은 라인(Himmel Sequin)의 색상 변형이라, 넣어도 기본 시연에서 골드 취향은 여전히 폴백이다.
--         그레이는 MCM 가방에 공식 컬러 분류 자체가 없다(0건). 두 색은 화면 6색을 바꿀지의 기획 결정 사항이지
--         백엔드가 데이터로 해결할 수 있는 문제가 아니다.
-- 함정 2: style_tags는 캐주얼·미니멀·스트릿·클래식·러블리·포멀 여섯 중에서만 쓴다. 근거 문구에 값이 그대로 박힌다.
--         **이 태그는 MCM이 붙인 것이 아니라 우리가 판단해 붙인 것이다.** 부여 경로는 셋이고 전부 기록했다.
--         (1) 이름이 같은 기존 상품의 태그 복사 17건 — 선정 근거 2-5의 "색이 스타일을 바꿀 이유가 없다"가 근거다
--         (2) 같은 계열 기존 상품을 보고 손으로 판단 8건 (Ottomar Weekender, Milla Tote, Leni Shopper,
--             Aren Crossbody, Toni Top-Zip Shopper — 각각 id 32·44·40·51·40을 따랐다)
--         (3) 나머지 20건은 선정 근거 2-2 규칙표를 기계 적용
--         **사이즈를 태그 근거로 쓰지 않았다** — 쓰면 같은 이름이 사이즈로 갈려 근거 문구가 흔들린다.
-- 함정 3: price는 미국 정가(USD)를 환율 1429로 환산해 만원 단위로 반올림한 값이며 **국내 판매가가 아니다.**
-- 함정 4: SKU 45건이 06·07의 54건과 겹치지 않는 것을 확인했다(컬러코드 QA·PZ 계열이라 BK·CO와 갈린다).
-- 함정 5: **로컬·테스트 시드(src/test/resources/db/02-seed.sql)는 건드리지 않는다.** 07이 만든 방식 그대로다.
--         테스트는 Testcontainers가 그 시드만 적재하므로 이 증분을 아예 보지 않는다. 그래서 회귀가 없지만,
--         **뒤집어 말하면 이 45건은 자동 테스트가 한 줄도 실행하지 않는다.** 적용 후 스모크로 직접 확인할 것.
-- 함정 6: **적용하면 기존 기본 추천 결과가 또 바뀐다.** 시연 시나리오가 옛 상품 id를 못박고 있으면 다시 고를 것.
--         과거에 저장된 추천 스냅샷과 그것으로 보낸 선물은 재계산하지 않으므로 그대로 유지된다.
--
-- 적용 순서
--   신규 인스턴스: 01-schema → 03-deploy-seed → 06-real-products → 07-product-catalog → 09-color-variants
--   기존 운영 DB(현재 상품 68건): 이 파일만 적용한다.
--
-- 적용 전 확인 둘. 하나라도 0이 아니면 실행하지 말 것
--   SELECT COUNT(*) FROM product WHERE id BETWEEN 69 AND 113;
--   SELECT COUNT(*) FROM product WHERE demo_serial LIKE 'MX2024Q%' OR demo_serial LIKE 'MX2024R%'
--     OR demo_serial LIKE 'MX2024S%' OR demo_serial LIKE 'MX2024T%' OR demo_serial LIKE 'MX2024U%';
--
-- 롤백 — 조건부다. recommendation_result·gift·owned_product가 product를 FK RESTRICT로 참조한다
--   SELECT (SELECT COUNT(*) FROM recommendation_result WHERE product_id BETWEEN 69 AND 113)
--        + (SELECT COUNT(*) FROM gift WHERE product_id BETWEEN 69 AND 113)
--        + (SELECT COUNT(*) FROM owned_product WHERE product_id BETWEEN 69 AND 113) AS refs;
--   refs가 0일 때만: DELETE FROM product WHERE id BETWEEN 69 AND 113;
--   0이 아니면 지우지 말 것 — 사용자 데이터가 끊긴다. 그때는 롤포워드로 해결한다.
--
-- 적용(비밀번호는 컨테이너 env로만 넘긴다. 셸 인자에 값을 박지 말 것):
--   **레포 루트에서** 실행한다. 07은 같은 줄을 상대 경로로 적어 두어 루트에서 그대로 붙여 넣으면 파일을 못 찾는다
--   docker compose exec -T mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" mcmory_java' < deploy/09-color-variants.sql

SET NAMES utf8mb4;

INSERT INTO product (id, name, category, color, price, image_url, official_url, style_tags, demo_serial) VALUES
  (69, 'Dessau Drawstring Bag in Maxi Monogram Leather', '가방', '핑크', 1970000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWDGSDU01QA001_01/MWDGSDU01QA001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/dessau-drawstring-bag-in-maxi-monogram-leather/MWDGSDU01QA001.html', JSON_ARRAY('미니멀','캐주얼'), 'MX2024Q051'),
  (70, 'Aren Drawstring Backpack in Maxi Monogram Leather', '가방', '핑크', 1900000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWKGSTA01QA001_01/MWKGSTA01QA001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-drawstring-backpack-in-maxi-monogram-leather/MWKGSTA01QA001.html', JSON_ARRAY('미니멀','캐주얼'), 'MX2024Q052'),
  (71, 'Ella Boston Bag in Maxi Monogram Leather', '가방', '핑크', 1830000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWBGSEA03QA001_01/MWBGSEA03QA001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/ella-boston-bag-in-maxi-monogram-leather/MWBGSEA03QA001.html', JSON_ARRAY('미니멀','클래식'), 'MX2024Q053'),
  (72, 'Ottomar Weekender Bag in Visetos', '가방', '핑크', 1830000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMVGATT01PZ001_01/MMVGATT01PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/ottomar-weekender-bag-in-visetos/MMVGATT01PZ001.html', JSON_ARRAY('클래식','포멀'), 'MX2024Q054'),
  (73, 'Stark Side Studs Backpack in Visetos', '가방', '핑크', 1760000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMKEAVE15PZ001_01/MMKEAVE15PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/stark-side-studs-backpack-in-visetos/MMKEAVE15PZ001.html', JSON_ARRAY('스트릿','캐주얼'), 'MX2024Q055'),
  (74, 'Milla Tote in Grained Leather', '가방', '핑크', 1690000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWTGSMA02QA001_01/MWTGSMA02QA001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/milla-tote-in-grained-leather/MWTGSMA02QA001.html', JSON_ARRAY('미니멀','포멀'), 'MX2024Q056'),
  (75, 'Aren Drawstring Backpack in Visetos', '가방', '핑크', 1690000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWKGATA03PZ001_01/MWKGATA03PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-drawstring-backpack-in-visetos/MWKGATA03PZ001.html', JSON_ARRAY('캐주얼'), 'MX2024Q057'),
  (76, 'Aren Crescent Hobo Bag in Maxi Monogram Leather', '가방', '핑크', 1610000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWHGSTA03QA001_01/MWHGSTA03QA001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-crescent-hobo-bag-in-maxi-monogram-leather/MWHGSTA03QA001.html', JSON_ARRAY('미니멀','캐주얼'), 'MX2024Q058'),
  (77, 'Stark Side Studs Backpack in Visetos', '가방', '핑크', 1610000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMKEAVE16PZ001_01/MMKEAVE16PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/stark-side-studs-backpack-in-visetos/MMKEAVE16PZ001.html', JSON_ARRAY('스트릿','캐주얼'), 'MX2024Q059'),
  (78, 'Aren East West Shoulder Bag in Maxi Monogram Leather', '가방', '핑크', 1540000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWSGSTA01QA001_01/MWSGSTA01QA001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-east-west-shoulder-bag-in-maxi-monogram-leather/MWSGSTA01QA001.html', JSON_ARRAY('미니멀'), 'MX2024Q060'),
  (79, 'Aren Drawstring Backpack in Visetos', '가방', '핑크', 1540000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWKFATA02PZ001_01/MWKFATA02PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-drawstring-backpack-in-visetos/MWKFATA02PZ001.html', JSON_ARRAY('캐주얼'), 'MX2024R061'),
  (80, 'Leni Shopper in Visetos', '가방', '핑크', 1500000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPGSTA01PZ001_01/MWPGSTA01PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/leni-shopper-in-visetos/MWPGSTA01PZ001.html', JSON_ARRAY('클래식','포멀'), 'MX2024R062'),
  (81, 'Aren Duo Hobo in Visetos', '가방', '핑크', 1400000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWHGSTA04PZ001_01/MWHGSTA04PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-duo-hobo-in-visetos/MWHGSTA04PZ001.html', JSON_ARRAY('캐주얼','미니멀'), 'MX2024R063'),
  (82, 'Aren Hobo in Visetos', '가방', '핑크', 1400000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWHESTA01PZ001_01/MWHESTA01PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-hobo-in-visetos/MWHESTA01PZ001.html', JSON_ARRAY('캐주얼'), 'MX2024R064'),
  (83, 'Aren Crescent Hobo Bag in Visetos', '가방', '핑크', 1400000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWHFATA02PZ001_01/MWHFATA02PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-crescent-hobo-bag-in-visetos/MWHFATA02PZ001.html', JSON_ARRAY('캐주얼'), 'MX2024R065'),
  (84, 'Stark Bebe Boo Side Studs Backpack in Visetos', '가방', '핑크', 1400000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMKEAVE17PZ001_01/MMKEAVE17PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/stark-bebe-boo-side-studs-backpack-in-visetos/MMKEAVE17PZ001.html', JSON_ARRAY('스트릿','캐주얼'), 'MX2024R066'),
  (85, 'Ella Boston Bag in Visetos', '가방', '핑크', 1400000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWBFAEA03PZ001_01/MWBFAEA03PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/ella-boston-bag-in-visetos/MWBFAEA03PZ001.html', JSON_ARRAY('클래식','포멀'), 'MX2024R067'),
  (86, 'Tracy Crossbody in Visetos', '가방', '핑크', 1400000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWRFAXT01PZ001_01/MWRFAXT01PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/tracy-crossbody-in-visetos/MWRFAXT01PZ001.html', JSON_ARRAY('클래식'), 'MX2024R068'),
  (87, 'Toni Top-Zip Shopper in Maxi Monogram Leather', '가방', '핑크', 1360000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPGSMT04QA001_01/MWPGSMT04QA001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/toni-top-zip-shopper-in-maxi-monogram-leather/MWPGSMT04QA001.html', JSON_ARRAY('미니멀'), 'MX2024R069'),
  (88, 'Aren School Bag Tote in Visetos', '가방', '핑크', 1330000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWTGATA03PZ001_01/MWTGATA03PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-school-bag-tote-in-visetos/MWTGATA03PZ001.html', JSON_ARRAY('캐주얼','스트릿'), 'MX2024R070'),
  (89, 'Aren Vanity Case in Maxi Monogram Leather', '가방', '핑크', 1260000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWRGSTA01QA001_01/MWRGSTA01QA001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-vanity-case-in-maxi-monogram-leather/MWRGSTA01QA001.html', JSON_ARRAY('미니멀','러블리'), 'MX2024S071'),
  (90, 'Ella Boston Bag in Visetos', '가방', '핑크', 1260000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWBESEA01PZ001_01/MWBESEA01PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/ella-boston-bag-in-visetos/MWBESEA01PZ001.html', JSON_ARRAY('클래식','포멀'), 'MX2024S072'),
  (91, 'New Liz Shopper in Visetos', '가방', '핑크', 1190000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPGSLR02PZ001_01/MWPGSLR02PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/new-liz-shopper-in-visetos/MWPGSLR02PZ001.html', JSON_ARRAY('클래식','포멀'), 'MX2024S073'),
  (92, 'Aren School Bag Tote in Visetos', '가방', '핑크', 1190000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWTGSTA03PZ001_01/MWTGSTA03PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-school-bag-tote-in-visetos/MWTGSTA03PZ001.html', JSON_ARRAY('캐주얼','스트릿'), 'MX2024S074'),
  (93, 'Liz Shopper in Visetos', '가방', '핑크', 1190000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPFSLR03PZ001_01/MWPFSLR03PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/liz-shopper-in-visetos/MWPFSLR03PZ001.html', JSON_ARRAY('클래식'), 'MX2024S075'),
  (94, 'Liz Shopper in Visetos', '가방', '핑크', 1110000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPFSLR07PZ001_01/MWPFSLR07PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/liz-shopper-in-visetos/MWPFSLR07PZ001.html', JSON_ARRAY('클래식'), 'MX2024S076'),
  (95, 'New Liz Shopper in Visetos', '가방', '핑크', 1110000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPGSLR03PZ001_01/MWPGSLR03PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/new-liz-shopper-in-visetos/MWPGSLR03PZ001.html', JSON_ARRAY('클래식','포멀'), 'MX2024S077'),
  (96, 'Aren East West Shoulder Bag in Visetos', '가방', '핑크', 1070000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWSGATA01PZ001_01/MWSGATA01PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-east-west-shoulder-bag-in-visetos/MWSGATA01PZ001.html', JSON_ARRAY('미니멀','클래식'), 'MX2024S078'),
  (97, 'Toni Top-Zip Shopper in Visetos', '가방', '핑크', 1040000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPFSMT06PZ001_01/MWPFSMT06PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/toni-top-zip-shopper-in-visetos/MWPFSMT06PZ001.html', JSON_ARRAY('클래식','포멀'), 'MX2024S079'),
  (98, 'Aren Vanity Case in Visetos Leather Mix', '가방', '핑크', 970000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWRGSTA02PZ001_01/MWRGSTA02PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-vanity-case-in-visetos-leather-mix/MWRGSTA02PZ001.html', JSON_ARRAY('러블리','캐주얼'), 'MX2024S080'),
  (99, 'Aren Chain Crossbody in Maxi Monogram Leather', '가방', '핑크', 970000, 'https://images.mcmworldwide.com/i/mcmworldwide/MYZGSTA07QA001_01/MYZGSTA07QA001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-chain-crossbody-in-maxi-monogram-leather/MYZGSTA07QA001.html', JSON_ARRAY('미니멀','러블리'), 'MX2024T081'),
  (100, 'Aren Crossbody in Visetos', '가방', '핑크', 900000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMRGSTA07PZ001_01/MMRGSTA07PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-crossbody-in-visetos/MMRGSTA07PZ001.html', JSON_ARRAY('미니멀','캐주얼'), 'MX2024T082'),
  (101, 'Toni Top-Zip Shopper in Visetos', '가방', '핑크', 900000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPFSMT03PZ001_01/MWPFSMT03PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/toni-top-zip-shopper-in-visetos/MWPFSMT03PZ001.html', JSON_ARRAY('클래식','포멀'), 'MX2024T083'),
  (102, 'Wristlet Standing Pouch With Chain Strap in Visetos', '가죽 소품', '핑크', 900000, 'https://images.mcmworldwide.com/i/mcmworldwide/MYZFATA03PZ001_01/MYZFATA03PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/wristlet-standing-pouch-with-chain-strap-in-visetos/MYZFATA03PZ001.html', JSON_ARRAY('러블리'), 'MX2024T084'),
  (103, 'Crossbody Pouch in Visetos Original', '가죽 소품', '핑크', 790000, 'https://images.mcmworldwide.com/i/mcmworldwide/MYZGSTA09PZ001_01/MYZGSTA09PZ001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/crossbody-pouch-in-visetos-original/MYZGSTA09PZ001.html', JSON_ARRAY('클래식'), 'MX2024T085'),
  (104, 'Ottomar Weekender Bag in Visetos', '가방', '베이지', 2260000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMVGATT07K8001_01/MMVGATT07K8001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/ottomar-weekender-bag-in-visetos/MMVGATT07K8001.html', JSON_ARRAY('클래식','포멀'), 'MX2024T086'),
  (105, 'Stark Side Studs Backpack in Visetos', '가방', '베이지', 2040000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMKEAVE12IG001_01/MMKEAVE12IG001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/stark-side-studs-backpack-in-visetos/MMKEAVE12IG001.html', JSON_ARRAY('스트릿','캐주얼'), 'MX2024T087'),
  (106, 'Stark Side Studs Backpack in Visetos', '가방', '베이지', 1760000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMKEAVE15IG001_01/MMKEAVE15IG001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/stark-side-studs-backpack-in-visetos/MMKEAVE15IG001.html', JSON_ARRAY('스트릿','캐주얼'), 'MX2024T088'),
  (107, 'Aren Drawstring Backpack in Visetos', '가방', '베이지', 1540000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWKGATA02I8001_01/MWKGATA02I8001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-drawstring-backpack-in-visetos/MWKGATA02I8001.html', JSON_ARRAY('캐주얼'), 'MX2024T089'),
  (108, 'New Liz Shopper in Visetos', '가방', '베이지', 1190000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPGSLR02I8001_01/MWPGSLR02I8001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/new-liz-shopper-in-visetos/MWPGSLR02I8001.html', JSON_ARRAY('클래식','포멀'), 'MX2024T090'),
  (109, 'Aren Sling in Visetos', '가방', '베이지', 1190000, 'https://images.mcmworldwide.com/i/mcmworldwide/MMLGATA05K8001_01/MMLGATA05K8001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-sling-in-visetos/MMLGATA05K8001.html', JSON_ARRAY('캐주얼','미니멀'), 'MX2024U091'),
  (110, 'Liz Shopper in Visetos', '가방', '베이지', 1110000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPCSVI01I8001_01/MWPCSVI01I8001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/liz-shopper-in-visetos/MWPCSVI01I8001.html', JSON_ARRAY('클래식'), 'MX2024U092'),
  (111, 'New Liz Shopper in Visetos', '가방', '베이지', 1110000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPGSLR03I8001_01/MWPGSLR03I8001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/new-liz-shopper-in-visetos/MWPGSLR03I8001.html', JSON_ARRAY('클래식','포멀'), 'MX2024U093'),
  (112, 'Aren East West Shoulder Bag in Visetos', '가방', '베이지', 1070000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWSGATA01I8001_01/MWSGATA01I8001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/aren-east-west-shoulder-bag-in-visetos/MWSGATA01I8001.html', JSON_ARRAY('미니멀','클래식'), 'MX2024U094'),
  (113, 'Toni Top-Zip Shopper in Visetos', '가방', '베이지', 1040000, 'https://images.mcmworldwide.com/i/mcmworldwide/MWPAATN04I8001_01/MWPAATN04I8001?$large$&fmt=auto&qlt=default', 'https://us.mcmworldwide.com/en_US/bags/all-bags/toni-top-zip-shopper-in-visetos/MWPAATN04I8001.html', JSON_ARRAY('클래식','포멀'), 'MX2024U095');
