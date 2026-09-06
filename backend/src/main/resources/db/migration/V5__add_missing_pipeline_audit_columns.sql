-- V5: เพิ่มคอลัมน์ Audit (updated_at, created_by) ให้กับตารางในโมดูล Pipeline เพื่อให้สอดคล้องกับ BaseEntity

ALTER TABLE pipeline_step_nodes
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);

ALTER TABLE pipeline_executions
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);

ALTER TABLE step_execution_logs
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
