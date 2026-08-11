package com.mcmory.backend.taste;

/**
 * 설문 화면(`Start-01`)에 보일 발송자 표시명임. **실명을 쓰지 않는다**(ADR-001의 익명 원칙).
 *
 * 인터페이스로 둔 이유는 아래 셋이 미결이라 구현이 바뀔 자리이기 때문임.
 *
 * <ul>
 * <li>닉네임을 <b>누가 정하는가</b> — 발송자가 직접 입력하는지, 수신자별로 서버가 무작위 부여하는지
 * <li>선물 초대의 닉네임(`GiftService.issueNickname`, 형용사 더하기 호저)과 <b>같은 값을 써야 하는가</b> — 같은 사람인데
 * 설문과 편지에서 다른 이름으로 보이면 수신자가 두 사람으로 오인함
 * <li>설정 화면이 필요한가 — <b>v1.1 디자인에 그 화면이 없다</b>
 * </ul>
 *
 * <b>기획자 의도 파악 후 구현한다.</b> 그전까지는 더미를 돌려줌.
 */
public interface AnonNicknameProvider {

	/**
	 * 이 친구에게 보일 발송자 표시명임.
	 * @param friendId 설문을 받은 친구
	 */
	String senderNameFor(Long friendId);

}
