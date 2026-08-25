CREATE TABLE lgpd_requests (                                        -- RF-115
  id           uuid PRIMARY KEY,
  subject_kind text NOT NULL CHECK (subject_kind IN ('BUYER','ACCOUNT')),
  subject_ref  text NOT NULL,
  kind         text NOT NULL CHECK (kind IN ('ACCESS','DELETION','CORRECTION','PORTABILITY')),
  status       text NOT NULL DEFAULT 'OPEN'
                 CHECK (status IN ('OPEN','IN_PROGRESS','DONE','REJECTED')),
  due_at       timestamptz NOT NULL,                                -- RNF-027
  handled_by   uuid REFERENCES admin_users(id),
  resolution   text,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON lgpd_requests (due_at) WHERE status IN ('OPEN','IN_PROGRESS');
