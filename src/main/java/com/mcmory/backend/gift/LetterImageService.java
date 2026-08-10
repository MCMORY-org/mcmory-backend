package com.mcmory.backend.gift;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.mcmory.backend.global.apiPayload.code.GiftErrorCode;
import com.mcmory.backend.global.apiPayload.exception.CustomException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * FR-012 편지 사진 저장임. **로컬 디스크에 둔다** — 오브젝트 스토리지를 붙이면 자격증명과 업로드 정책과 CORS가 따라오는데 남은 일정에 맞지
 * 않음. 배포가 단일 인스턴스이고 시연 당일에만 살아 있으면 되므로 이 단순화가 성립함.
 *
 * ponytail: 재배포하면 파일이 사라진다. 여러 인스턴스로 늘리거나 파일이 살아남아야 하는 순간이 오면 오브젝트 스토리지로 옮길 것.
 *
 * 접근 제어는 파일명의 UUID 비추측성에 기댄다 — 초대 토큰과 같은 보안 모델임(ADR-013 결정 4).
 */
@Service
public class LetterImageService {

	/** ADR-008의 7번. 파일당 10MB, 최대 5장. */
	static final long MAX_BYTES = 10L * 1024 * 1024;

	static final int MAX_COUNT = 5;

	/** 확장자를 서버가 정함 — 올라온 파일명을 그대로 쓰면 경로 조작과 실행 가능 확장자가 섞여 들어옴. */
	private static final Map<String, String> ALLOWED = Map.of("image/jpeg", ".jpg", "image/png", ".png", "image/webp",
			".webp", "image/gif", ".gif");

	/** 이 접두사로 시작하는 URL만 선물에 실을 수 있음 — 외부 URL 주입을 막는 지점임. */
	static final String URL_PREFIX = "/letter-images/";

	/**
	 * 선물에 실을 수 있는 URL의 정확한 형태임. **접두사만 검사하면 뚫린다** — `/letter-images/../api/v1/auth/me`나
	 * `%2e%2e`가 startsWith를 통과하고, 소비자가 dot-segment를 정규화하면 이 폴더 밖의 같은 오리진 경로를 부르게 됨. 그래서
	 * UUID 32자리와 허용 확장자까지 통째로 못박음.
	 */
	private static final Pattern URL_FORM = Pattern.compile("^/letter-images/[0-9a-f]{32}\\.(jpg|png|webp|gif)$");

	/**
	 * 앞머리 바이트(매직 넘버)가 **선언한 Content-Type과 같은 형식인지** 봄. 완전한 디코딩이 아니라 형식 위장만 걸러내는 최소 검사임.
	 *
	 * "아무 이미지인가"만 보면 JPEG 바이트를 image/png로 선언해 .png로 저장하는 길이 남음 — 확장자를 선언 MIME으로 정하기 때문임.
	 *
	 * ponytail: ImageIO로 실제 디코딩까지 하면 확실하지만 큰 파일에서 비싸고, 여기서 막으려는 것은 형식 위장이라 앞머리로 충분함.
	 */
	private boolean matchesDeclaredType(MultipartFile file, String contentType) {
		byte[] head;
		try (var stream = file.getInputStream()) {
			// read()는 요청한 만큼 채운다는 보장이 없음. readNBytes는 스트림이 끝날 때까지 채움
			head = stream.readNBytes(12);
		}
		catch (IOException ex) {
			return false;
		}
		if (head.length < 12) {
			return false;
		}

		// **선언한 형식과 같은지**를 봄. "아무 이미지인가"만 보면 JPEG 바이트를 image/png로 선언해 .png로 저장할 수 있음
		return switch (contentType) {
			case "image/jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
			case "image/png" -> (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
			case "image/gif" -> head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8';
			case "image/webp" -> head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F' && head[8] == 'W'
					&& head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
			default -> false;
		};
	}

	/** 업로드가 실제로 발급한 URL인가. 형태와 실물 존재를 둘 다 봄. */
	public boolean isIssuedUrl(String url) {
		if (url == null || !URL_FORM.matcher(url).matches()) {
			return false;
		}
		return Files.isRegularFile(this.directory.resolve(url.substring(URL_PREFIX.length())));
	}

	private final Path directory;

	public LetterImageService(@Value("${app.letter-images.dir}") String directory) {
		this.directory = Path.of(directory);
	}

	public List<String> store(List<MultipartFile> files) {
		if (files == null || files.isEmpty()) {
			throw new CustomException(GiftErrorCode.NO_IMAGE);
		}
		if (files.size() > MAX_COUNT) {
			throw new CustomException(GiftErrorCode.TOO_MANY_IMAGES);
		}

		List<String> urls = new ArrayList<>();
		for (MultipartFile file : files) {
			urls.add(storeOne(file));
		}
		return urls;
	}

	private String storeOne(MultipartFile file) {
		if (file.isEmpty() || file.getSize() > MAX_BYTES) {
			throw new CustomException(GiftErrorCode.INVALID_IMAGE);
		}

		String contentType = (file.getContentType() == null) ? ""
				: file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
		String extension = ALLOWED.get(contentType);
		if (extension == null) {
			throw new CustomException(GiftErrorCode.INVALID_IMAGE);
		}

		// 선언 MIME만 믿으면 임의 바이트를 image/jpeg로 보내 .jpg로 저장할 수 있음. 앞머리 바이트로 실제 형식을 확인함
		if (!matchesDeclaredType(file, contentType)) {
			throw new CustomException(GiftErrorCode.INVALID_IMAGE);
		}

		String name = UUID.randomUUID().toString().replace("-", "") + extension;
		try {
			Files.createDirectories(this.directory);
			file.transferTo(this.directory.resolve(name));
		}
		catch (IOException ex) {
			throw new IllegalStateException("편지 사진 저장 실패", ex);
		}
		return URL_PREFIX + name;
	}

}
