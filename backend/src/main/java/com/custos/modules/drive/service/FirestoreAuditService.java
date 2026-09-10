package com.custos.modules.drive.service;

import com.custos.modules.drive.config.GoogleCloudConfig;
import com.custos.modules.drive.model.FileUploadAuditRecord;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class FirestoreAuditService {

    private final Firestore firestoreClient;
    private final GoogleCloudConfig googleCloudConfig;

    @Autowired
    public FirestoreAuditService(
            @Autowired(required = false) Firestore firestoreClient,
            GoogleCloudConfig googleCloudConfig
    ) {
        this.firestoreClient = firestoreClient;
        this.googleCloudConfig = googleCloudConfig;
    }

    /**
     * Records an upload transaction log into Firebase Cloud Firestore.
     */
    public FileUploadAuditRecord recordUploadLog(FileUploadAuditRecord record) throws ExecutionException, InterruptedException {
        if (firestoreClient == null) {
            throw new IllegalStateException("Firestore client is not initialized. Please verify GCP credentials.");
        }

        String collectionName = googleCloudConfig.getCollectionName();
        CollectionReference collection = firestoreClient.collection(collectionName);

        String docId = (record.getId() != null && !record.getId().isBlank())
                ? record.getId()
                : UUID.randomUUID().toString();
        record.setId(docId);

        String now = Instant.now().toString();
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(now);
        }
        record.setUpdatedAt(now);

        DocumentReference docRef = collection.document(docId);
        log.info("Recording file upload audit log to Firestore collection '{}' with ID: {}", collectionName, docId);

        // Blocking write to confirm write success
        docRef.set(record).get();

        return record;
    }

    /**
     * Queries file upload audit logs from Firestore with optional filters.
     */
    public List<FileUploadAuditRecord> queryAuditLogs(String systemSource, String status, Integer limit) {
        if (firestoreClient == null) {
            log.warn("Firestore client is null. Returning empty audit list.");
            return List.of();
        }

        String collectionName = googleCloudConfig.getCollectionName();
        CollectionReference collection = firestoreClient.collection(collectionName);

        Query query = collection;

        if (systemSource != null && !systemSource.trim().isEmpty()) {
            query = query.whereEqualTo("systemSource", systemSource.trim());
        }

        if (status != null && !status.trim().isEmpty()) {
            query = query.whereEqualTo("status", status.trim().toUpperCase());
        }

        int maxResults = (limit != null && limit > 0) ? Math.min(limit, 100) : 50;
        query = query.orderBy("createdAt", Query.Direction.DESCENDING).limit(maxResults);

        try {
            ApiFuture<QuerySnapshot> future = query.get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            List<FileUploadAuditRecord> results = new ArrayList<>(documents.size());

            for (QueryDocumentSnapshot document : documents) {
                FileUploadAuditRecord record = document.toObject(FileUploadAuditRecord.class);
                if (record.getId() == null) {
                    record.setId(document.getId());
                }
                results.add(record);
            }
            return results;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to query Firestore audit logs: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to query Firestore audit logs: " + e.getMessage(), e);
        }
    }
}
