-- 상품 4건을 MCM 실제 상품으로 교체하는 증분이다(F28 최은서 조사 반영, 2026-08-12).
--
-- **이름과 공식 링크와 이미지만 바꾸고 가격·색상·스타일 태그·카테고리는 건드리지 않는다.**
-- 색상과 태그는 추천 매칭 키라 바꾸면 시연이 통째로 달라지고, 조사된 가격은 미국 정가라
-- 국내 판매가가 아니다(조사자 주의사항 1번: `K열 원화 값은 환산 참고값이며 국내 판매가가 아님`).
--
-- 4건만 바꾼 이유: 조사에서 색상이 해석된 것이 코냑·블랙뿐이라 나머지 여섯(카키·브라운·베이지·핑크·골드·그레이)은
-- 색이 맞는 실물이 없다. 색이 다른 사진을 붙이면 추천 근거 문구("블랙을 좋아하신다고 하셔서")와 화면이 어긋난다.
--
-- 적용: docker compose exec -T mysql mysql --default-character-set=utf8mb4 -u root -p"$DB_ROOT_PASSWORD" mcmory_java < 06-real-products.sql

SET NAMES utf8mb4;

-- MMRGATA07BK001 (Black, Small, US $750). 브라우저 실측으로 페이지 실재 확인함 — curl은 봇 차단으로 403이다
UPDATE product SET name = 'Aren Nova Crossbody in Monogram ECONYL',
  official_url = 'https://us.mcmworldwide.com/en_US/women/bags/shoulder-crossbody-bags/aren-nova-crossbody-in-monogram-econyl®/MMRGATA07BK001.html',
  image_url = 'https://images.mcmworldwide.com/i/mcmworldwide/MMRGATA07BK001_01/MMRGATA07BK001?$large$&fmt=auto&qlt=default'
  WHERE id = 1;

-- MWSGSTA02CO001 (Cognac, Small, US $750)
UPDATE product SET name = 'Aren East West Shoulder Bag in Visetos',
  official_url = 'https://us.mcmworldwide.com/en_US/women/bags/shoulder-crossbody-bags/aren-east-west-shoulder-bag-in-visetos/MWSGSTA02CO001.html',
  image_url = 'https://images.mcmworldwide.com/i/mcmworldwide/MWSGSTA02CO001_01/MWSGSTA02CO001?$large$&fmt=auto&qlt=default'
  WHERE id = 2;

-- MXADATA08CO001 (Cognac, Mini, US $210)
UPDATE product SET name = 'Aren Card Case in Visetos',
  official_url = 'https://us.mcmworldwide.com/en_US/women/wallets-small-leather-goods/all-wallets/aren-card-case-in-visetos/MXADATA08CO001.html',
  image_url = 'https://images.mcmworldwide.com/i/mcmworldwide/MXADATA08CO001_01/MXADATA08CO001?$large$&fmt=auto&qlt=default'
  WHERE id = 3;

-- MYSGSTA01BK001 (Black, Small, US $380). 시드의 `Aren 브라스 플레이트 월렛`이 이 상품을 옮긴 것이었다
UPDATE product SET name = 'Aren Brass Plate Wallet in Visetos',
  official_url = 'https://us.mcmworldwide.com/en_US/women/wallets-small-leather-goods/all-wallets/aren-brass-plate-wallet-in-visetos/MYSGSTA01BK001.html',
  image_url = 'https://images.mcmworldwide.com/i/mcmworldwide/MYSGSTA01BK001_01/MYSGSTA01BK001?$large$&fmt=auto&qlt=default'
  WHERE id = 5;
