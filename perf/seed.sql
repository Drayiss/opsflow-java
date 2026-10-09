INSERT INTO organizations(id,name) VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc','Benchmark workspace') ON CONFLICT DO NOTHING;
INSERT INTO memberships VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc','11111111-1111-1111-1111-111111111111','Alice Morgan','ADMIN') ON CONFLICT DO NOTHING;
INSERT INTO incidents(id,organization_id,title,description,severity,status,created_by,created_at,updated_at)
SELECT md5('opsflow-benchmark-'||n)::uuid,'cccccccc-cccc-cccc-cccc-cccccccccccc','Benchmark incident '||n,'Synthetic performance fixture',
  (ARRAY['SEV1','SEV2','SEV3','SEV4'])[1+(n%4)],(ARRAY['OPEN','ACKNOWLEDGED','RESOLVED'])[1+(n%3)],
  '11111111-1111-1111-1111-111111111111',now()-n*interval '1 second',now()
FROM generate_series(1,200000) n ON CONFLICT DO NOTHING;
ANALYZE incidents;
