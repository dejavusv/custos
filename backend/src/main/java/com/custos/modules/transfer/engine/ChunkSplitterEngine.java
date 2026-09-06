package com.custos.modules.transfer.engine;

import com.custos.modules.transfer.model.ChunkMetadata;
import com.custos.modules.transfer.model.TransferManifest;
import com.custos.modules.transfer.service.ChecksumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkSplitterEngine {

    private static final int BUFFER_SIZE = 65536; // 64 KB streaming buffer
    public static final long DEFAULT_CHUNK_SIZE_BYTES = 50 * 1024 * 1024L; // 50 MB

    private final ChecksumService checksumService;

    /**
     * Splits a large file into sequential parts with Zero-RAM streaming.
     */
    public TransferManifest splitFile(File sourceFile, File outputDir, long chunkSizeBytes) {
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            throw new IllegalArgumentException("Source file does not exist: " + sourceFile);
        }

        long effectiveChunkSize = chunkSizeBytes > 0 ? chunkSizeBytes : DEFAULT_CHUNK_SIZE_BYTES;
        long totalFileSize = sourceFile.length();

        if (outputDir == null) {
            outputDir = sourceFile.getParentFile();
        }
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        int estimatedChunks = (int) Math.ceil((double) totalFileSize / effectiveChunkSize);
        if (estimatedChunks == 0) estimatedChunks = 1;

        List<ChunkMetadata> chunkList = new ArrayList<>();
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            MessageDigest originalDigest = MessageDigest.getInstance("SHA-256");

            try (InputStream in = new BufferedInputStream(new FileInputStream(sourceFile), BUFFER_SIZE);
                 DigestInputStream digestIn = new DigestInputStream(in, originalDigest)) {

                int partNumber = 1;
                boolean hasMoreBytes = true;

                while (hasMoreBytes) {
                    String partFileName = String.format("%s.part%02d", sourceFile.getName(), partNumber);
                    File chunkFile = new File(outputDir, partFileName);

                    MessageDigest chunkDigest = MessageDigest.getInstance("SHA-256");
                    long bytesWrittenToChunk = 0;

                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(chunkFile), BUFFER_SIZE);
                         DigestOutputStream digestOut = new DigestOutputStream(out, chunkDigest)) {

                        while (bytesWrittenToChunk < effectiveChunkSize) {
                            long bytesRemainingInChunk = effectiveChunkSize - bytesWrittenToChunk;
                            int bytesToRead = (int) Math.min(buffer.length, bytesRemainingInChunk);

                            int bytesRead = digestIn.read(buffer, 0, bytesToRead);
                            if (bytesRead == -1) {
                                hasMoreBytes = false;
                                break;
                            }

                            digestOut.write(buffer, 0, bytesRead);
                            bytesWrittenToChunk += bytesRead;
                        }
                        digestOut.flush();
                    }

                    if (bytesWrittenToChunk > 0) {
                        String chunkSha256 = HexFormat.of().formatHex(chunkDigest.digest());
                        chunkList.add(ChunkMetadata.builder()
                                .partNumber(partNumber)
                                .fileName(partFileName)
                                .filePath(chunkFile.getAbsolutePath())
                                .sizeBytes(bytesWrittenToChunk)
                                .checksumSha256(chunkSha256)
                                .status("PENDING")
                                .retries(0)
                                .build());

                        log.debug("Created chunk #{}: {} ({} bytes, SHA-256: {})",
                                partNumber, partFileName, bytesWrittenToChunk, chunkSha256);
                        partNumber++;
                    } else {
                        // Empty trailing chunk
                        chunkFile.delete();
                        break;
                    }
                }
            }

            String originalSha256 = HexFormat.of().formatHex(originalDigest.digest());

            log.info("Split {} ({} bytes) into {} chunks. Original SHA-256: {}",
                    sourceFile.getName(), totalFileSize, chunkList.size(), originalSha256);

            return TransferManifest.builder()
                    .originalFileName(sourceFile.getName())
                    .originalFilePath(sourceFile.getAbsolutePath())
                    .originalSizeBytes(totalFileSize)
                    .originalChecksumSha256(originalSha256)
                    .chunkSizeBytes(effectiveChunkSize)
                    .totalChunks(chunkList.size())
                    .chunks(chunkList)
                    .createdAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to split file {}: {}", sourceFile.getAbsolutePath(), e.getMessage(), e);
            throw new RuntimeException("Chunk split failed: " + e.getMessage(), e);
        }
    }

    /**
     * Merges sequentially ordered chunk files back into a single output file.
     */
    public File mergeChunks(List<File> chunkFiles, File destinationFile) {
        if (chunkFiles == null || chunkFiles.isEmpty()) {
            throw new IllegalArgumentException("Chunk files list cannot be empty");
        }

        // Sort chunks by name/part number to guarantee sequential reconstruction
        List<File> sortedChunks = new ArrayList<>(chunkFiles);
        sortedChunks.sort(Comparator.comparing(File::getName));

        if (destinationFile.getParentFile() != null && !destinationFile.getParentFile().exists()) {
            destinationFile.getParentFile().mkdirs();
        }

        byte[] buffer = new byte[BUFFER_SIZE];

        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(destinationFile), BUFFER_SIZE)) {
            for (File chunk : sortedChunks) {
                if (!chunk.exists()) {
                    throw new FileNotFoundException("Missing chunk part: " + chunk.getAbsolutePath());
                }

                try (InputStream in = new BufferedInputStream(new FileInputStream(chunk), BUFFER_SIZE)) {
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
            }
            out.flush();

            log.info("Merged {} chunks into destination file: {} ({} bytes)",
                    sortedChunks.size(), destinationFile.getAbsolutePath(), destinationFile.length());
            return destinationFile;

        } catch (Exception e) {
            if (destinationFile.exists()) {
                destinationFile.delete();
            }
            log.error("Failed to merge chunks: {}", e.getMessage(), e);
            throw new RuntimeException("Chunk merge failed: " + e.getMessage(), e);
        }
    }
}
