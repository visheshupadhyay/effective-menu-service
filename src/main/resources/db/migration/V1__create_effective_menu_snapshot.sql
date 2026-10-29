-- One row contains the complete customer-ready menu for one outlet.
CREATE TABLE effective_menu_snapshot (
    outlet_id BIGINT PRIMARY KEY,
    snapshot_version BIGINT NOT NULL,
    effective_menu JSONB NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT effective_menu_snapshot_version_ck CHECK (snapshot_version >= 0)
);

COMMENT ON TABLE effective_menu_snapshot IS
    'Materialized customer-ready effective menu owned by Effective Menu Service';
COMMENT ON COLUMN effective_menu_snapshot.outlet_id IS
    'Catalog outlet identifier; no cross-service foreign key is used';
COMMENT ON COLUMN effective_menu_snapshot.snapshot_version IS
    'Newest catalog version included in this snapshot';
COMMENT ON COLUMN effective_menu_snapshot.effective_menu IS
    'Complete effective menu document returned to customers';
