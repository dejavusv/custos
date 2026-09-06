package com.custos.modules.transfer.client;

import java.io.File;

public interface RemoteTransferClient extends AutoCloseable {

    void connect() throws Exception;

    void disconnect();

    boolean isConnected();

    void uploadFile(File localFile, String remoteDirectory, String remoteFileName) throws Exception;

    boolean remoteFileExists(String remoteDirectory, String remoteFileName) throws Exception;

    long getRemoteFileSize(String remoteDirectory, String remoteFileName) throws Exception;

    @Override
    default void close() {
        disconnect();
    }
}
