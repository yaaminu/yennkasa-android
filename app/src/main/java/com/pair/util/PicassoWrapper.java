package com.pair.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.util.LruCache;
import android.util.Log;

import com.pair.pairapp.BuildConfig;
import com.squareup.picasso.Cache;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * @author Null-Pointer on 7/17/2015.
 */
public class PicassoWrapper {
    private static final String TAG = PicassoWrapper.class.getSimpleName();
    private PicassoWrapper() {
        throw new UnsupportedOperationException("cannot instantiate");
    }

    public static Picasso with(Context context) {
        Log.w(TAG, "calling with without any cache file, using cache dir");
        return with(context, context.getCacheDir().getAbsolutePath());
    }

    public static Picasso with(Context context, String cachePath) {
        Picasso.Builder builder = new Picasso.Builder(context)
                .indicatorsEnabled(BuildConfig.DEBUG);
        File file = new File(cachePath);
        if (!file.isDirectory() && !file.mkdirs()) {
            Log.w(TAG, "failed to initialise file based cache");
            return builder.build();
        }
        return builder.memoryCache(new DiskCache(file)).build();
    }

    private static class DiskCache implements Cache {
        final static LruCache<String, Bitmap> inMemoryCache;

        static LruCache<String, Bitmap> initCache() {
            int maxMemory = ((int) (Runtime.getRuntime().maxMemory() / 1024));
            return new LruCache<>(maxMemory / 8);
        }

        static {
            inMemoryCache = initCache();
        }

        final File cacheDirectory;
        DiskCache(File cacheDirectory) {
            if (cacheDirectory == null || !cacheDirectory.isDirectory()) {
                throw new IllegalArgumentException("cache directory is invalid");
            }
            this.cacheDirectory = cacheDirectory;
        }

        @Override
        public Bitmap get(String s) {
            final String normalisedString = normalise(s);
            Log.d(TAG, "retrieving entry: " + normalisedString + " from cache");
            Bitmap cache = inMemoryCache.get(normalisedString);
            if (cache == null) {
                File cacheFile = new File(cacheDirectory, normalisedString + ".jpeg");
                if (cacheFile.exists()) {
                    cache = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                }
            }
            return cache;
        }

        @Override
        public void set(final String s, Bitmap bitmap) {
            final String normalisedString = normalise(s);
            inMemoryCache.put(normalisedString, bitmap);
            FileOutputStream out = null;
            final File cache = new File(cacheDirectory, normalisedString + ".jpeg");
            if (cache.exists()) { //file already exists don't save again
                Log.i(TAG, "cache already exists don't overwrite");
                return;
            }
            try {
                out = new FileOutputStream(cache);
                //noinspection ConstantConditions
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)) {
                    Log.i(TAG, "saved bitmap successfully");
                } else {
                    Log.i(TAG, "failed to save bitmap");
                }
            } catch (FileNotFoundException e) {
                Log.i(TAG, "file not found");
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, e.getMessage(), e.getCause());
                } else {
                    Log.e(TAG, e.getMessage());
                }
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {

                    }
                }
            }
        }

        @Override
        public int size() {
            return inMemoryCache.size();
        }

        @Override
        public int maxSize() {
            return 10000;
        }

        @Override
        public void clear() {
            inMemoryCache.evictAll();
            File[] files = cacheDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }

        @Override
        public void clearKeyUri(String s) {
            inMemoryCache.remove(normalise(s));
            File file = new File(cacheDirectory, normalise(s) + ".jpeg");
            file.delete();
        }

        String normalise(String uri) {
            try {
                String normalised = URLEncoder.encode(uri, "utf8");
                Log.i(TAG, normalised + " retrieved from uri");
                return normalised;
            } catch (UnsupportedEncodingException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, e.getMessage(), e.getCause());
                } else {
                    Log.e(TAG, e.getMessage());
                }
                Log.wtf(TAG, "failed to normalise uri, returning it like that");
                return uri;
            }
        }

        String deNormalise(String uri) {
            try {
                String deNormalised = URLDecoder.decode(uri, "utf8");
                Log.i(TAG, deNormalised + " retrieved from uri");
                return deNormalised;
            } catch (UnsupportedEncodingException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, e.getMessage(), e.getCause());
                } else {
                    Log.e(TAG, e.getMessage());
                }
                Log.wtf(TAG, "failed to deNormalise uri, returning it like that");
                return uri;
            }
        }
    }
}
