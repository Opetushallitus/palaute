CREATE TABLE feedback (
  id           BIGSERIAL   PRIMARY KEY,
  created_time TIMESTAMP   WITH TIME ZONE DEFAULT now(),
  key          TEXT        NOT NULL,
  stars        INTEGER     NOT NULL,
  feedback     TEXT        NULL,
  user_agent   TEXT        NULL,
  data         JSONB       NULL
);

