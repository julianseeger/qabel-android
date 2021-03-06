package de.qabel.qabelbox.storage;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import de.qabel.qabelbox.QabelBoxApplication;
import de.qabel.qabelbox.communication.BlockServer;
import de.qabel.qabelbox.communication.callbacks.RequestCallback;
import okhttp3.Response;

public class BlockServerTransferManager implements TransferManager {

    private static final Logger logger = LoggerFactory.getLogger(BlockServerTransferManager.class.getName());
    private static final String TAG = "TransferManager";
    private final File tempDir;
    private final Map<Integer, CountDownLatch> latches;
    private final Map<Integer, Exception> errors;
    private final BlockServer blockServer;
    private final Context context;

    public BlockServerTransferManager(File tempDir) {
        this.tempDir = tempDir;
        latches = new ConcurrentHashMap<>();
        errors = new HashMap<>();

        context = QabelBoxApplication.getInstance().getApplicationContext();
        blockServer = new BlockServer(context);
    }

    @Override
    public File createTempFile() {

        try {
            return File.createTempFile("download", "", tempDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create tempfile");
        }
    }

    /**
     * uploadAndDeleteLocalfile localfile to server
     * For convience the localfile will be delete after the oparation is finished
     *
     * @param prefix              prefix from identity
     * @param name                localfile name with path
     * @param localfile           localfile to uploadAndDeleteLocalfile
     * @param boxTransferListener listener
     * @return new download id
     */
    @Override
    public int uploadAndDeleteLocalfileOnSuccess(String prefix, final String name, final File localfile, @Nullable final BoxTransferListener boxTransferListener) {

        Log.d(TAG, "uploadAndDeleteLocalfile " + prefix + " " + name + " " + localfile.toString());
        final int id = blockServer.getNextId();
        latches.put(id, new CountDownLatch(1));
        blockServer.uploadFile(context, prefix, name, localfile, new RequestCallback(new int[]{201, 204}) {

            @Override
            protected void onSuccess(int statusCode, Response response) {
                Log.d(TAG, "uploadAndDeleteLocalfile response " + response.code());
                if (boxTransferListener != null) {
                    boxTransferListener.onFinished();
                }
                Log.d(TAG, "delete localfile " + localfile.getName());
                localfile.delete();
                latches.get(id).countDown();
            }

            @Override
            protected void onError(Exception e, @Nullable Response response) {
                errors.put(id, e);
                Log.e(TAG, "error uploading file " + name, e);
                if (boxTransferListener != null) {
                    boxTransferListener.onFinished();
                }
                latches.get(id).countDown();
            }
        });

        return id;
    }

    @Override
    public Exception lookupError(int transferId) {
        return errors.get(transferId);
    }

    /**
     * download file from server
     *
     * @param prefix              prefix from identity
     * @param name                file name with directory
     * @param file                destination file
     * @param boxTransferListener listener
     * @return new download id
     */
    @Override
    public int download(String prefix, String name, final File file, @Nullable final BoxTransferListener boxTransferListener) {

        Log.d(TAG, "download " + prefix + " " + name + " " + file.toString());

        final int id = blockServer.getNextId();
        latches.put(id, new CountDownLatch(1));
        blockServer.downloadFile(context, prefix, name, new RequestCallback() {
            @Override
            public void onError(Exception e, @Nullable Response response) {
                if (boxTransferListener != null) {
                    boxTransferListener.onFinished();
                }
                errors.put(id, e);
                latches.get(id).countDown();
            }

            @Override
            public void onSuccess(int statusCode, Response response) {
                try {
                    readStreamFromServer(response, file, boxTransferListener);
                } catch (IOException e) {
                    Log.e(TAG, "Error reading stream from Server", e);
                }
                if (boxTransferListener != null) {
                    boxTransferListener.onFinished();
                }
                latches.get(id).countDown();
            }
        });

        return id;
    }

    /**
     * read stream from server
     *
     * @param response
     * @param file
     * @param boxTransferListener
     * @throws IOException
     */
    private void readStreamFromServer(Response response, File file, @Nullable BoxTransferListener boxTransferListener) throws IOException {

        InputStream is = response.body().byteStream();
        BufferedInputStream input = new BufferedInputStream(is);
        OutputStream output = new FileOutputStream(file);

        Log.d(TAG, "Server response received. Reading stream with unknown size");
        final byte[] data = new byte[1024];
        long total = 0;
        int count;
        while ((count = input.read(data)) != -1) {
            total += count;
            output.write(data, 0, count);
        }

        Log.d(TAG, "download filesize after: " + total);
        if (boxTransferListener != null) {
            boxTransferListener.onProgressChanged(total, total);
        }
        output.flush();
        output.close();
        input.close();
    }

    /**
     * wait until server request finished.
     *
     * @param id id (getted from up/downbload
     * @return true if no error occurs
     */
    @Override
    public boolean waitFor(int id) {

        logger.info("Waiting for " + id);
        try {
            latches.get(id).await();
            logger.info("Waiting for " + id + " finished");
            Exception e = errors.get(id);
            if (e != null) {
                logger.warn("Error found waiting for " + id, e);
            }
            return e == null;
        } catch (InterruptedException e) {
            return false;
        }
    }


    @Override
    public int delete(String prefix, String name) {
        Log.d(TAG, "delete " + prefix + " " + name);
        final int id = blockServer.getNextId();
        latches.put(id, new CountDownLatch(1));
        blockServer.deleteFile(context, prefix, name, new RequestCallback(new int[]{200, 204, 404}) {
            @Override
            public void onError(Exception e, @Nullable Response response) {
                latches.get(id).countDown();
                errors.put(id, e);
            }

            @Override
            protected void onSuccess(int statusCode, Response response) {
                Log.d(TAG, "delete response " + response.code());
                latches.get(id).countDown();
            }
        });
        return id;
    }

}
