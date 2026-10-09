CREATE TABLE organizations (
  id uuid PRIMARY KEY, name varchar(120) NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE memberships (
  organization_id uuid NOT NULL REFERENCES organizations(id), subject varchar(200) NOT NULL,
  display_name varchar(120) NOT NULL, role varchar(20) NOT NULL CHECK (role IN ('ADMIN','RESPONDER','VIEWER')),
  PRIMARY KEY (organization_id, subject)
);
CREATE INDEX memberships_subject_idx ON memberships(subject, organization_id);
CREATE TABLE incidents (
  id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id),
  title varchar(160) NOT NULL, description varchar(8000) NOT NULL,
  severity varchar(10) NOT NULL CHECK (severity IN ('SEV1','SEV2','SEV3','SEV4')),
  status varchar(20) NOT NULL CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED')),
  assignee varchar(200), created_by varchar(200) NOT NULL, version integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, id),
  FOREIGN KEY (organization_id, assignee) REFERENCES memberships(organization_id, subject)
);
CREATE INDEX incidents_tenant_created_idx ON incidents(organization_id, created_at DESC, id DESC);
CREATE INDEX incidents_tenant_status_idx ON incidents(organization_id, status, created_at DESC, id DESC);
CREATE TABLE incident_activity (
  id uuid PRIMARY KEY, organization_id uuid NOT NULL, incident_id uuid NOT NULL,
  actor varchar(200) NOT NULL, message varchar(2000) NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (organization_id, incident_id) REFERENCES incidents(organization_id,id)
);
CREATE INDEX activity_tenant_incident_idx ON incident_activity(organization_id,incident_id,created_at DESC);
CREATE TABLE outbox (
  id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id), payload jsonb NOT NULL,
  attempts integer NOT NULL DEFAULT 0, available_at timestamptz NOT NULL DEFAULT now(),
  published_at timestamptz, dead_at timestamptz, last_error varchar(1000), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX outbox_pending_idx ON outbox(available_at) WHERE published_at IS NULL AND dead_at IS NULL;
CREATE TABLE processed_events (event_id uuid PRIMARY KEY, processed_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE notifications (
  id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES processed_events(event_id), organization_id uuid NOT NULL,
  subject varchar(200) NOT NULL, incident_id uuid NOT NULL, message varchar(500) NOT NULL,
  read_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (organization_id,incident_id) REFERENCES incidents(organization_id,id),
  UNIQUE (event_id,subject)
);
CREATE INDEX notifications_inbox_idx ON notifications(organization_id,subject,created_at DESC);
