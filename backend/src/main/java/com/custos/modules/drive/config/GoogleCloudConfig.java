package com.custos.modules.drive.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Slf4j
@Configuration
@Getter
public class GoogleCloudConfig {

    private final ResourceLoader resourceLoader;

    public GoogleCloudConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Value("${custos.gcp.enabled:true}")
    private boolean enabled;

    @Value("${custos.gcp.service-account-path:classpath:credentials/service-account.json}")
    private String serviceAccountPath;

    @Value("${custos.gcp.drive.default-folder-id:}")
    private String defaultFolderId;

    @Value("${custos.gcp.drive.application-name:Custos-Drive-Uploader}")
    private String applicationName;

    @Value("${custos.gcp.firestore.collection-name:file_upload_history}")
    private String collectionName;

    public InputStream getCredentialsInputStream() throws IOException {
        if (!enabled || serviceAccountPath == null || serviceAccountPath.trim().isEmpty()) {
            return null;
        }

        String path = serviceAccountPath.trim();

        // 1. If explicit classpath: or file: prefix
        if (path.startsWith("classpath:") || path.startsWith("file:")) {
            Resource res = resourceLoader.getResource(path);
            if (res.exists()) {
                return res.getInputStream();
            }
        }

        // 2. Direct filesystem check
        File directFile = new File(path);
        if (directFile.exists() && directFile.isFile()) {
            return new FileInputStream(directFile);
        }

        // 3. Fallback classpath resolution
        Resource classPathRes = resourceLoader.getResource("classpath:" + path);
        if (classPathRes.exists()) {
            return classPathRes.getInputStream();
        }

        return null;
    }

    @Bean
    public Drive googleDriveClient() {
        if (!enabled) {
            log.info("Google Cloud Drive is disabled via configuration (custos.gcp.enabled: false)");
            return null;
        }

        try {
            InputStream in = getCredentialsInputStream();
            if (in == null) {
                log.warn("GCP Service Account credentials not found at: {}. Google Drive client will be null.", serviceAccountPath);
                return null;
            }

            try (in) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                        .createScoped(Collections.singleton(DriveScopes.DRIVE_FILE));

                log.info("Initializing Google Drive client with application name: {}", applicationName);
                return new Drive.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        new HttpCredentialsAdapter(credentials))
                        .setApplicationName(applicationName)
                        .build();
            }
        } catch (IOException | GeneralSecurityException e) {
            log.error("Failed to initialize Google Drive Client: {}", e.getMessage(), e);
            return null;
        }
    }

    @Bean
    public Firestore firestoreClient() {
        if (!enabled) {
            log.info("Google Cloud Firestore is disabled via configuration (custos.gcp.enabled: false)");
            return null;
        }

        try {
            InputStream in = getCredentialsInputStream();
            if (in == null) {
                log.warn("GCP Service Account credentials not found at: {}. Firestore client will be null.", serviceAccountPath);
                return null;
            }

            if (FirebaseApp.getApps().isEmpty()) {
                try (in) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(in))
                            .build();
                    FirebaseApp.initializeApp(options);
                    log.info("FirebaseApp initialized successfully");
                }
            }
            return FirestoreClient.getFirestore();
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Firestore Client: {}", e.getMessage(), e);
            return null;
        }
    }
}
