-- Batch instruction signing phrase: an extra credential a maker uses to sign bulk instructions.

ALTER TABLE portal_user ADD COLUMN signing_digest VARCHAR(200);
