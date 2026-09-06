package com.custos.modules.transfer.client;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Slf4j
public class SftpTransferClient implements RemoteTransferClient {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String privateKey;
    private final String passphrase;

    private JSch jsch;
    private Session session;
    private ChannelSftp channelSftp;

    public SftpTransferClient(String host, int port, String username, String password, String privateKey, String passphrase) {
        this.host = host;
        this.port = port > 0 ? port : 22;
        this.username = username;
        this.password = password;
        this.privateKey = privateKey;
        this.passphrase = passphrase;
    }

    @Override
    public void connect() throws Exception {
        jsch = new JSch();

        // If SSH private key provided, add identity
        if (privateKey != null && !privateKey.trim().isEmpty()) {
            byte[] keyBytes = privateKey.trim().getBytes(StandardCharsets.UTF_8);
            byte[] passBytes = (passphrase != null && !passphrase.isEmpty()) ? passphrase.getBytes(StandardCharsets.UTF_8) : null;
            jsch.addIdentity("sftp-identity", keyBytes, null, passBytes);
            log.info("Added SSH private key identity for SFTP user: {}", username);
        }

        log.info("Connecting to SFTP server {}:{} as user {}", host, port, username);
        session = jsch.getSession(username, host, port);

        if (password != null && !password.isEmpty()) {
            session.setPassword(password);
        }

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.setTimeout(30000);
        session.connect(15000);

        Channel channel = session.openChannel("sftp");
        channel.connect(15000);
        channelSftp = (ChannelSftp) channel;

        log.info("SFTP connection and channel established successfully");
    }

    @Override
    public void disconnect() {
        if (channelSftp != null && channelSftp.isConnected()) {
            try {
                channelSftp.disconnect();
            } catch (Exception ignored) {}
        }
        if (session != null && session.isConnected()) {
            try {
                session.disconnect();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean isConnected() {
        return channelSftp != null && channelSftp.isConnected() && session != null && session.isConnected();
    }

    @Override
    public void uploadFile(File localFile, String remoteDirectory, String remoteFileName) throws Exception {
        if (!isConnected()) {
            connect();
        }

        if (remoteDirectory != null && !remoteDirectory.trim().isEmpty()) {
            ensureRemoteDirectoryExists(remoteDirectory);
            channelSftp.cd(remoteDirectory);
        }

        try (InputStream in = new BufferedInputStream(new FileInputStream(localFile), 65536)) {
            channelSftp.put(in, remoteFileName, ChannelSftp.OVERWRITE);
        }
    }

    @Override
    public boolean remoteFileExists(String remoteDirectory, String remoteFileName) throws Exception {
        if (!isConnected()) {
            connect();
        }
        String path = (remoteDirectory != null && !remoteDirectory.isEmpty()) ? remoteDirectory + "/" + remoteFileName : remoteFileName;
        try {
            SftpATTRS attrs = channelSftp.stat(path);
            return attrs != null;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public long getRemoteFileSize(String remoteDirectory, String remoteFileName) throws Exception {
        if (!isConnected()) {
            connect();
        }
        String path = (remoteDirectory != null && !remoteDirectory.isEmpty()) ? remoteDirectory + "/" + remoteFileName : remoteFileName;
        try {
            SftpATTRS attrs = channelSftp.stat(path);
            return attrs.getSize();
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return -1;
            }
            throw e;
        }
    }

    private void ensureRemoteDirectoryExists(String path) {
        String[] folders = path.split("[/\\\\]");
        for (String folder : folders) {
            if (!folder.isEmpty()) {
                try {
                    channelSftp.cd(folder);
                } catch (SftpException e) {
                    try {
                        channelSftp.mkdir(folder);
                        channelSftp.cd(folder);
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
