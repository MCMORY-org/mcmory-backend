package com.mcmory.backend.gift;

import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

import com.mcmory.backend.global.apiPayload.CustomResponse;

import com.mcmory.backend.config.OpenApiConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "선물과 초대",
		description = "선물 발송(FR-013)과 비회원 초대 열람·동의(FR-015), 편지 사진 업로드(FR-012), 내 제품 등록(FR-020), 옵션 변경 문의(FR-018)를 다룸. **`/api/v1/invitations/{token}` 계열은 로그인 없이 진입하는 유일한 경로임**(ADR-006, ADR-013 결정 4) — 단 `POST /api/v1/invitations/{token}/owned`만 예외로 로그인이 필요함. 실패는 문구가 아니라 `code`로 분기할 것.")
@RestController
public class GiftController {

	private final GiftService gifts;

	private final CurrentMember currentMember;

	private final LetterImageService letterImages;

	public GiftController(GiftService gifts, CurrentMember currentMember, LetterImageService letterImages) {
		this.gifts = gifts;
		this.currentMember = currentMember;
		this.letterImages = letterImages;
	}

	/**
	 * `friendId`가 수신자 식별의 정본이고 `friendName`은 폴백임. 둘 다 받는 이유는 친구 등록 화면을 거치지 않는 발송 경로가 실재하기
	 * 때문임 — 그 경로는 id를 가질 수 없음. **id가 오면 이름은 무시함**(FIX-W001 T1).
	 */
	public record SendRequest(
			@Schema(description = "보낼 시드 상품 id임", example = "1",
					requiredMode = Schema.RequiredMode.REQUIRED) Long productId,
			@Schema(description = "이 발송의 근거가 된 추천 id임. 선택인 이유는 추천을 거치지 않고 상품을 직접 골라 보내는 경로가 있기 때문임. **내 추천만 매달 수 있고** 남의 추천 id는 `REC400_3`임",
					example = "3", nullable = true,
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long recommendationId,
			@Schema(description = "편지 본문임. 앞뒤 공백을 제거한 뒤 1자 이상 200자 이하임(화면 표기 `78/200자`와 같은 한도임). 벗어나면 `GIFT400_2`임",
					example = "생일 축하해. 늘 고마워", minLength = 1, maxLength = 200,
					requiredMode = Schema.RequiredMode.REQUIRED) String letterBody,
			@Schema(description = "**수신자 식별의 정본임.** 오면 `friendName`을 무시하고 이 친구에게 보냄. 선택인 이유는 친구 등록 화면을 거치지 않는 발송 경로가 실재해 id를 가질 수 없기 때문임. **화면이 친구를 고를 수 있으면 반드시 이 값을 보낼 것** — 이름으로 찾으면 한 글자만 틀려도 번호 없는 친구 행이 새로 생겨 도착 알림(FR-017)이 조용히 끊김. 없는 id와 남의 친구는 함께 `FRIEND404_1`임(존재 여부를 갈라주지 않음)",
					example = "101", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long friendId,
			@Schema(description = "`friendId`가 없을 때만 쓰는 폴백 이름임. 앞뒤 공백 제거 후 20자 이하이고 21자 이상은 `FRIEND400_1`임. **비우면 `친구`로 저장됨**. `friendId`가 오면 이 값은 무시됨",
					example = "지민", maxLength = 20, nullable = true,
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) String friendName,
			@Schema(description = "편지에 붙일 사진 URL 목록임. 선택이고, **#28 `POST /api/v1/gift/letter-images`가 발급한 `/letter-images/`로 시작하는 URL만** 받음 — 외부 URL은 `GIFT400_7`임. 최대 5개이고 초과는 `GIFT400_6`임",
					example = "[\"/letter-images/8f0c1d2e111122223333444455556666.jpg\"]", nullable = true,
					requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<String> letterImageUrls,
			@Schema(description = "편지지 **배경색 토큰**임(글자색이 아니고 자유 hex도 아님 — 대비는 화면이 배경 명도로 판단함). 선택이고 고르지 않으면 저장하지 않음. 소문자로 보내도 대문자로 저장하며 넷 밖은 `GIFT400_8`임. **취향 색상 6종·상품 색상과는 다른 축이라 섞지 말 것**",
					example = "GOLD", allowableValues = {
							"GOLD", "BLACK", "BEIGE", "PINK" },
					nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String letterColor){
	}

	public record ChangeRequest(@Schema(
			description = "옵션 변경 문의 사유임(ADR-007 결정 2의 고정 4종). `SIZE` 크기, `COLOR` 색상, `ALREADY_OWNED` 이미 보유, `ETC` 기타임. 넷 밖은 `GIFT400_4`임. 발송자에게는 알림 `type`이 `CHANGE_REQ_SIZE` 형태로 전달됨",
			example = "SIZE", allowableValues = {
					"SIZE", "COLOR", "ALREADY_OWNED", "ETC" },
			requiredMode = Schema.RequiredMode.REQUIRED) String reason){
	}

	/** FR-013 선물 발송임. */
	@Operation(summary = "선물 발송",
			description = """
					명세서 5.4 #11임. **로그인 필수임.**

					`letterBody`는 앞뒤 공백 제거 후 1자에서 200자임. `letterImageUrls`는 #28이 발급한 `/letter-images/`로 시작하는 URL만 받고 최대 5개임 — 외부 URL은 `GIFT400_7`임. `letterColor`는 `GOLD`·`BLACK`·`BEIGE`·`PINK` 넷뿐인 배경색 토큰이고 자유 hex가 아님(그 밖은 `GIFT400_8`).

					함정: `friendId`가 수신자 식별의 정본이고 오면 `friendName`을 무시함. 이름으로 찾으면 한 글자만 틀려도 번호 없는 친구 행이 새로 생겨 도착 알림(FR-017)이 조용히 끊김 — 화면이 친구를 고를 수 있으면 반드시 `friendId`를 보낼 것. 남의 친구와 없는 친구는 같은 `FRIEND404_1`임(존재 여부를 알려주지 않음).

					응답 `result`: `{ "token", "nickname" }`.""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "발송 성공. 초대 토큰과 익명 닉네임을 반환함",
					content = @Content(examples = @ExampleObject(name = "성공", value = """
							{
							  "isSuccess": true,
							  "code": "200",
							  "message": "OK",
							  "result": { "token": "k3n8pq2wz7ta5vh0jr4bmx91", "nickname": "다정한 호저" }
							}"""))),
			@ApiResponse(responseCode = "400",
					description = "`GIFT400_1` 상품을 찾을 수 없습니다 / `GIFT400_2` 편지는 1자에서 200자까지 쓸 수 있습니다 / `GIFT400_6` 사진은 5장까지 넣을 수 있습니다 / `GIFT400_7` 사진은 10MB 이하 이미지 파일만 올릴 수 있습니다(외부 URL 포함) / `GIFT400_8` 편지지 색상 형식을 확인해주세요 / `REC400_3` 추천 정보를 찾을 수 없습니다(남의 추천 ID) / `FRIEND400_1` 이름은 1자에서 20자까지 입력해주세요",
					content = @Content(examples = { @ExampleObject(name = "GIFT400_2", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT400_2",
							  "message": "편지는 1자에서 200자까지 쓸 수 있습니다",
							  "result": null
							}"""), @ExampleObject(name = "GIFT400_1", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT400_1",
							  "message": "상품을 찾을 수 없습니다",
							  "result": null
							}"""), @ExampleObject(name = "GIFT400_6", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT400_6",
							  "message": "사진은 5장까지 넣을 수 있습니다",
							  "result": null
							}"""), @ExampleObject(name = "GIFT400_8", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT400_8",
							  "message": "편지지 색상 형식을 확인해주세요",
							  "result": null
							}""") })),
			@ApiResponse(responseCode = "401", description = "`AUTH401_1` 로그인이 필요합니다",
					content = @Content(examples = @ExampleObject(name = "AUTH401_1", value = """
							{
							  "isSuccess": false,
							  "code": "AUTH401_1",
							  "message": "로그인이 필요합니다",
							  "result": null
							}"""))),
			@ApiResponse(responseCode = "404", description = "`FRIEND404_1` 친구 정보를 찾을 수 없습니다(없거나 남의 친구, 삭제된 친구)",
					content = @Content(examples = @ExampleObject(name = "FRIEND404_1", value = """
							{
							  "isSuccess": false,
							  "code": "FRIEND404_1",
							  "message": "친구 정보를 찾을 수 없습니다",
							  "result": null
							}"""))) })
	@SecurityRequirement(name = OpenApiConfig.ACCESS_COOKIE)
	@PostMapping("/api/v1/gift")
	public CustomResponse<GiftService.Sent> send(@RequestBody SendRequest request) {
		return CustomResponse.ok(this.gifts.send(this.currentMember.requireId(), request.productId(),
				request.recommendationId(), request.letterBody(), request.friendId(), request.friendName(),
				request.letterImageUrls(), request.letterColor()));
	}

	/**
	 * FR-012 편지 사진 업로드임. 발송과 분리한 이유는 작성 화면에서 사진을 먼저 붙이고 나중에 보내기 때문임 — 발송 한 번에 multipart로
	 * 묶으면 미리보기 화면이 파일을 다시 들고 있어야 함.
	 */
	@Operation(summary = "편지 사진 업로드",
			description = """
					명세서 5.4 #28임. **로그인 필수임.** `multipart/form-data`이고 파트 이름은 `files`(복수)임.

					파일당 10MB 이하, 최대 5장, `image/jpeg`·`image/png`·`image/webp`·`image/gif`만 받음. 함정: **선언한 Content-Type과 실제 파일 형식이 같아야 함** — JPEG 바이트를 `image/png`로 보내면 거부됨(확장자를 선언 형식으로 정하기 때문임).

					받은 URL은 #11 `POST /api/v1/gift`의 `letterImageUrls`에 그대로 실을 것. 파일은 서버 로컬 디스크에 있어 **재배포하면 사라짐**.

					화면 미구현 상태임(명세서 5.0).""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "업로드 성공. 발급된 URL 목록을 반환함",
					content = @Content(examples = @ExampleObject(name = "성공", value = """
							{
							  "isSuccess": true,
							  "code": "200",
							  "message": "OK",
							  "result": { "urls": ["/letter-images/{uuid}.jpg"] }
							}"""))),
			@ApiResponse(responseCode = "400",
					description = "`GIFT400_5` 사진을 선택해주세요(파일 없음) / `GIFT400_6` 사진은 5장까지 넣을 수 있습니다 / `GIFT400_7` 사진은 10MB 이하 이미지 파일만 올릴 수 있습니다(형식이나 크기 위반)",
					content = @Content(examples = { @ExampleObject(name = "GIFT400_7", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT400_7",
							  "message": "사진은 10MB 이하 이미지 파일만 올릴 수 있습니다",
							  "result": null
							}"""), @ExampleObject(name = "GIFT400_5", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT400_5",
							  "message": "사진을 선택해주세요",
							  "result": null
							}"""), @ExampleObject(name = "GIFT400_6", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT400_6",
							  "message": "사진은 5장까지 넣을 수 있습니다",
							  "result": null
							}""") })),
			@ApiResponse(responseCode = "401", description = "`AUTH401_1` 로그인이 필요합니다",
					content = @Content(examples = @ExampleObject(name = "AUTH401_1", value = """
							{
							  "isSuccess": false,
							  "code": "AUTH401_1",
							  "message": "로그인이 필요합니다",
							  "result": null
							}"""))) })
	@SecurityRequirement(name = OpenApiConfig.ACCESS_COOKIE)
	@PostMapping("/api/v1/gift/letter-images")
	public CustomResponse<Map<String, Object>> uploadLetterImages(
			@RequestPart(value = "files", required = false) List<MultipartFile> files) {
		this.currentMember.requireId();
		return CustomResponse.ok(Map.of("urls", this.letterImages.store(files)));
	}

	/**
	 * 비회원 초대 열람임(ADR-006, ADR-013 결정 4). 로그인 없이 진입하는 유일한 API 경로라 인증을 요구하지 않음.
	 */
	@Operation(summary = "비회원 초대 열람",
			description = """
					명세서 5.4 #12임. **인증 불필요임**(ADR-006, ADR-013 결정 4).

					동의 전에는 `{ "needConsent": true, "nickname": "다정한 호저" }`만 줌 — **`letterBody` 키 자체가 없음**(FR-015). 동의 후에는 `needConsent: false`와 함께 `letterBody`, `product`, `openedAt`이 붙고, 사진을 넣어 보냈으면 `letterImageUrls`가, 색상을 골랐으면 `letterColor`가 붙음.

					함정: **값이 없으면 그 키도 없음**(NON_NULL 계약) — 키 존재를 전제로 파싱하지 말 것. 본문을 보려면 #13 `POST /api/v1/invitations/{token}` 동의를 먼저 거쳐야 함.""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "동의 전이면 `needConsent: true`와 닉네임만, 동의 후면 본문과 상품이 함께 옴",
					content = @Content(examples = { @ExampleObject(name = "동의 전", value = """
							{
							  "isSuccess": true,
							  "code": "200",
							  "message": "OK",
							  "result": { "needConsent": true, "nickname": "다정한 호저" }
							}""") })),
			@ApiResponse(responseCode = "404", description = "`GIFT404_1` 초대 정보를 찾을 수 없습니다",
					content = @Content(examples = @ExampleObject(name = "GIFT404_1", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT404_1",
							  "message": "초대 정보를 찾을 수 없습니다",
							  "result": null
							}"""))) })
	@GetMapping("/api/v1/invitations/{token}")
	public CustomResponse<GiftService.InviteView> read(@PathVariable String token) {
		return CustomResponse.ok(this.gifts.read(token));
	}

	/** 동의와 열람임. 동의 전에는 본문을 주지 않으므로 이 호출이 열람의 전제임(FR-015). */
	@Operation(summary = "동의와 열람",
			description = """
					명세서 5.4 #13임. **인증 불필요임.** 요청 본문 없음.

					동의와 최초 열람을 기록함. 함정: **재방문은 시각을 갈아치우지 않음** — 최초 열람 시각만 남음. 동의 전에는 #12가 `letterBody`를 주지 않으므로 이 호출이 열람의 전제임(FR-015).

					응답 `result`는 `{ "ok": true }` 고정임(명세서 5.4는 #13의 `result` 형태를 따로 적지 않아 실제 구현 값을 예시로 실음).""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "동의와 최초 열람 기록 성공",
					content = @Content(examples = @ExampleObject(name = "성공", value = """
							{
							  "isSuccess": true,
							  "code": "200",
							  "message": "OK",
							  "result": { "ok": true }
							}"""))),
			@ApiResponse(responseCode = "404", description = "`GIFT404_1` 초대 정보를 찾을 수 없습니다",
					content = @Content(examples = @ExampleObject(name = "GIFT404_1", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT404_1",
							  "message": "초대 정보를 찾을 수 없습니다",
							  "result": null
							}"""))) })
	@PostMapping("/api/v1/invitations/{token}")
	public CustomResponse<Map<String, Object>> consent(@PathVariable String token) {
		this.gifts.consentAndOpen(token);
		return CustomResponse.ok(Map.of("ok", true));
	}

	/**
	 * FR-020 "내 제품으로 등록"임. **경로는 `/api/v1/invitations/**` 아래지만 이것만 로그인이 필요함** — 보유 제품에
	 * 주인이 있어야 하기 때문임. 시큐리티가 경로 인가를 하지 않으므로(SecurityConfig) `requireId()`가 유일한 문지기임. 지우면 곧
	 * 인증 구멍임.
	 */
	@Operation(summary = "받은 선물을 내 제품으로 등록",
			description = """
					명세서 5.4 #29임. 요청 본문 없음.

					함정: **경로는 `/api/v1/invitations/**` 아래지만 이것만 로그인이 필요함** — 보유 제품에 주인이 있어야 하기 때문임. 동의와 열람(#13)을 마친 뒤에만 되고, 다시 눌러도 새 행을 만들지 않고 같은 결과를 줌(멱등).

					`GIFT404_1`은 없는 토큰과 **다른 회원에게 지정된 선물**을 함께 덮음 — 존재 여부를 알려주지 않으려고 같은 코드를 씀.

					응답 `result`: `{ "id", "source": "GIFT", "product": { "productId", "name", "price", "imageUrl": "https://images.mcmworldwide.com/i/mcmworldwide/MMRGATA07BK001_01/MMRGATA07BK001?$large$&fmt=auto&qlt=default" } }`.""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "등록 성공. 재호출해도 같은 결과를 반환함",
					content = @Content(examples = @ExampleObject(name = "성공",
							value = """
									{
									  "isSuccess": true,
									  "code": "200",
									  "message": "OK",
									  "result": { "id": 1, "source": "GIFT", "product": { "productId": 1, "name": "...", "price": 0, "imageUrl": "https://images.mcmworldwide.com/i/mcmworldwide/MMRGATA07BK001_01/MMRGATA07BK001?$large$&fmt=auto&qlt=default" } }
									}"""))),
			@ApiResponse(responseCode = "400",
					description = "`GIFT400_3` 선물을 먼저 열어주세요(아직 열지 않음) / `GIFT400_1` 상품을 찾을 수 없습니다(연결된 상품 없음)",
					content = @Content(examples = { @ExampleObject(name = "GIFT400_3", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT400_3",
							  "message": "선물을 먼저 열어주세요",
							  "result": null
							}"""), @ExampleObject(name = "GIFT400_1", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT400_1",
							  "message": "상품을 찾을 수 없습니다",
							  "result": null
							}""") })),
			@ApiResponse(responseCode = "401", description = "`AUTH401_1` 로그인이 필요합니다",
					content = @Content(examples = @ExampleObject(name = "AUTH401_1", value = """
							{
							  "isSuccess": false,
							  "code": "AUTH401_1",
							  "message": "로그인이 필요합니다",
							  "result": null
							}"""))),
			@ApiResponse(responseCode = "404", description = "`GIFT404_1` 초대 정보를 찾을 수 없습니다(없는 토큰 또는 다른 회원에게 지정된 선물)",
					content = @Content(examples = @ExampleObject(name = "GIFT404_1", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT404_1",
							  "message": "초대 정보를 찾을 수 없습니다",
							  "result": null
							}"""))) })
	@SecurityRequirement(name = OpenApiConfig.ACCESS_COOKIE)
	@PostMapping("/api/v1/invitations/{token}/owned")
	public CustomResponse<GiftService.OwnedFromGift> registerAsOwned(@PathVariable String token) {
		return CustomResponse.ok(this.gifts.registerAsOwned(this.currentMember.requireId(), token));
	}

	/** FR-018 옵션 변경 문의임. 동의·열람과 같은 비회원 경로라 로그인을 요구하지 않음. */
	@Operation(summary = "옵션 변경 문의",
			description = """
					명세서 5.4 #30임. **인증 불필요임**(동의·열람과 같은 비회원 경로임).

					요청 `{ "reason": "SIZE" | "COLOR" | "ALREADY_OWNED" | "ETC" }`이고 그 넷 밖은 `GIFT400_4`임. 함정: **선물당 1회임** — 두 번째 호출은 `GIFT409_1`임. 열람 전에는 `GIFT400_3`임.

					발송자는 알림으로 사유를 봄 — `type`이 `CHANGE_REQ_SIZE` 형태임(명세서 5.8). 사유 전용 컬럼을 만들지 않고 타입에 실은 결정이라 이 표기가 계약임.

					응답 `result`: `{ "reason", "officialUrl" }`. 화면 미구현 상태임(명세서 5.0).""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "문의 접수 성공",
					content = @Content(examples = @ExampleObject(name = "성공", value = """
							{
							  "isSuccess": true,
							  "code": "200",
							  "message": "OK",
							  "result": { "reason": "SIZE", "officialUrl": "..." }
							}"""))),
			@ApiResponse(responseCode = "400",
					description = "`GIFT400_4` 문의 사유를 선택해주세요(사유가 4종 밖) / `GIFT400_3` 선물을 먼저 열어주세요",
					content = @Content(examples = { @ExampleObject(name = "GIFT400_4", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT400_4",
							  "message": "문의 사유를 선택해주세요",
							  "result": null
							}"""), @ExampleObject(name = "GIFT400_3", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT400_3",
							  "message": "선물을 먼저 열어주세요",
							  "result": null
							}""") })),
			@ApiResponse(responseCode = "404", description = "`GIFT404_1` 초대 정보를 찾을 수 없습니다",
					content = @Content(examples = @ExampleObject(name = "GIFT404_1", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT404_1",
							  "message": "초대 정보를 찾을 수 없습니다",
							  "result": null
							}"""))),
			@ApiResponse(responseCode = "409", description = "`GIFT409_1` 이미 옵션 변경을 문의했습니다",
					content = @Content(examples = @ExampleObject(name = "GIFT409_1", value = """
							{
							  "isSuccess": false,
							  "code": "GIFT409_1",
							  "message": "이미 옵션 변경을 문의했습니다",
							  "result": null
							}"""))) })
	@PostMapping("/api/v1/invitations/{token}/change-request")
	public CustomResponse<GiftService.ChangeRequested> requestChange(@PathVariable String token,
			@RequestBody ChangeRequest request) {
		return CustomResponse.ok(this.gifts.requestChange(token, request.reason()));
	}

}
