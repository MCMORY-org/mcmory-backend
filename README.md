# 🎁 MCMORY 백엔드 (Spring Boot + JPA + MySQL)

MCMORY Back-end 레포지토리입니다. **MCMORY**는 멋쟁이사자처럼 중앙 해커톤 출품작으로, **취향 기반 선물 추천과 익명 편지** 서비스입니다.

> 발송자가 취향으로 추천을 받아 익명 편지와 함께 선물을 보내고, 수신자가 초대 링크에서 **동의한 뒤에** 열어봅니다. 여기에 제품 관리와 매장 예약이 따라붙습니다.

REST API 서버이며 응답은 전부 공통 봉투(`CustomResponse`)로 나갑니다.

> **현재 상태: CI 골격만 있습니다.** 이 저장소에는 빌드 설정과 GitHub Actions 워크플로와 진입점 클래스만 있고 **도메인 코드는 아직 없습니다.** 제품 코드는 개인 private 저장소(`mcmory-proto-backend`)에서 검증 중이고 이슈 단위로 이곳에 이식합니다. 그래서 아래 내용 중 도메인·API·스모크 부분은 **승격될 코드의 구성**을 미리 기술한 것입니다.

> **`docs/`는 이 저장소에 없습니다.** API명세서·데이터모델·ADR은 상위 작업공간에 있고 git으로 추적되지 않아 **clone해도 따라오지 않습니다**. 저장소 안에서 바로 볼 수 있는 규칙 정본은 `AGENTS.md`입니다.

## 📍 API 엔드포인트

**모든 엔드포인트는 `/api/v1` 접두사를 가집니다.** 전체 경로와 요청·응답 계약과 에러코드는 **서버 기동 후 Swagger UI**와 상위 프로젝트의 **`docs/architecture/API명세서.md`**에서 관리합니다. README에는 목록을 중복하지 않으므로 **API가 바뀌어도 README를 고칠 필요가 없습니다.**

> Swagger UI: `http://localhost:8080/swagger-ui/index.html` (springdoc이 `/v3/api-docs`를 코드에서 자동 생성)

응답 봉투는 `isSuccess`, `code`, `message`, `result` 네 필드입니다. 멋사 14기 형식이라 프론트가 배운 그대로 파싱합니다. **실패는 문구가 아니라 `code`로 분기하세요** — 문구는 UX 개선으로 바뀝니다.

## 🛠️ 기술 스택

- **Language**: Java 17 (toolchain 고정 — Java 21 API 사용 금지. `List.getFirst()` 등은 컴파일 실패)
- **Framework**: Spring Boot 4.0.3, Spring MVC
- **Build**: Gradle (Groovy DSL, wrapper 9.3.1)
- **Persistence**: Spring Data JPA + MySQL 8.0
- **Security**: Spring Security + JWT (jjwt 0.13.0)
- **API Docs**: springdoc-openapi 3.0.2 (Swagger UI)
- **Test**: JUnit 5 + Testcontainers (실제 MySQL)
- **Quality**: spring-javaformat 0.0.47, Checkstyle 10.17.0, SpotBugs 6.5.8, JaCoCo 0.8.14

> **JaCoCo는 리포트만 냅니다. 커버리지 수치 게이트는 두지 않습니다.** 해커톤 일정에서 숫자를 채우려고 의미 없는 테스트를 쓰게 되기 때문이고, 게이트는 "필수 검증 항목 전부 녹색"입니다.

> 소스 인코딩과 포맷터 인코딩을 **UTF-8로 명시 고정**합니다. 개발 머신의 JVM 기본 인코딩이 MS949라 고정하지 않으면 한글 리터럴이 깨지고 `format`과 `checkFormat`이 엇갈립니다.

## 🔐 인증

- 액세스 토큰과 리프레시 토큰을 **모두 HttpOnly 쿠키**로 내려줍니다. 프론트가 토큰을 직접 다루지 않습니다.
- 액세스 30분, 리프레시 1일이며 리프레시는 회전합니다. 회전에는 **30초 유예창**이 있습니다.
- **유예 경로는 200이면서 `Set-Cookie`를 보내지 않습니다.** 프론트는 그 부재를 실패로 다루면 안 됩니다.
- 쿠키 SameSite는 `Lax`, `secure`는 로컬 기본 `false`이고 배포 프로파일에서만 `true`로 올립니다.

## 🏃 빠른 시작

**사전 요구사항: JDK 17, Docker.** Docker는 로컬 MySQL과 Testcontainers 통합 테스트 모두에 필요합니다.

```bash
# 1. JDK 확인 (17이어야 함)
./gradlew -version

# 2. 8080이 비어 있는지 확인
netstat -ano | grep :8080

# 3. 로컬 MySQL 기동 (첫 기동에 스키마와 시드가 자동 적용됨)
docker compose up -d

# 4. 서버 실행
./gradlew bootRun

# 5. 확인
#    http://localhost:8080/actuator/health
#    http://localhost:8080/swagger-ui/index.html
```

**이 레포만 clone하면 됩니다.** 별도 파일 없이 뜹니다. 로그인 계정은 `01012345678 / 1234`입니다.

### 로컬 DB

| 항목 | 값 |
| :--- | :--- |
| 이미지 | mysql:8.0 |
| 호스트 포트 | 3307 |
| 데이터베이스 | `mcmory_java` |
| 계정 | `root` / `mcmory` |

포트 3307은 다른 MySQL과 겹치지 않게 고른 값이고, `application.yml`의 기본 접속 URL이 3307이라 **포트를 옮기면 팀원이 환경변수를 따로 줘야 합니다.**

`compose.yml`이 테스트 리소스의 스키마 디렉터리를 초기화 디렉터리로 마운트하므로 **테스트가 쓰는 스키마와 시드가 그대로 들어갑니다.** 사본을 늘리지 않으려는 것이고, 덕분에 엔티티와 스키마가 항상 같은 커밋에서 움직입니다.

**스키마가 바뀌면 볼륨을 지우고 다시 띄웁니다.** 초기화 스크립트는 볼륨이 빌 때만 돌고, `ddl-auto`가 `validate`라 어긋난 채로는 서버가 기동조차 못 합니다.

```bash
docker compose down -v && docker compose up -d
```

> **`-v`는 로컬 데이터를 전부 지웁니다.** 살려야 할 데이터가 있으면 지우는 대신 `ALTER TABLE`을 기동 중인 DB에 직접 거세요.

### 개별 테스트 실행

`--tests` 패턴으로 클래스·메소드·패키지 단위로 골라 실행합니다.

```bash
./gradlew test --tests "*AuthServiceTest*"          # 클래스 하나
./gradlew test --tests "com.mcmory.backend.gift.*"  # 패키지 전체
./gradlew test --tests "*GiftServiceTest*" --info   # 상세 로그
```

> **개별 실행이라고 빨라지지 않습니다.** Testcontainers가 컨테이너를 재사용하지 않아 **매번 콜드 스타트로 약 2분**이 듭니다. 여러 개를 볼 거면 그냥 `./gradlew test`가 낫습니다.

### 막히면

| 증상 | 원인 |
| :--- | :--- |
| 컴파일 실패 (`getFirst()` 등) | JDK 21 문법을 썼습니다. **Java 17 문법**으로 쓰세요 |
| 테스트가 Docker 오류로 실패 | Docker Desktop이 꺼져 있습니다(Testcontainers 필요) |
| 기동 시 스키마 검증 실패 | `ddl-auto: validate`입니다. 스키마 변경을 DB에 반영하지 않았습니다 |
| 포트 3307 충돌 | 다른 MySQL 컨테이너가 이미 3307을 쓰고 있습니다. 하나만 띄우세요 |
| `bootRun`이 exit 1인데 8080이 응답함 | **8080에 구버전 서버가 살아 있습니다.** 응답만 보지 말고 `netstat -ano \| grep :8080`의 PID가 바뀌었는지 보세요. 안 바뀌었으면 `taskkill //F //PID {pid} //T` |
| 한글이 깨져 저장됨 | mysql 클라이언트 기본값이 latin1입니다. `--default-character-set=utf8mb4`를 주세요 |

## 🔒 검증 루프 (커밋 전 필수)

커밋 전 아래 5단계를 순서대로 실행합니다. 한 단계라도 실패하면 수정 후 **그 단계부터** 재실행하며, 전체 통과 전에는 커밋과 push를 하지 않습니다.

```bash
./gradlew format                                    # 별도 호출
./gradlew checkFormat
./gradlew checkstyleMain checkstyleTest spotbugsMain spotbugsTest
./gradlew test
./gradlew build bootJar
```

**`format`을 검증 명령과 같은 Gradle 호출에 섞지 마세요.** `./gradlew format check` 처럼 한 번에 부르면 Gradle의 암묵 의존성 검증이 실패합니다. 반드시 따로 돌린 뒤 `checkFormat`부터 재검증합니다.

> Checkstyle이 **한국어 지역 변수명을 거부합니다**(`^[a-z][a-zA-Z0-9]*$`). 테스트 메소드명은 한국어를 허용합니다.

계약 검증은 스모크입니다. 서버를 띄운 뒤 프로토타입 저장소에서 돌립니다.

```bash
MCMORY_BASE=http://localhost:8080 MCMORY_ENVELOPE=1 npm run smoke
```

**`MCMORY_ENVELOPE=1`을 빼지 마세요.** 이것이 응답 봉투를 지키는 장치라, 없으면 서버가 날것 응답으로 회귀해도 전 항목이 통과합니다.

> GitHub Actions CI가 `main`과 `dev` push와 PR마다 commitlint, checkFormat, Checkstyle, SpotBugs, test, JaCoCo 리포트, build bootJar 순으로 동일 게이트를 재검증합니다. fail-fast라 앞이 깨지면 뒤를 돌리지 않습니다. `docs/**`와 `**.md` 변경은 CI를 트리거하지 않습니다.
>
> 배포 잡은 `DEPLOY_ENABLED` 저장소 변수(현재 `false`)로 잠겨 있습니다. `Dockerfile`과 배포용 `docker-compose.yml`은 이미 있고, **EC2 인스턴스와 Docker Hub 계정이 정해지면** compose의 이미지 이름을 채우고 시크릿 5종을 등록한 뒤 켭니다.

## 💻 개발 환경 설정 (필수!)

1. **IntelliJ IDEA**를 권장합니다.
2. **들여쓰기는 탭**입니다. spring-javaformat 플러그인을 설치하면 저장 시 자동 포맷이 적용되어 손으로 맞출 필요가 없습니다.
3. 줄바꿈은 **LF**, 인코딩은 **UTF-8**입니다.

## 📜 프로젝트 규약 (Conventions)

- **Git 협업 전략** — base 브랜치는 `dev`입니다.

```
main                # 배포 브랜치
dev                 # 개발 메인 브랜치 (base)
feature/{N}-{name}  # 기능 개발
fix/{name}          # 버그 수정
hotfix/{name}       # 긴급 수정
refactor/{name}     # 리팩토링
docs/{name}         # 문서 작업
```

> `feature/{N}-{name}`에서 `{N}`은 **GitHub 이슈 번호**, `{name}`은 기능 이름입니다.
> - 이슈 단위로 브랜치를 파서 어떤 작업인지 이슈와 바로 연결됩니다.
> - 이슈가 있는 기능 개발: 이슈 12번 선물 발송이면 `feature/12-gift-send`
> - 이슈가 없는 단순 수정: 응답 코드 오타면 `fix/error-code-typo` (번호 생략)

- **작업 순서:**
  1. 이슈를 만들고 `dev`에서 작업 브랜치를 분기합니다.
  2. 작업 후 `dev`로 PR을 생성합니다.
  3. 리뷰 1명 이상을 받고 `dev`에 병합합니다. `main` 직접 commit 금지.

- **커밋 메시지 컨벤션** — **Conventional Commits** 형식입니다. description은 소문자로 시작하고 마침표를 찍지 않습니다. CI의 commitlint가 검사합니다. 1 task = 1 commit.

```
{type}({scope}): {description}

예: feat(gift): 익명 편지 발송 구현

feat     새로운 기능 추가
fix      버그 수정
docs     문서 수정
test     테스트 추가·수정
refactor 리팩토링
chore    빌드·설정 등 (로직 변경 없음)
style    코드 스타일 (포맷팅 등)
perf     성능 개선
ci       CI 설정
revert   되돌리기
```

> **gitmoji는 아직 확정되지 않았습니다.** 옆 프로젝트(campus-hackathon-2026)는 type 앞에 이모지를 붙였지만 이 팀은 정하지 않았습니다. 팀 컨벤션이 확정되면 이 절을 갱신합니다. 그때까지는 이모지 없이 씁니다.

- **디렉토리 구조** — 도메인별 **평면 패키지**입니다.

```
src/
├── main/
│   ├── java/com/mcmory/backend/
│   │   ├── auth/                    # 인증. 토큰 발급·회전, 쿠키, 시큐리티 필터, 가입 정책
│   │   ├── common/                  # 도메인 공용 헬퍼 (전화번호 정규화 등)
│   │   ├── config/                  # 스프링 설정 (시큐리티, 정적 리소스 매핑)
│   │   ├── consent/                 # 동의 항목과 버전, 재동의 필요 판정
│   │   ├── friend/                  # 친구 등록·수정·삭제
│   │   ├── gift/                    # 선물과 익명 편지, 초대 링크, 편지함, 편지 사진
│   │   ├── global/
│   │   │   └── apiPayload/          # 전역 공통. 응답 봉투
│   │   │       ├── code/            # 도메인별 에러코드 enum
│   │   │       └── exception/       # 커스텀 예외와 전역 예외 핸들러
│   │   ├── member/                  # 회원 엔티티와 저장소
│   │   ├── notification/            # 알림 목록과 읽음 처리
│   │   ├── owned/                   # 보유 제품 관리
│   │   ├── product/                 # 제품 마스터 데이터
│   │   ├── recommend/               # 추천 실행과 추천 이력
│   │   ├── reservation/             # 매장 예약
│   │   ├── store/                   # 매장 조회
│   │   └── taste/                   # 취향 프로필
│   └── resources/                   # 애플리케이션 설정
└── test/
    ├── java/com/mcmory/backend/
    │   ├── auth/                    # 도메인 테스트. 운영 패키지 구조를 그대로 따라감
    │   ├── common/
    │   ├── consent/
    │   ├── gift/
    │   ├── recommend/
    │   ├── reservation/
    │   └── support/                 # 테스트 공용 기반 (통합 테스트 지원, MySQL 컨테이너)
    └── resources/
        └── db/                      # 스키마와 시드 정본. 테스트와 compose 초기화가 함께 봄
```

**도메인 패키지 안은 평면입니다.** 컨트롤러·서비스·엔티티·저장소·DTO를 `controller/`, `service/`, `dto/` 하위 폴더로 나누지 않고 도메인 폴더에 나란히 둡니다. 옆 프로젝트(campus)는 도메인마다 하위 폴더로 나눴지만 여기서는 채택하지 않았습니다 — 해커톤 규모에서 한 도메인이 파일 열 개 안팎이라 폴더를 더 파면 탐색만 깊어집니다. **새 도메인도 평면 구조를 따르세요.** 도메인 폴더 하나를 `backend/` 바로 아래에 만들고 그 안에 전부 둡니다. 전역 공통(응답 봉투, 에러코드, 예외 핸들러)만 `global/`로 갑니다.

- **네이밍 컨벤션**
  - 클래스는 PascalCase, 메소드와 변수는 camelCase, 상수는 SCREAMING_SNAKE_CASE를 씁니다.
  - 클래스·메소드·필드·변수·파라미터 네이밍은 **Checkstyle이 자동 강제**합니다(위반 시 빌드 실패). 테스트 메소드명은 한국어 서술형을 허용합니다.
  - 주석은 한국어 명사형 종결(`~함`, `~반환`, `~임`)로 씁니다. UI 문자열과 산문은 평서형을 허용합니다.
  - **불필요한 추상화를 만들지 않습니다.** 표준 라이브러리와 플랫폼 기능을 먼저 쓰고, 의도한 단순화에는 `// ponytail:` 주석을 답니다.
