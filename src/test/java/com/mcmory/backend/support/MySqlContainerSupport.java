package com.mcmory.backend.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * 통합 테스트 공용 MySQL 컨테이너 베이스임. 통합 테스트 클래스는 이 클래스를 상속해 컨테이너를 공유함.
 *
 * 컨테이너를 클래스마다 띄우면 콜드 스타트를 그만큼 지불하고, 각 클래스의 @ServiceConnection 필드가 서로 다른
 * ServiceConnectionContextCustomizer를 만들어 Spring TestContext 캐시 키까지 갈라짐(캐시 키는
 * connectionName·connectionDetailsTypes·컨테이너 인스턴스 동일성으로 판정함). 필드를 이 베이스 한 곳에 두면 인스턴스가 하나라
 * 키가 합쳐지고 컨텍스트도 재사용됨.
 *
 * ponytail: 정리는 Testcontainers Ryuk에 맡기고 stop()을 호출하지 않음. 컨텍스트가 여럿 공유하는 컨테이너를 어느 한쪽이 종료하면
 * 나머지가 깨지므로 JVM 수명 동안 살려 둠.
 */
public abstract class MySqlContainerSupport {

	// 스키마 정본을 컨테이너 entrypoint에 넣음. ddl-auto가 validate라 스키마가 없으면
	// 엔티티가 하나라도 생기는 순간 모든 컨텍스트 로딩 테스트가 SchemaManagementException으로 죽음.
	// withInitScript(JDBC 실행)가 아니라 entrypoint 마운트를 쓰는 이유는 로컬 compose.yml과 같은 경로로
	// 태우기 위함임(01-schema.sql 첫 줄의 SET NAMES utf8mb4가 그 전제).
	// 반드시 classpath 사본을 가리킬 것. 레포 밖 경로를 forHostPath로 가리키면 CI에서 파일이 없는데도
	// MountableFile이 예외를 던지지 않아 테이블 0개로 조용히 통과함(실측). 어긋나면 SchemaLoadedTest가 잡음.
	private static final String SCHEMA = "db/01-schema.sql";

	/** 시드도 함께 태움. 통합 테스트가 상품과 매장 카탈로그를 전제로 하므로 스키마만으로는 빈 DB임. */
	private static final String SEED = "db/02-seed.sql";

	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
		.withCopyFileToContainer(MountableFile.forClasspathResource(SCHEMA),
				"/docker-entrypoint-initdb.d/01-schema.sql")
		.withCopyFileToContainer(MountableFile.forClasspathResource(SEED), "/docker-entrypoint-initdb.d/02-seed.sql");

	static {
		MYSQL.start();
	}

}
