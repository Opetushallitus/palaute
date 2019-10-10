-- name: yesql-insert-feedback<!
-- Add feedback
INSERT INTO feedback (
  key,
  feedback,
  stars,
  user_agent,
  data
) VALUES (
  :key,
  :feedback,
  :stars,
  :user_agent,
  :data
);

-- name: yesql-get-feedback
SELECT * FROM feedback WHERE key = :key;

-- name: yesql-get-average
SELECT avg(stars) as keskiarvo, count(stars) as lukumaara FROM feedback WHERE key = :key;
