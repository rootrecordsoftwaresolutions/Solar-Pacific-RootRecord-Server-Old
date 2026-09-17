-- Editable content blobs for hosted sites + personal sites (e.g. alexrs94).
CREATE TABLE IF NOT EXISTS rr_content_blobs (
  id TEXT PRIMARY KEY NOT NULL,
  owner_account_id TEXT NOT NULL,
  site_key TEXT NOT NULL,
  kind TEXT NOT NULL,
  path TEXT NOT NULL,
  title TEXT NOT NULL DEFAULT '',
  body TEXT NOT NULL DEFAULT '',
  meta_json TEXT NOT NULL DEFAULT '{}',
  updated_at TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_rr_content_blobs_key_path
  ON rr_content_blobs (site_key, kind, path);
CREATE INDEX IF NOT EXISTS idx_rr_content_blobs_owner
  ON rr_content_blobs (owner_account_id);
CREATE INDEX IF NOT EXISTS idx_rr_content_blobs_site
  ON rr_content_blobs (site_key);
