CREATE TABLE IF NOT EXISTS scores (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    kind       VARCHAR(16) NOT NULL,
    score      INTEGER     NOT NULL,
    kills      INTEGER     NOT NULL,
    cause      VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS scores_name_kind ON scores (name, kind);
