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
