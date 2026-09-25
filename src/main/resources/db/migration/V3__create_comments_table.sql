CREATE TABLE comments (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id  VARCHAR(20)  NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    body       TEXT         NOT NULL,
    author     VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_comments_ticket_id ON comments (ticket_id);
