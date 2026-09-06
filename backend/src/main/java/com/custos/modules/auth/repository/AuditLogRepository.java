package com.custos.modules.auth.repository;

import com.custos.modules.auth.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:username IS NULL OR LOWER(a.username) LIKE LOWER(CONCAT('%', :username, '%'))) AND " +
           "(:action IS NULL OR LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%')))")
    Page<AuditLog> searchLogs(@Param("username") String username, @Param("action") String action, Pageable pageable);
}
