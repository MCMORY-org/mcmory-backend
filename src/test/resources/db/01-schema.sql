-- 컨테이너의 character_set_client 기본값이 latin1이라 이 선언이 없으면 한글이 깨져 저장된다.
SET NAMES utf8mb4;

-- MCMORY 프로토타입 스키마. docs/architecture/erd-and-tables.md 기준.
-- 이식 시에는 JPA ddl-auto나 마이그레이션 도구가 이 역할을 대신한다.
-- ponytail: 프로토타입 검증용이라 인덱스는 조회 경로에 걸리는 것만 둔다.

CREATE TABLE member (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(20)  NOT NULL,
  phone         VARCHAR(11)  NOT NULL UNIQUE,          -- 로그인 ID. 숫자만 저장(ADR-008)
  password_hash VARCHAR(255) NOT NULL,
  birth_date    DATE         NULL,
  gender        VARCHAR(10)  NULL,                     -- MALE, FEMALE, NONE (ADR-008)
  sms_opt_in    BOOLEAN      NOT NULL DEFAULT FALSE,   -- consent 이력의 현재값 캐시
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at    DATETIME     NULL                      -- 회원 탈퇴는 MVP 범위 밖. 예약 컬럼
);

-- ADR-013 결정 9와 11: 기기당 1행, prev_* 는 회전 경쟁 유예창 판정용
CREATE TABLE refresh_token (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id       BIGINT       NOT NULL,               -- 유니크 아님. campus 원형은 유니크였고 그것이 앞 기기를 끊는 원인이었다
  token_hash      VARCHAR(255) NOT NULL UNIQUE,
  prev_token_hash VARCHAR(255) NULL,
  prev_rotated_at DATETIME     NULL,
  expires_at      DATETIME     NOT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_rt_member FOREIGN KEY (member_id) REFERENCES member(id),
  INDEX idx_rt_prev (prev_token_hash)
);

CREATE TABLE consent (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id    BIGINT      NULL,
  gift_id      BIGINT      NULL,
  consent_type VARCHAR(30) NOT NULL,
  agreed       BOOLEAN     NOT NULL,
  terms_version VARCHAR(20) NOT NULL DEFAULT 'v1',      -- 시연까지 단일 버전 상수
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- member_id와 gift_id 중 정확히 하나만 값이 있어야 한다
  CONSTRAINT ck_consent_owner CHECK ((member_id IS NULL) <> (gift_id IS NULL)),
  CONSTRAINT fk_consent_member FOREIGN KEY (member_id) REFERENCES member(id)
  -- gift_id FK는 gift 테이블이 아래에 있어 여기서 못 건다. 파일 끝 ALTER로 붙임
);

CREATE TABLE friend (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_member_id BIGINT      NOT NULL,
  name            VARCHAR(20) NULL,                     -- 삭제 즉시 NULL 파기(ADR-003)
  phone           VARCHAR(11) NULL,
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at      DATETIME    NULL,
  CONSTRAINT fk_friend_owner FOREIGN KEY (owner_member_id) REFERENCES member(id),
  CONSTRAINT uk_friend_phone UNIQUE (owner_member_id, phone)
);

CREATE TABLE product (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(100) NOT NULL,
  category     VARCHAR(30)  NOT NULL,
  color        VARCHAR(30)  NULL,
  price        INT          NULL,
  image_url    VARCHAR(500) NULL,
  official_url VARCHAR(500) NULL,
  style_tags   JSON         NULL,
  demo_serial  VARCHAR(30)  NULL UNIQUE                 -- FR-028 시연용 매칭 키
);

CREATE TABLE taste_profile (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT      NULL,
  friend_id BIGINT      NULL,
  source    VARCHAR(20) NOT NULL DEFAULT 'OWNER_INPUT', -- 프로토타입은 OWNER_INPUT만
  answers   JSON        NOT NULL,
  updated_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT ck_taste_owner CHECK ((member_id IS NULL) <> (friend_id IS NULL)),
  -- 대상당 1행(ADR-009 결정 9). 두 컬럼 중 하나는 항상 NULL인데 InnoDB는 NULL을 서로 다른 값으로 봐서 그쪽은 제약이 걸리지 않음
  CONSTRAINT uq_taste_friend UNIQUE (friend_id),
  CONSTRAINT uq_taste_member UNIQUE (member_id),
  CONSTRAINT fk_taste_member FOREIGN KEY (member_id) REFERENCES member(id),
  CONSTRAINT fk_taste_friend FOREIGN KEY (friend_id) REFERENCES friend(id)
);

CREATE TABLE recommendation (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT   NOT NULL,
  friend_id BIGINT   NULL,                              -- 취향 저장 시 귀속(ADR-009)
  context   JSON     NOT NULL,                          -- 선물 건 맥락(목적, 관계, 예산)
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_rec_member FOREIGN KEY (member_id) REFERENCES member(id),
  CONSTRAINT fk_rec_friend FOREIGN KEY (friend_id) REFERENCES friend(id)
);

CREATE TABLE recommendation_result (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  recommendation_id BIGINT      NOT NULL,
  product_id        BIGINT      NOT NULL,
  rank_no           INT         NOT NULL,
  reason_type       VARCHAR(10) NOT NULL,               -- PERSONAL 또는 GENERAL
  reason            VARCHAR(200) NULL,
  CONSTRAINT fk_recres_rec FOREIGN KEY (recommendation_id) REFERENCES recommendation(id),
  CONSTRAINT fk_recres_product FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE TABLE gift (
  id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
  sender_member_id       BIGINT       NOT NULL,
  friend_id              BIGINT       NOT NULL,
  product_id             BIGINT       NULL,
  recommendation_id      BIGINT       NULL,
  invite_token           VARCHAR(64)  NOT NULL UNIQUE,
  anon_nickname          VARCHAR(30)  NOT NULL,          -- 발송 시 고정 발급(ADR-001)
  is_anonymous           BOOLEAN      NOT NULL DEFAULT TRUE,
  status                 VARCHAR(20)  NOT NULL,          -- CREATED, SENT, OPENED (ADR-011)
  letter_body            TEXT         NULL,              -- letter 테이블을 gift에 병합
  letter_image_urls      JSON         NULL,
  letter_color           VARCHAR(7)   NULL,               -- FR-012 편지지 색상 토큰(GOLD, BLACK, BEIGE, PINK). 미지정이면 기본 편지지
  change_requested_at    DATETIME     NULL,              -- 옵션 변경 문의 1회(ADR-007)
  recipient_member_id    BIGINT       NULL,              -- 발송 시점에 전화번호로 해석한 회원 수신자(ADR-006 결정 5). NULL이면 비회원
  recipient_consented_at DATETIME     NULL,              -- NULL이면 열람 불가(FR-015)
  sent_at                DATETIME     NULL,
  opened_at              DATETIME     NULL,
  CONSTRAINT fk_gift_sender FOREIGN KEY (sender_member_id) REFERENCES member(id),
  CONSTRAINT fk_gift_friend FOREIGN KEY (friend_id) REFERENCES friend(id),
  CONSTRAINT fk_gift_recipient FOREIGN KEY (recipient_member_id) REFERENCES member(id),
  CONSTRAINT fk_gift_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_gift_rec FOREIGN KEY (recommendation_id) REFERENCES recommendation(id)
);

CREATE TABLE owned_product (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id       BIGINT       NOT NULL,
  source          VARCHAR(10)  NOT NULL,                -- GIFT 또는 EXTERNAL
  product_id      BIGINT       NULL,                    -- GIFT 또는 시드 매칭 시 참조
  gift_id         BIGINT       NULL,
  custom_name     VARCHAR(100) NULL,                    -- 수동 폼은 프로토타입 범위 밖
  custom_category VARCHAR(30)  NULL,
  custom_color    VARCHAR(30)  NULL,
  serial_memo     VARCHAR(100) NULL,                    -- 검증 없는 메모(ADR-004)
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at      DATETIME     NULL,
  CONSTRAINT fk_owned_member FOREIGN KEY (member_id) REFERENCES member(id),
  CONSTRAINT fk_owned_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_owned_gift FOREIGN KEY (gift_id) REFERENCES gift(id)
);

CREATE TABLE store (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  name              VARCHAR(100)  NOT NULL,
  address           VARCHAR(200)  NOT NULL,
  lat               DECIMAL(10,7) NOT NULL,
  lng               DECIMAL(10,7) NOT NULL,
  open_time         TIME          NOT NULL,
  close_time        TIME          NOT NULL,
  repair_available  BOOLEAN       NOT NULL DEFAULT TRUE
);

-- ADR-012: 취소는 행 삭제로 슬롯 해제. MySQL은 부분 유니크 인덱스가 없어
-- 활성 예약에만 거는 조건부 유니크가 불가능하기 때문이다.
CREATE TABLE store_reservation (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id        BIGINT       NOT NULL,
  store_id         BIGINT       NOT NULL,
  owned_product_id BIGINT       NOT NULL,
  reserve_date     DATE         NOT NULL,               -- 완전한 날짜 저장
  time_slot        VARCHAR(5)   NOT NULL,               -- 10:00 형식, 슬롯 상수 10개(ADR-012 개정 이력 — v1.1이 20시까지 늘렸다)
  request_note     VARCHAR(500) NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_res_member FOREIGN KEY (member_id) REFERENCES member(id),
  CONSTRAINT fk_res_store FOREIGN KEY (store_id) REFERENCES store(id),
  -- 이 FK가 붙기 전에는 코드가 유일한 방어선이었다(ReservationService.create의 소유 조회)
  CONSTRAINT fk_res_owned FOREIGN KEY (owned_product_id) REFERENCES owned_product(id),
  CONSTRAINT uk_res_slot UNIQUE (store_id, reserve_date, time_slot)
);

CREATE TABLE notification (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT      NOT NULL,
  type      VARCHAR(30) NOT NULL,                       -- GIFT_ARRIVED, GIFT_OPENED
  gift_id   BIGINT      NULL,
  read_at   DATETIME    NULL,
  created_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_noti_member FOREIGN KEY (member_id) REFERENCES member(id),
  CONSTRAINT fk_noti_gift FOREIGN KEY (gift_id) REFERENCES gift(id)
);

-- FK 보강 (2026-08-10, FIX-W001 T3). ADR-010이 "무결성을 DB 제약으로 강제한다"고 선언했는데
-- 11건이 빠져 있었다. 열은 각 테이블에 인라인으로 걸었고, 아래 하나만 참조 대상(gift)이
-- 자기보다 뒤에 정의돼 있어(전방 참조) 여기서 붙인다.
--
-- ON DELETE는 걸지 않는다(MySQL 기본 RESTRICT). soft delete가 정본이라(ADR-003) 물리 삭제
-- 경로가 없고, 제약의 목적은 고아 행 방지이지 연쇄 삭제가 아니다.
ALTER TABLE consent
  ADD CONSTRAINT fk_consent_gift FOREIGN KEY (gift_id) REFERENCES gift(id);
