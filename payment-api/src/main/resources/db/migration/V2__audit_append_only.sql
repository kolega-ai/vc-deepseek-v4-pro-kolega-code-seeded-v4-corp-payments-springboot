-- The audit trail is append-only. Beyond the application exposing no update/delete path,
-- this trigger rejects UPDATE/DELETE on audit_event at the database level for every role,
-- including the application's own database user.
CREATE OR REPLACE FUNCTION reject_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only: % is forbidden', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_append_only ON audit_event;
CREATE TRIGGER trg_audit_append_only
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
