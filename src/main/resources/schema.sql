CREATE TABLE IF NOT EXISTS member (
  id   VARCHAR(20)  PRIMARY KEY,
  name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS task (
  id          VARCHAR(36)  PRIMARY KEY,
  title       VARCHAR(120) NOT NULL,
  note        VARCHAR(500),
  status      VARCHAR(5)   NOT NULL,
  assignee_id VARCHAR(20),            -- no foreign key on purpose: the seed contains "m-99"
  due_date    DATE,
  version     INT          NOT NULL,
  deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
  updated_at  TIMESTAMP(3) WITH TIME ZONE NOT NULL,  -- stored and formatted as UTC
  seq         BIGINT       NOT NULL UNIQUE   -- change counter, basis of the cursor
);

CREATE TABLE IF NOT EXISTS sync_state (
  id       INT    PRIMARY KEY,        -- always 1
  last_seq BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS idempotency (
  idem_key    VARCHAR(64)   PRIMARY KEY,   -- "key" is a reserved word in H2
  fingerprint VARCHAR(64)   NOT NULL,
  http_status INT           NOT NULL,
  body        VARCHAR(8000) NOT NULL,
  created_at  TIMESTAMP     NOT NULL
);