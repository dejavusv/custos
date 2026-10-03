package com.custos.modules.pipeline.entity;

public enum TaskType {
    DATABASE_BACKUP,
    FILE_BACKUP,
    SPLIT_TRANSFER,
    EMAIL_ALERT,
    GOOGLE_DRIVE_UPLOAD,
    LINE_NOTIFY,
    /** Control node: entry point of a pipeline (at most one per pipeline). */
    START,
    /** Control node: terminates a branch of the pipeline. */
    STOP
}
