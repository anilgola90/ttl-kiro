CREATE TABLE tickets (
    id               VARCHAR(20)  PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    description      TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    priority         VARCHAR(10)  NOT NULL,
    assignee         VARCHAR(100),
    resolution_notes TEXT,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_tickets_status     ON tickets (status);
CREATE INDEX idx_tickets_created_at ON tickets (created_at DESC);
