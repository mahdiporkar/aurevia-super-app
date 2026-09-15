-- Kept as an isolated migration because PostgreSQL enum values must be committed before use.
ALTER TYPE subject_type ADD VALUE IF NOT EXISTS 'ACCESS_GROUP';
