-- name: yesql-insert-feedback<!
-- Add feedback
INSERT INTO feedback (
  key,
  feedback,
  service,
  created_time,
  stars,
  user_agent,
  data
) VALUES (
  :key,
  :feedback,
  :service,
  :created_at,
  :stars,
  :user_agent,
  :data
);

-- name: yesql-get-feedback
SELECT (extract(epoch from created_time)::bigint) * 1000 as created_time, stars, user_agent, feedback FROM feedback WHERE key LIKE ? ORDER BY created_time DESC;

-- name: yesql-get-average
SELECT avg(stars) as keskiarvo, count(stars) as lukumaara FROM feedback WHERE key = :key;
