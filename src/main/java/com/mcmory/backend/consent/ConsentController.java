package com.mcmory.backend.consent;

import java.util.List;
import java.util.Map;

import com.mcmory.backend.auth.CurrentMember;

import com.mcmory.backend.global.apiPayload.CustomResponse;
import com.mcmory.backend.global.apiPayload.code.ValidationErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 동의 현황과 재동의임. 이 두 경로가 버전 관리의 쓸모 그 자체다 — 버전을 올려두기만 하고 다시 묻는 경로가 없으면 버전 컬럼은 장식이 된다.
 */
@RestController
@RequestMapping("/api/v1/consents")
public class ConsentController {

	private final ConsentService consents;

	private final CurrentMember currentMember;

	public ConsentController(ConsentService consents, CurrentMember currentMember) {
		this.consents = consents;
		this.currentMember = currentMember;
	}

	public record ConsentRequest(String type, Boolean agreed) {
	}

	/** needsAction이 하나라도 참이면 화면이 동의 시트를 다시 띄운다. */
	@GetMapping
	public CustomResponse<Map<String, Object>> status() {
		List<ConsentService.ConsentStatus> items = this.consents.statusOf(this.currentMember.requireId());
		boolean needsAction = items.stream().anyMatch(ConsentService.ConsentStatus::needsAction);

		return CustomResponse.ok(Map.of("items", items, "needsAction", needsAction));
	}

	/** 재동의와 선택 항목 변경임. 기존 행을 고치지 않고 새 행을 남긴다(append-only). */
	@PostMapping
	public CustomResponse<Map<String, Object>> record(@RequestBody ConsentRequest request) {
		// agreed 누락을 false로 접으면 필드를 빠뜨린 요청이 조용히 "철회"로 처리됨 — 동의와 철회는 감사 대상이라 명시를 요구함
		if (request.agreed() == null) {
			throw new CustomException(ValidationErrorCode.MISSING_PARAMETER);
		}
		this.consents.record(this.currentMember.requireId(), request.type(), request.agreed());
		return CustomResponse.ok(Map.of("ok", true));
	}

}
