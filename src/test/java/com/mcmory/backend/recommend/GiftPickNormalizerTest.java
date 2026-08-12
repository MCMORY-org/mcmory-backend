package com.mcmory.backend.recommend;

import java.util.Set;

import com.mcmory.backend.recommend.GiftPickNormalizer.Rejection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모델이 고른 상품 목록의 런타임 게이트임. **이것이 환각을 막는 지점임** — 후보 밖 id가 하나라도 있으면 통째로 버리고 규칙 결과로 폴백함.
 *
 * 순수 함수라 여기서 따로 봄. Bedrock 클라이언트를 모킹하는 통합 테스트로는 이 로직이 한 줄도 안 돌아감(스타일링 정규화와 같은 이유임).
 *
 * 아래 픽스처는 실제로 모델이 내는 나쁜 사례를 최소 수정으로 만든 것임 — 코드블록으로 감싸기, 개수 어기기, 목록 밖 상품 지어내기.
 */
class GiftPickNormalizerTest {

	private static final Set<Long> CANDIDATES = Set.of(1L, 2L, 3L, 4L, 5L);

	@Test
	void 후보_안의_3건과_근거를_그대로_통과시킨다() {
		var result = GiftPickNormalizer.normalize("""
				{"picks":[3,1,5],"reason":"블랙 계열을 좋아하신다고 하셔서 골랐어요"}
				""", CANDIDATES, 3);

		assertThat(result.accepted()).isTrue();
		// 순서가 곧 순위임. 정렬하면 모델이 고른 1순위가 사라짐
		assertThat(result.productIds()).containsExactly(3L, 1L, 5L);
		assertThat(result.reason()).isEqualTo("블랙 계열을 좋아하신다고 하셔서 골랐어요");
	}

	/** 모델이 JSON을 코드블록으로 감싸는 일이 잦음. 그것 때문에 전부 버리면 폴백률만 올라감. */
	@Test
	void 코드블록으로_감싼_JSON도_읽는다() {
		var result = GiftPickNormalizer.normalize("""
				```json
				{"picks":[2,4,1],"reason":"캐주얼 스타일을 즐기시네요"}
				```
				""", CANDIDATES, 3);

		assertThat(result.accepted()).isTrue();
		assertThat(result.productIds()).containsExactly(2L, 4L, 1L);
	}

	@Test
	void 깨진_JSON은_버린다() {
		var result = GiftPickNormalizer.normalize("고심해서 골랐습니다. 1번과 2번을 추천해요", CANDIDATES, 3);

		assertThat(result.accepted()).isFalse();
		assertThat(result.rejection()).isEqualTo(Rejection.BAD_JSON);
	}

	@Test
	void 개수가_어긋나면_버린다() {
		assertThat(GiftPickNormalizer.normalize("""
				{"picks":[1,2],"reason":"블랙 계열을 좋아하시네요"}
				""", CANDIDATES, 3).rejection()).isEqualTo(Rejection.WRONG_SIZE);

		assertThat(GiftPickNormalizer.normalize("""
				{"picks":[1,2,3,4],"reason":"블랙 계열을 좋아하시네요"}
				""", CANDIDATES, 3).rejection()).isEqualTo(Rejection.WRONG_SIZE);
	}

	/** 같은 상품이 둘 나오면 화면에 같은 카드가 둘 뜸. 개수만 세면 못 잡음. */
	@Test
	void 중복_id는_버린다() {
		var result = GiftPickNormalizer.normalize("""
				{"picks":[2,2,5],"reason":"블랙 계열을 좋아하시네요"}
				""", CANDIDATES, 3);

		assertThat(result.rejection()).isEqualTo(Rejection.DUPLICATE);
	}

	/** **이 테스트가 이 클래스의 존재 이유임.** 후보에 없는 상품은 모델이 지어낸 것이고 그대로 나가면 계약 오염임. */
	@Test
	void 후보_밖의_id가_하나라도_있으면_통째로_버린다() {
		var result = GiftPickNormalizer.normalize("""
				{"picks":[1,2,99],"reason":"블랙 계열을 좋아하시네요"}
				""", CANDIDATES, 3);

		assertThat(result.accepted()).isFalse();
		assertThat(result.rejection()).isEqualTo(Rejection.UNKNOWN_ID);
	}

	/** 근거 검사는 스타일링 정규화를 그대로 씀 — 금칙어와 길이와 영문 차단이 이미 실측으로 다듬어져 있음. */
	@Test
	void 금칙어가_든_근거는_버린다() {
		var result = GiftPickNormalizer.normalize("""
				{"picks":[1,2,3],"reason":"정품 인증된 제품이라 안심하세요"}
				""", CANDIDATES, 3);

		assertThat(result.rejection()).isEqualTo(Rejection.BAD_REASON);
	}

	@Test
	void 근거가_상한을_넘으면_버린다() {
		var result = GiftPickNormalizer.normalize("""
				{"picks":[1,2,3],"reason":"고객님이 평소에 즐겨 착용하시는 블랙 계열의 미니멀한 스타일과 아주 잘 어울리는 제품이라고 생각합니다"}
				""", CANDIDATES, 3);

		assertThat(result.rejection()).isEqualTo(Rejection.BAD_REASON);
	}

	/** 후보가 3건이 안 되면 그만큼만 고르게 함. 개수 검사가 3으로 고정이면 그 경우가 전부 폴백함. */
	@Test
	void 기대_개수가_2면_2건을_통과시킨다() {
		var result = GiftPickNormalizer.normalize("""
				{"picks":[4,1],"reason":"캐주얼 스타일을 즐기시네요"}
				""", CANDIDATES, 2);

		assertThat(result.accepted()).isTrue();
		assertThat(result.productIds()).containsExactly(4L, 1L);
	}

}
