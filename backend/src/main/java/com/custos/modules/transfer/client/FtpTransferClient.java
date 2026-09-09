package com.custos.modules.transfer.client;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.util.TrustManagerUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@Slf4j
public class FtpTransferClient implements RemoteTransferClient {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean isFtps;
    private final boolean trustSelfSigned;
    private FTPClient ftpClient;

    public FtpTransferClient(String host, int port, String username, String password, boolean isFtps) {
        this(host, port, username, password, isFtps, true);
    }

    public FtpTransferClient(String host, int port, String username, String password, boolean isFtps, boolean trustSelfSigned) {
        this.host = host;
        this.port = port > 0 ? port : 21;
        this.username = username;
        this.password = password;
        this.isFtps = isFtps;
        this.trustSelfSigned = trustSelfSigned;
    }

    public boolean isFtps() {
        return isFtps;
    }

    @Override
    public void connect() throws Exception {
        if (isFtps) {
            // Explicit TLS encryption (default port 21, sends AUTH TLS before authentication)
            FTPSClient ftpsClient = new FTPSClient("TLS", false);
            if (trustSelfSigned) {
                ftpsClient.setTrustManager(TrustManagerUtils.getAcceptAllTrustManager());
            }
            ftpClient = ftpsClient;
        } else {
            ftpClient = new FTPClient();
        }

        ftpClient.setConnectTimeout(15000);
        ftpClient.setDefaultTimeout(30000);

        log.info("Connecting to FTP server {}:{} (FTPS Explicit TLS: {})", host, port, isFtps);
        ftpClient.connect(host, port);

        int reply = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            disconnect();
            throw new RuntimeException("FTP server refused connection. Reply code: " + reply);
        }

        if (username != null && !username.isEmpty()) {
            boolean loggedIn = ftpClient.login(username, password);
            if (!loggedIn) {
                disconnect();
                throw new RuntimeException("FTP authentication failed for user: " + username);
            }
        }

        // RFC 4217: Protect data channel with TLS encryption (PROT P)
        if (isFtps && ftpClient instanceof FTPSClient ftpsClient) {
            ftpsClient.execPBSZ(0);
            ftpsClient.execPROT("P");
            log.info("FTPS data channel protection set to Private (PROT P)");
        }

        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        ftpClient.setBufferSize(65536);
        log.info("FTP connection established successfully");
    }

    @Override
    public void disconnect() {
        if (ftpClient != null && ftpClient.isConnected()) {
            try {
                ftpClient.logout();
            } catch (Exception ignored) {}
            try {
                ftpClient.disconnect();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean isConnected() {
        return ftpClient != null && ftpClient.isConnected();
    }

    @Override
    public void uploadFile(File localFile, String remoteDirectory, String remoteFileName) throws Exception {
        if (!isConnected()) {
            connect();
        }

        String initialWorkingDir = null;
        try {
            initialWorkingDir = ftpClient.printWorkingDirectory();
        } catch (Exception ignored) {}

        try {
            if (remoteDirectory != null && !remoteDirectory.trim().isEmpty()) {
                ensureRemoteDirectoryExists(remoteDirectory);
                ftpClient.changeWorkingDirectory(remoteDirectory);
            }

            try (InputStream in = new BufferedInputStream(new FileInputStream(localFile), 65536)) {
                boolean done = ftpClient.storeFile(remoteFileName, in);
                if (!done) {
                    throw new RuntimeException("FTP upload failed for " + remoteFileName + " (Reply: " + ftpClient.getReplyString() + ")");
                }
            }
        } finally {
            if (initialWorkingDir != null) {
                try {
                    ftpClient.changeWorkingDirectory(initialWorkingDir);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public boolean remoteFileExists(String remoteDirectory, String remoteFileName) throws Exception {
        if (!isConnected()) {
            connect();
        }
        String path = (remoteDirectory != null && !remoteDirectory.isEmpty()) ? remoteDirectory + "/" + remoteFileName : remoteFileName;
        FTPFile[] files = ftpClient.listFiles(path);
        return files != null && files.length > 0;
    }

    @Override
    public long getRemoteFileSize(String remoteDirectory, String remoteFileName) throws Exception {
        if (!isConnected()) {
            connect();
        }
        String path = (remoteDirectory != null && !remoteDirectory.isEmpty()) ? remoteDirectory + "/" + remoteFileName : remoteFileName;
        FTPFile[] files = ftpClient.listFiles(path);
        if (files != null && files.length > 0) {
            return files[0].getSize();
        }
        return -1;
    }

    private void ensureRemoteDirectoryExists(String path) {
        try {
            if (path.startsWith("/") || path.startsWith("\\")) {
                ftpClient.changeWorkingDirectory("/");
            }
            String[] dirs = path.split("[/\\\\]");
            for (String dir : dirs) {
                if (!dir.isEmpty()) {
                    if (!ftpClient.changeWorkingDirectory(dir)) {
                        ftpClient.makeDirectory(dir);
                        ftpClient.changeWorkingDirectory(dir);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("FTP makeDirectory error: {}", e.getMessage());
        }
    }
}
