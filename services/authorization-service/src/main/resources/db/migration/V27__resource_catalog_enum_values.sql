-- flyway:executeInTransaction=false
-- PostgreSQL requires newly added enum values to be committed before a later migration uses them.
ALTER TYPE resource_type ADD VALUE IF NOT EXISTS 'FIELD';
ALTER TYPE lifecycle_status ADD VALUE IF NOT EXISTS 'DRAFT';
ALTER TYPE lifecycle_status ADD VALUE IF NOT EXISTS 'DEPRECATED';
ALTER TYPE lifecycle_status ADD VALUE IF NOT EXISTS 'DISABLED';
