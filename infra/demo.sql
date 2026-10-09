INSERT INTO organizations(id,name) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','Acme Operations'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','Northstar Labs') ON CONFLICT DO NOTHING;
INSERT INTO memberships(organization_id,subject,display_name,role) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','11111111-1111-1111-1111-111111111111','Alice Morgan','ADMIN'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','22222222-2222-2222-2222-222222222222','Bob Chen','RESPONDER'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','33333333-3333-3333-3333-333333333333','Eve Park','ADMIN') ON CONFLICT DO NOTHING;
INSERT INTO incidents(id,organization_id,title,description,severity,status,assignee,created_by,created_at,updated_at) VALUES
('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','Elevated checkout error rate','Checkout errors increased after the latest release. Investigate the payment gateway connection pool.','SEV1','OPEN',NULL,'11111111-1111-1111-1111-111111111111',now()-interval '23 minutes',now()),
('aaaaaaaa-0000-0000-0000-000000000002','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','API response times above threshold','The team is investigating increased latency on the public API.','SEV2','ACKNOWLEDGED','22222222-2222-2222-2222-222222222222','11111111-1111-1111-1111-111111111111',now()-interval '1 hour',now()),
('aaaaaaaa-0000-0000-0000-000000000003','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','Delayed webhook deliveries','Third-party webhook delivery is running behind schedule. No data loss observed.','SEV3','OPEN',NULL,'22222222-2222-2222-2222-222222222222',now()-interval '2 hours',now()),
('aaaaaaaa-0000-0000-0000-000000000004','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','Search index refresh stalled','Search results were stale. Rebuilt the affected index and verified freshness.','SEV3','RESOLVED','22222222-2222-2222-2222-222222222222','11111111-1111-1111-1111-111111111111',now()-interval '3 hours',now()),
('aaaaaaaa-0000-0000-0000-000000000005','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','Dashboard export job failures','Large exports are timing out. Smaller exports continue to work.','SEV4','ACKNOWLEDGED','11111111-1111-1111-1111-111111111111','22222222-2222-2222-2222-222222222222',now()-interval '4 hours',now()),
('aaaaaaaa-0000-0000-0000-000000000006','aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','Email delivery interruption','Delivery resumed after rotating the expired provider credential.','SEV2','RESOLVED','11111111-1111-1111-1111-111111111111','11111111-1111-1111-1111-111111111111',now()-interval '5 hours',now()),
('bbbbbbbb-0000-0000-0000-000000000001','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','Research portal unavailable','This incident belongs only to Northstar Labs.','SEV2','OPEN',NULL,'33333333-3333-3333-3333-333333333333',now(),now()) ON CONFLICT DO NOTHING;
INSERT INTO incident_activity(id,organization_id,incident_id,actor,message)
SELECT id,organization_id,id,created_by,'Incident opened: '||title FROM incidents
WHERE id IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002',
  'aaaaaaaa-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000004',
  'aaaaaaaa-0000-0000-0000-000000000005','aaaaaaaa-0000-0000-0000-000000000006',
  'bbbbbbbb-0000-0000-0000-000000000001') ON CONFLICT DO NOTHING;
INSERT INTO outbox(id,organization_id,payload)
SELECT id,organization_id,jsonb_build_object('eventId',id,'organizationId',organization_id,'incidentId',id,'message','Incident opened: '||title)
FROM incidents WHERE id IN ('aaaaaaaa-0000-0000-0000-000000000001','aaaaaaaa-0000-0000-0000-000000000002',
  'aaaaaaaa-0000-0000-0000-000000000003','aaaaaaaa-0000-0000-0000-000000000004',
  'aaaaaaaa-0000-0000-0000-000000000005','aaaaaaaa-0000-0000-0000-000000000006',
  'bbbbbbbb-0000-0000-0000-000000000001') ON CONFLICT DO NOTHING;
