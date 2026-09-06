package com.custos.modules.backup.database;

import com.custos.modules.backup.dto.DatabaseBackupRequest;
import com.custos.modules.backup.model.BackupResult;

import java.io.File;
import java.util.List;

public interface DatabaseBackupEngine {

    /**
     * Executes a native database dump with Zero-RAM compression streaming.
     */
    BackupResult executeBackup(DatabaseBackupRequest request, File targetFile);

    /**
     * Generates sanitized command line argument list for testing or execution.
     */
    List<String> buildCommandList(DatabaseBackupRequest request);
}
