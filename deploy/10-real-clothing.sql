-- 코디용 의류 8건(id 11-18)을 MCM 실제 상품으로 교체하는 증분이다.
--
-- 왜 필요한가: id 11-18은 시드 주석이 스스로 `임시 데이터`라 적어 둔 지어낸 상품이었다.
-- FR-023 AI 스타일링이 제안하는 옷이 전부 이것들이라, 발표에서 화면에 뜨는 옷이 가짜였다.
--
-- **추가가 아니라 교체(UPDATE)여야 한다.** 스타일링 정렬은 태그 교집합이 같으면 id 순이라
-- (StylingService.pick()의 안정 정렬), 새 의류를 id 114번대로 추가만 하면 기존 가짜가 계속 이겨
-- 화면에 그대로 뜬다. 그래서 id를 유지하고 내용만 덮는다. 프론트 이미지 규약(/products/{id}.webp)도 id에 걸려 있다.
--
-- 함정 1: **이미지 주소는 SKU로 조립한 것이고, 46건을 전부 내려받아 검증했다.**
--         MCM CDN은 **없는 SKU에도 200과 자리표시자 이미지(15,688바이트)를 준다** — 200은 증거가 아니다.
--         자리표시자의 md5(91fd056187f8012480e360557040b731)와 대조해 걸렀고, 8건은 눈으로도 확인했다.
-- 함정 2: 태그 8건 중 7건은 기존 값을 그대로 뒀다. 실물이 그 태그를 실제로 지지하는 것만 골랐기 때문이다.
--         id 14만 바뀐다(`포멀`+`클래식` → `러블리`+`클래식`) — 실크 파자마 셔츠는 포멀이라 부를 수 없다.
-- 함정 3: 색은 **자료가 확정한 값만** 썼다. 조사 자료에서 색이 `기타`인 상품은 고르지 않았다.
--         의류 색은 추천 점수에 쓰이지 않지만(선물 부적격) 화면에 표시되므로 지어내지 않는다.
-- 함정 4: price는 미국 정가(USD)를 환율 1429로 환산해 만원 단위로 반올림한 값이며 국내 판매가가 아니다.
-- 함정 5: 카테고리(`WOMAN OUTER`·`WOMAN TOP`·`WOMAN BOTTOM`)와 id를 바꾸지 않는다.
--         `Product.isGiftEligible`이 카테고리 화이트리스트라 이 셋은 선물 추천과 발송에서 계속 제외된다.
--         카테고리를 바꾸면 "친구에게 슬랙스를 선물하세요"가 나간다.
-- 함정 6: **과거 기록의 표시값도 함께 바뀐다.** 스타일링은 저장하지 않지만, 이 상품을 보유 제품으로 등록한
--         기록이 있으면 그 화면의 이름과 가격이 새 값으로 보인다. 되돌리려면 아래 이전 값을 쓴다.
--
-- 되돌리기 — DELETE가 아니라 UPDATE라 FK RESTRICT와 무관하다. 이전 값은 이렇다
--   11 워싱 데님 재킷 / WOMAN OUTER / 블루 / 1250000 / 캐주얼·스트릿
--   12 루렉스 데님 플레어 팬츠 / WOMAN BOTTOM / 블루 / 830000 / 캐주얼·러블리
--   13 로고 자카드 니트 / WOMAN TOP / 베이지 / 690000 / 클래식·미니멀
--   14 비세토스 실크 블라우스 / WOMAN TOP / 화이트 / 580000 / 포멀·클래식
--   15 테일러드 울 코트 / WOMAN OUTER / 블랙 / 1890000 / 포멀·클래식
--   16 플리츠 미디 스커트 / WOMAN BOTTOM / 핑크 / 620000 / 러블리·미니멀
--   17 오버핏 후디 / WOMAN TOP / 그레이 / 450000 / 스트릿·캐주얼
--   18 스트레이트 슬랙스 / WOMAN BOTTOM / 블랙 / 520000 / 미니멀·포멀
--   이전 official_url은 8건 모두 https://kr.mcmworldwide.com 이고 image_url은 NULL이었다.
--
-- 적용 전 확인. 8이 아니면 실행하지 말 것
--   SELECT COUNT(*) FROM product WHERE id BETWEEN 11 AND 18;
--
-- 적용(비밀번호는 컨테이너 env로만 넘긴다. 셸 인자에 값을 박지 말 것):
--   **레포 루트에서** 실행한다
--   docker compose exec -T mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" mcmory_java' < deploy/10-real-clothing.sql

SET NAMES utf8mb4;

UPDATE product SET
  name = 'Washed Denim Jacket',
  color = '핑크',
  price = 1260000,
  image_url = 'https://images.mcmworldwide.com/i/mcmworldwide/MFJGAMM02PZ042_01/MFJGAMM02PZ042?$large$&fmt=auto&qlt=default',
  official_url = 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/washed-denim-jacket/MFJGAMM02PZ042.html',
  style_tags = JSON_ARRAY('캐주얼','스트릿')
  WHERE id = 11;

UPDATE product SET
  name = 'Washed Denim Jeans',
  color = '핑크',
  price = 760000,
  image_url = 'https://images.mcmworldwide.com/i/mcmworldwide/MFPGAMM06PZ040_01/MFPGAMM06PZ040?$large$&fmt=auto&qlt=default',
  official_url = 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/washed-denim-jeans/MFPGAMM06PZ040.html',
  style_tags = JSON_ARRAY('캐주얼','러블리')
  WHERE id = 12;

UPDATE product SET
  name = 'Monogram Print T-Shirt',
  color = '코냑',
  price = 440000,
  image_url = 'https://images.mcmworldwide.com/i/mcmworldwide/MHTGSMM07CO00M_01/MHTGSMM07CO00M?$large$&fmt=auto&qlt=default',
  official_url = 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/monogram-print-t-shirt/MHTGSMM07CO00M.html',
  style_tags = JSON_ARRAY('클래식','미니멀')
  WHERE id = 13;

UPDATE product SET
  name = 'Silk Pajama Shirt',
  color = '핑크',
  price = 1040000,
  image_url = 'https://images.mcmworldwide.com/i/mcmworldwide/MFHGAMM01PZ038_01/MFHGAMM01PZ038?$large$&fmt=auto&qlt=default',
  official_url = 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/silk-pajama-shirt/MFHGAMM01PZ038.html',
  style_tags = JSON_ARRAY('러블리','클래식')
  WHERE id = 14;

UPDATE product SET
  name = 'Tweed Gilet',
  color = '블랙',
  price = 1830000,
  image_url = 'https://images.mcmworldwide.com/i/mcmworldwide/MFVGAMM01BK00M_01/MFVGAMM01BK00M?$large$&fmt=auto&qlt=default',
  official_url = 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/tweed-gilet/MFVGAMM01BK00M.html',
  style_tags = JSON_ARRAY('포멀','클래식')
  WHERE id = 15;

UPDATE product SET
  name = 'Monogram Midi Skirt in ECONYL',
  color = '핑크',
  price = 830000,
  image_url = 'https://images.mcmworldwide.com/i/mcmworldwide/MFKFAMM01PZ00L_01/MFKFAMM01PZ00L?$large$&fmt=auto&qlt=default',
  official_url = 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/monogram-midi-skirt-in-econyl/MFKFAMM01PZ00L.html',
  style_tags = JSON_ARRAY('러블리','미니멀')
  WHERE id = 16;

UPDATE product SET
  name = 'Disco Patch Ponte Hoodie',
  color = '그레이',
  price = 690000,
  image_url = 'https://images.mcmworldwide.com/i/mcmworldwide/MHAGAMM01ET00L_01/MHAGAMM01ET00L?$large$&fmt=auto&qlt=default',
  official_url = 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/disco-patch-ponte-hoodie/MHAGAMM01ET00L.html',
  style_tags = JSON_ARRAY('스트릿','캐주얼')
  WHERE id = 17;

UPDATE product SET
  name = 'Pants in Wool Twill and Monogram Print Leather',
  color = '블랙',
  price = 970000,
  image_url = 'https://images.mcmworldwide.com/i/mcmworldwide/MFPGSMM01BK038_01/MFPGSMM01BK038?$large$&fmt=auto&qlt=default',
  official_url = 'https://us.mcmworldwide.com/en_US/women/ready-to-wear/all-ready-to-wear/pants-in-wool-twill-and-monogram-print-leather/MFPGSMM01BK038.html',
  style_tags = JSON_ARRAY('미니멀','포멀')
  WHERE id = 18;
