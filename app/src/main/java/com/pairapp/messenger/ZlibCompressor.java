package com.pairapp.messenger;

import android.support.annotation.NonNull;

import com.pairapp.util.PLog;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * @author aminu on 11/18/2016.
 */
public class ZlibCompressor implements MessagePacker.Compressor {
    private static final String TAG = "ZlibCompressor";

    @NonNull
    @Override
    public byte[] compress(@NonNull byte[] data) {
        PLog.d(TAG, "before compressing size: %d bytes", data.length);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            deflater.finish();
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer); // returns the generated code... index
                outputStream.write(buffer, 0, count);
            }
            byte[] out = outputStream.toByteArray();
            PLog.d(TAG, "after compressing size: %d bytes", out.length);
            PLog.d(TAG, "saved %d bytes", data.length - out.length);
            return out;
        } finally {
            deflater.end();
        }
    }

    @NonNull
    @Override
    public byte[] decompress(@NonNull byte[] data) throws DataFormatException {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(data);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            return outputStream.toByteArray();
        } finally {
            inflater.end();
        }
    }
}
