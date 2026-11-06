ALTER TABLE app_user
 ADD COLUMN tino_unlocked BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN tino_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 ADD CONSTRAINT ck_user_tino_enabled CHECK (tino_enabled=FALSE OR tino_unlocked=TRUE);
