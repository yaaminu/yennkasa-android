package com.pair.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.util.LruCache;
import android.util.Base64;
import android.util.Log;

import com.pair.pairapp.BuildConfig;
import com.squareup.picasso.Cache;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * @author Null-Pointer on 7/17/2015.
 */
public class PicassoWrapper {
    private static final String TAG = PicassoWrapper.class.getSimpleName();

    private PicassoWrapper() {
        throw new UnsupportedOperationException("cannot instantiate " + PicassoWrapper.class.getSimpleName());
    }

    public static Picasso with(Context context) {
        Log.w(TAG, "calling with() without any cache dir, using default cache dir");
        return with(context, context.getCacheDir().getAbsolutePath());
    }

    public static Picasso with(Context context, String cachePath) {
        Picasso.Builder builder = new Picasso.Builder(context)
                .indicatorsEnabled(BuildConfig.DEBUG);
        File file = new File(cachePath);
        if (!file.isDirectory() && !file.mkdirs()) {
            Log.w(TAG, "failed to initialise file based cache, this may be because the cache dir passed is invalid");
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
                Log.d(TAG, "not in memory cache trying to retrieve from file system");
                File cacheFile = new File(cacheDirectory, normalisedString + ".jpeg");
                if (cacheFile.exists()) {
                    Log.i(TAG, cacheFile.getAbsolutePath());
                    cache = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                }
            }
            if (cache == null) {
                Log.d(TAG, "entry: " + normalisedString + " not cached");
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
                closeQuitely(out);
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

        Pattern normaliser = Pattern.compile("(=|\\s)+");

        String normalise(String uri) {
            try {
                try {
                    String shortened = Base64.encodeToString(MessageDigest.getInstance("md5").digest(uri.getBytes()), Base64.URL_SAFE);
                    //noinspection ConstantConditions
                    shortened = normaliser.matcher(shortened).replaceAll("_");
                    Log.i(TAG, "shortened: " + shortened);
                    return shortened;
                } catch (NoSuchAlgorithmException e) {
                    Log.e(TAG, e.getMessage(), e.getCause());
                    return URLEncoder.encode(uri, "utf8");
                }
            } catch (UnsupportedEncodingException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, e.getMessage(), e.getCause());
                } else {
                    Log.e(TAG, e.getMessage());
                }
                Log.wtf(TAG, "failed to encode uri, returning it like that");
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

    private static void closeQuitely(OutputStream out) {
        if (out != null) {
            //noinspection EmptyCatchBlock
            try {
                out.close();
            } catch (IOException e) {
            }
        }
    }
}
