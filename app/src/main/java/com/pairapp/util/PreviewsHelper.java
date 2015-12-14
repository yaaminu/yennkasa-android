package com.pairapp.util;

import android.text.TextUtils;

import com.pairapp.R;

import java.util.HashMap;
import java.util.Map;


public class PreviewsHelper {

    final static Map<String, Integer> previewsMap = new HashMap<>();

    public static int getPreview(String fileName) {
        String ext = FileUtils.getExtension(fileName);
        if (TextUtils.isEmpty(ext)) {
            return R.drawable.format_unkown;
        }
        Integer drawableRes = previewsMap.get(ext);
        if (drawableRes != null) {
            return drawableRes;
        }
        return R.drawable.format_unkown;
    }

    static {
        previewsMap.put("jpeg", R.drawable.format_pic);
        previewsMap.put("jpg", R.drawable.format_pic);
        previewsMap.put("png", R.drawable.format_pic);
        previewsMap.put("gif", R.drawable.format_pic);
        previewsMap.put("ppt", R.drawable.format_ppt);
        previewsMap.put("pptx", R.drawable.format_ppt);
        previewsMap.put("pdf", R.drawable.format_pdf);
        previewsMap.put("docx", R.drawable.format_word);
        previewsMap.put("doc", R.drawable.format_word);
        previewsMap.put("mp3", R.drawable.format_music);
        previewsMap.put("wav", R.drawable.format_music);
        previewsMap.put("amr", R.drawable.format_music);
        previewsMap.put("3gpp", R.drawable.format_music);
        previewsMap.put("3gp", R.drawable.format_music);
        previewsMap.put("mp4", R.drawable.format_vid);
        previewsMap.put("flv", R.drawable.format_vid);
        previewsMap.put("avi", R.drawable.format_vid);
        previewsMap.put("mpeg", R.drawable.format_vid);
        previewsMap.put("zip", R.drawable.format_zip);
        previewsMap.put("gzip", R.drawable.format_zip);
        previewsMap.put("gz", R.drawable.format_zip);
        previewsMap.put("rar", R.drawable.format_zip);
        previewsMap.put("txt", R.drawable.format_txt);
        previewsMap.put("apk", R.drawable.format_apk);
    }
}
