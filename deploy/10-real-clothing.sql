-- 코디용 의류 8건(id 11-18)을 지어낸 값에서 MCM 실제 상품으로 교체한다. 운영 DB에 1회 적용한다.
--
-- **추가가 아니라 교체여야 한다.** 스타일링 정렬은 태그 교집합이 같으면 id 순이라(StylingService.pick),
-- 새 id로 넣으면 옛 행이 계속 이겨 화면이 그대로다. id와 카테고리는 유지한다 — 카테고리 셋은 선물 부적격 판정의 근거다.
--
-- **image_url은 SKU로 조립한 주소이고, 넣기 전에 반드시 실물인지 확인한다.**
-- MCM CDN은 없는 SKU에도 200과 자리표시자 이미지를 준다. 상태 코드는 증거가 아니므로
-- 자리표시자 md5(91fd056187f8012480e360557040b731)와 대조한다. 이 8건은 그렇게 걸렀다.
--
-- price는 미국 정가(USD)를 환율 1429로 환산한 값이며 국내 판매가가 아니다.
-- 신규 인스턴스는 이 파일이 필요 없다 — deploy/03-deploy-seed.sql이 같은 값을 이미 담고 있다.
--
-- 적용 전 확인. 8이 아니면 실행하지 말 것
--   SELECT COUNT(*) FROM product WHERE id BETWEEN 11 AND 18;
--
-- 되돌리기 — UPDATE라 FK와 무관하다. 이전 값은 이렇다(official_url은 8건 모두 https://kr.mcmworldwide.com, image_url은 NULL)
--   11 워싱 데님 재킷 / 블루 / 1250000 / 캐주얼·스트릿      12 루렉스 데님 플레어 팬츠 / 블루 / 830000 / 캐주얼·러블리
--   13 로고 자카드 니트 / 베이지 / 690000 / 클래식·미니멀    14 비세토스 실크 블라우스 / 화이트 / 580000 / 포멀·클래식
--   15 테일러드 울 코트 / 블랙 / 1890000 / 포멀·클래식       16 플리츠 미디 스커트 / 핑크 / 620000 / 러블리·미니멀
--   17 오버핏 후디 / 그레이 / 450000 / 스트릿·캐주얼         18 스트레이트 슬랙스 / 블랙 / 520000 / 미니멀·포멀
--
-- 적용(레포 루트에서. 비밀번호는 컨테이너 env로만 넘긴다)
--   docker compose exec -T mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" mcmory_java' < deploy/10-real-clothing.sql

SET NAMES utf8mb4;

START TRANSACTION;

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

COMMIT;
