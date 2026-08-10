package com.mcmory.backend;

import com.mcmory.backend.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 컨테이너에 스키마 정본이 실제로 태워졌는지 확인함.
 *
 * 엔티티가 없는 동안은 ddl-auto=validate가 아무것도 검증하지 않아 마운트가 깨져도 컨텍스트 로딩은 통과함. 이 테스트가 없으면 첫 엔티티를
 * 추가하는 시점에야 스키마 부재를 발견하게 됨.
 */
@SpringBootTest
class SchemaLoadedTest extends MySqlContainerSupport {

	private static final int EXPECTED_TABLES = 13;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void 스키마_정본의_테이블이_전부_생성됨() {
		Integer count = this.jdbc.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()", Integer.class);

		assertThat(count).isEqualTo(EXPECTED_TABLES);
	}

	@Test
	void 한글이_깨지지_않고_저장됨() {
		String charset = this.jdbc.queryForObject("SELECT table_collation FROM information_schema.tables "
				+ "WHERE table_schema = DATABASE() AND table_name = 'member'", String.class);

		assertThat(charset).startsWith("utf8mb4");
	}

}
