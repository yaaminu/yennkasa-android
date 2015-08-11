package com.pair.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.util.LruCache;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.pair.pairapp.BuildConfig;
import com.squareup.picasso.Cache;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
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
        if (cachePath == null) {
            throw new IllegalArgumentException("cache path is null!");
        }
        File file = new File(cachePath);
        if (!file.isDirectory() && !file.mkdirs()) {
            Log.w(TAG, "failed to initialise file based cache, this may be because the cache dir passed is invalid");
        }
        return new Picasso.Builder(context)
                .indicatorsEnabled(BuildConfig.DEBUG)
                .loggingEnabled(BuildConfig.DEBUG)
                .memoryCache(new DiskCache(file)).build();
    }

    private static class DiskCache implements Cache {
        //all caches share this lru so that we don't leak memory
        final static LruCache<String, Bitmap> inMemoryCache;

        static LruCache<String, Bitmap> initCache() {
            int maxMemory = figureOutCacheSize();
            return new MyLRUCache(maxMemory);
        }

        static {
            inMemoryCache = initCache();
        }

        final File cacheDirectory;

        DiskCache(File cacheDirectory) {
            if (cacheDirectory == null || !cacheDirectory.isDirectory()) {
                Log.w(TAG, "passed an invalid file for disk cache ignoring");
                cacheDirectory = new File(""); //is this safe?
            }
            this.cacheDirectory = cacheDirectory;
            //re-calculate in memory cache size
            int currentSize = inMemoryCache.size(),
                    currentPossibleCacheSize = figureOutCacheSize();
            if (currentPossibleCacheSize - currentSize > 1024 * 2) {
                inMemoryCache.resize(currentPossibleCacheSize);
            }
        }

        @Override
        public Bitmap get(String s) {
            final String normalisedString = normalise(s);
            Log.d(TAG, "retrieving entry: " + normalisedString + " from cache[memory]");
            Bitmap cache = inMemoryCache.get(normalisedString);
            if (cache == null) {
                Log.d(TAG, "not in memory cache trying to retrieve from file system");
                File cacheFile = new File(cacheDirectory, normalisedString + ".jpeg");
                if (cacheFile.exists()) {
                    Log.i(TAG, cacheFile.getAbsolutePath());
                    try {
                        cache = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                    } catch (OutOfMemoryError e) { //device out of memory so we resize and return null
                        int cacheSize = figureOutCacheSize();
                        inMemoryCache.resize(cacheSize);
                        return null;
                    }
                }
            }
            if (cache == null) {
                Log.d(TAG, "entry: " + normalisedString + " not cached");
            }
            return cache;
        }

        private static int figureOutCacheSize() {
            return getFreeMemory() / 8;
        }

        private static int getFreeMemory() {
            return (int) (Runtime.getRuntime().freeMemory() / 1024);
        }

        private static boolean cacheAlmostFull() {
            return inMemoryCache.maxSize() - inMemoryCache.size() < 1024;
        }

        @Override
        public void set(final String s, Bitmap bitmap) {
            // TODO: 8/9/2015 schedule this to run async
            int currentSize = inMemoryCache.size(),
                    currentPossibleCacheSize = figureOutCacheSize();
            if (currentPossibleCacheSize - currentSize > 1024 * 2 && cacheAlmostFull()) { //2 MB
                Log.i(TAG, "cache almost full resizing before storing");
                inMemoryCache.resize(currentPossibleCacheSize);
            }
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
            return inMemoryCache.maxSize();
        }

        @Override
        public void clear() {
            inMemoryCache.evictAll();
            //run async if possible
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

        private static class MyLRUCache extends LruCache<String, Bitmap> {
            /**
             * @param maxSize for caches that do not override {@link #sizeOf}, this is
             *                the maximum number of entries in the cache. For all other caches,
             *                this is the maximum sum of the sizes of the entries in this cache.
             */
            public MyLRUCache(int maxSize) {
                super(maxSize);
            }

            @Override
            protected int sizeOf(String key, Bitmap value) {
                if (value == null) {
                    this.remove(key);
                    return 0;
                }
                int size = 1;
                size += key.getBytes().length; //is this important? are strings not shared on the heap?
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    //copied from the Bitmap#getByteCount. this was necessary as i was getting a lint error.
                    size += value.getRowBytes() * value.getHeight();
                } else {
                    size += value.getAllocationByteCount();
                }
                return size;
            }
        }
    }

    private static class Target implements com.squareup.picasso.Target {
        //weak reference since this target is likely to be used in adapters
        private final WeakReference<View> progressView;
        private final WeakReference<ImageView> target;

        public Target(View loadingProgress, ImageView target) {
            if (loadingProgress == null || target == null) {
                throw new IllegalArgumentException("either progress bar or imageview may not be null");
            }
            this.progressView = new WeakReference<>(loadingProgress);
            this.target = new WeakReference<>(target);
        }

        public Target(ImageView target) {
            if (target == null) {
                throw new IllegalArgumentException("image view cannot be null");
            }
            progressView = new WeakReference<>(null);
            this.target = new WeakReference<>(target);
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
            final ImageView imageView = this.target.get();
            final View progressView = this.progressView.get();
            if (imageView != null) {
                imageView.setImageBitmap(bitmap);
            }
            if (progressView != null) {
                progressView.setVisibility(View.GONE);
            }
        }

        @Override
        public void onBitmapFailed(Drawable drawable) {
            final ImageView imageView = this.target.get();
            final View progressView = this.progressView.get();
            if (imageView != null) {
                imageView.setImageDrawable(drawable);
            }
            if (progressView != null) {
                progressView.setVisibility(View.GONE);
            }
        }

        @Override
        public void onPrepareLoad(Drawable drawable) {
            final View progressView = this.progressView.get();
            final ImageView target = this.target.get();
            if (target != null) {
                target.setImageDrawable(drawable);
            }
            if (progressView != null) {
                progressView.setVisibility(View.VISIBLE);
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
