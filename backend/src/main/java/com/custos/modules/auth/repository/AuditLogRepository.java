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

    Page<AuditLog> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    Page<AuditLog> findByActionContainingIgnoreCase(String action, Pageable pageable);

    Page<AuditLog> findByUsernameContainingIgnoreCaseAndActionContainingIgnoreCase(String username, String action, Pageable pageable);
}
