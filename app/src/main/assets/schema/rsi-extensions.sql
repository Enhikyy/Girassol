CREATE TABLE IF NOT EXISTS rsi_promotion_ledger (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    world_tick INTEGER NOT NULL,
    proposer_agent_id TEXT NOT NULL,
    baseline_hash TEXT NOT NULL,
    candidate_hash TEXT NOT NULL,
    promoted INTEGER NOT NULL CHECK (promoted IN (0, 1)),
    net_fitness_gain REAL NOT NULL,
    brier_before REAL NOT NULL,
    brier_after REAL NOT NULL,
    reason TEXT NOT NULL,
    created_utc INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS subjective_beliefs (
    observer_agent_id TEXT NOT NULL,
    claim_key TEXT NOT NULL,
    domain TEXT NOT NULL,
    belief REAL NOT NULL,
    disbelief REAL NOT NULL,
    uncertainty REAL NOT NULL,
    base_rate REAL NOT NULL,
    provenance_event_id INTEGER,
    updated_tick INTEGER NOT NULL,
    PRIMARY KEY (observer_agent_id, claim_key)
);

CREATE TABLE IF NOT EXISTS ecological_niche_structures (
    structure_id TEXT PRIMARY KEY,
    structure_type TEXT NOT NULL,
    grid_x INTEGER NOT NULL,
    grid_z INTEGER NOT NULL,
    level INTEGER NOT NULL,
    structural_integrity REAL NOT NULL,
    builder_agent_id TEXT NOT NULL,
    built_at_tick INTEGER NOT NULL
);