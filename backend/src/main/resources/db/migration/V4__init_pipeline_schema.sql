-- V4: ตารางสำหรับโมดูล Pipeline Orchestration และ Quartz Scheduler

-- 1. แม่แบบคำสั่งงาน (Task Definitions)
CREATE TABLE IF NOT EXISTS task_definitions (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    task_type VARCHAR(50) NOT NULL,
    config_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_task_definitions_type ON task_definitions(task_type);

-- 2. นิยามเวิร์กโฟลว์ (Pipeline Definitions)
CREATE TABLE IF NOT EXISTS pipeline_definitions (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    cron_expression VARCHAR(100),
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    misfire_policy VARCHAR(50) NOT NULL DEFAULT 'SMART_POLICY',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_pipeline_definitions_active ON pipeline_definitions(is_active);

-- 3. โหนดขั้นตอนงานใน DAG (Pipeline Step Nodes)
CREATE TABLE IF NOT EXISTS pipeline_step_nodes (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL REFERENCES pipeline_definitions(id) ON DELETE CASCADE,
    task_id UUID REFERENCES task_definitions(id) ON DELETE SET NULL,
    node_key VARCHAR(100) NOT NULL,
    node_label VARCHAR(150) NOT NULL,
    node_type VARCHAR(50) NOT NULL,
    step_order INT NOT NULL DEFAULT 0,
    position_x DOUBLE PRECISION NOT NULL DEFAULT 0,
    position_y DOUBLE PRECISION NOT NULL DEFAULT 0,
    config_override_json TEXT DEFAULT '{}',
    on_success_node_id UUID REFERENCES pipeline_step_nodes(id) ON DELETE SET NULL,
    on_failure_node_id UUID REFERENCES pipeline_step_nodes(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_step_nodes_pipeline ON pipeline_step_nodes(pipeline_id);

-- 4. ประวัติรอบการรันเวิร์กโฟลว์ (Pipeline Executions)
CREATE TABLE IF NOT EXISTS pipeline_executions (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL REFERENCES pipeline_definitions(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    duration_ms BIGINT,
    error_message TEXT,
    context_data_json TEXT DEFAULT '{}',
    triggered_by VARCHAR(100),
    trigger_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pipeline_executions_status ON pipeline_executions(status);
CREATE INDEX IF NOT EXISTS idx_pipeline_executions_pipe ON pipeline_executions(pipeline_id);

-- 5. บันทึกผลลัพธ์และ Log ราย Step (Step Execution Logs)
CREATE TABLE IF NOT EXISTS step_execution_logs (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES pipeline_executions(id) ON DELETE CASCADE,
    step_node_id UUID REFERENCES pipeline_step_nodes(id) ON DELETE SET NULL,
    step_name VARCHAR(150) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    duration_ms BIGINT,
    logs_text TEXT,
    file_size_bytes BIGINT,
    output_path VARCHAR(1000),
    checksum_sha256 VARCHAR(64),
    aws_ses_message_id VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_step_execution_logs_exec ON step_execution_logs(execution_id);

-- 6. ตารางมาตรฐานสำหรับ Quartz Scheduler (PostgreSQL JobStore)
CREATE TABLE IF NOT EXISTS qrtz_job_details (
    sched_name VARCHAR(120) NOT NULL,
    job_name VARCHAR(200) NOT NULL,
    job_group VARCHAR(200) NOT NULL,
    description VARCHAR(250) NULL,
    job_class_name VARCHAR(250) NOT NULL,
    is_durable BOOL NOT NULL,
    is_nonconcurrent BOOL NOT NULL,
    is_update_data BOOL NOT NULL,
    requests_recovery BOOL NOT NULL,
    job_data BYTEA NULL,
    PRIMARY KEY (sched_name, job_name, job_group)
);

CREATE TABLE IF NOT EXISTS qrtz_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    job_name VARCHAR(200) NOT NULL,
    job_group VARCHAR(200) NOT NULL,
    description VARCHAR(250) NULL,
    next_fire_time BIGINT NULL,
    prev_fire_time BIGINT NULL,
    priority INTEGER NULL,
    trigger_state VARCHAR(16) NOT NULL,
    trigger_type VARCHAR(8) NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NULL,
    calendar_name VARCHAR(200) NULL,
    misfire_instr SMALLINT NULL,
    job_data BYTEA NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, job_name, job_group) REFERENCES qrtz_job_details(sched_name, job_name, job_group)
);

CREATE TABLE IF NOT EXISTS qrtz_simple_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    repeat_count BIGINT NOT NULL,
    repeat_interval BIGINT NOT NULL,
    times_triggered BIGINT NOT NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS qrtz_cron_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    time_zone_id VARCHAR(80),
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS qrtz_simprop_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    str_prop_1 VARCHAR(512) NULL,
    str_prop_2 VARCHAR(512) NULL,
    str_prop_3 VARCHAR(512) NULL,
    int_prop_1 INT NULL,
    int_prop_2 INT NULL,
    long_prop_1 BIGINT NULL,
    long_prop_2 BIGINT NULL,
    dec_prop_1 NUMERIC(13,4) NULL,
    dec_prop_2 NUMERIC(13,4) NULL,
    bool_prop_1 BOOL NULL,
    bool_prop_2 BOOL NULL,
    time_zone_id VARCHAR(80) NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS qrtz_blob_triggers (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    blob_data BYTEA NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES qrtz_triggers(sched_name, trigger_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS qrtz_calendars (
    sched_name VARCHAR(120) NOT NULL,
    calendar_name VARCHAR(200) NOT NULL,
    calendar BYTEA NOT NULL,
    PRIMARY KEY (sched_name, calendar_name)
);

CREATE TABLE IF NOT EXISTS qrtz_paused_trigger_grps (
    sched_name VARCHAR(120) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    PRIMARY KEY (sched_name, trigger_group)
);

CREATE TABLE IF NOT EXISTS qrtz_fired_triggers (
    sched_name VARCHAR(120) NOT NULL,
    entry_id VARCHAR(140) NOT NULL,
    trigger_name VARCHAR(200) NOT NULL,
    trigger_group VARCHAR(200) NOT NULL,
    instance_name VARCHAR(200) NOT NULL,
    fired_time BIGINT NOT NULL,
    sched_time BIGINT NOT NULL,
    priority INTEGER NOT NULL,
    state VARCHAR(16) NOT NULL,
    job_name VARCHAR(200) NULL,
    job_group VARCHAR(200) NULL,
    is_nonconcurrent BOOL NULL,
    requests_recovery BOOL NULL,
    PRIMARY KEY (sched_name, entry_id)
);

CREATE TABLE IF NOT EXISTS qrtz_scheduler_state (
    sched_name VARCHAR(120) NOT NULL,
    instance_name VARCHAR(200) NOT NULL,
    last_checkin_time BIGINT NOT NULL,
    checkin_interval BIGINT NOT NULL,
    PRIMARY KEY (sched_name, instance_name)
);

CREATE TABLE IF NOT EXISTS qrtz_locks (
    sched_name VARCHAR(120) NOT NULL,
    lock_name VARCHAR(40) NOT NULL,
    PRIMARY KEY (sched_name, lock_name)
);
