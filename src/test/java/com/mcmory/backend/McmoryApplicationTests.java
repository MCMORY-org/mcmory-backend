package com.mcmory.backend;

import com.mcmory.backend.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 컨텍스트 로딩 확인. CI 파이프라인이 실제로 도는지 보는 용도임.
 *
 * MySqlContainerSupport를 상속해 공용 컨테이너를 쓴다. 로컬 개발 DB(포트 3307)에 의존하지 않으므로 러너에서도 동일하게 돈다.
 */
@SpringBootTest
class McmoryApplicationTests extends MySqlContainerSupport {

	@Test
	void contextLoads() {
	}

}
