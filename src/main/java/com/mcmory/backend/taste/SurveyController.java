package com.mcmory.backend.taste;

import java.util.List;
import java.util.Map;

import com.mcmory.backend.global.apiPayload.CustomResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * `Start-01`과 `Start-02`임. 초대 열람(`/api/v1/g/{token}`)과 같은 비회원 경로임 — 수신자는 회원이 아닐 수 있음.
 */
@RestController
public class SurveyController {

	private final SurveyService survey;

	public SurveyController(SurveyService survey) {
		this.survey = survey;
	}

	/** 세 축 모두 다중 선택임(v1.1 실측). */
	public record SubmitRequest(boolean privacyAgreed, List<String> colors, List<String> styles, List<String> bags) {
	}

	@GetMapping("/api/v1/s/{token}")
	public CustomResponse<SurveyService.SurveyView> read(@PathVariable String token) {
		return CustomResponse.ok(this.survey.read(token));
	}

	@PostMapping("/api/v1/s/{token}")
	public CustomResponse<Map<String, Object>> submit(@PathVariable String token, @RequestBody SubmitRequest request) {
		this.survey.submit(token, request.privacyAgreed(), request.colors(), request.styles(), request.bags());
		return CustomResponse.ok(Map.of("ok", true));
	}

}
