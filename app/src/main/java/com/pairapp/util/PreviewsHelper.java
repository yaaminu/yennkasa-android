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
            return R.drawable.ic_preview_file_unknown_format_200dp;
        }
        Integer drawableRes = previewsMap.get(ext);
        if (drawableRes != null) {
            return drawableRes;
        }
        return R.drawable.ic_preview_file_unknown_format_200dp;
    }

    static {
        previewsMap.put("jpeg", R.drawable.ic_preview_pic_200dp);
        previewsMap.put("jpg", R.drawable.ic_preview_pic_200dp);
        previewsMap.put("png", R.drawable.ic_preview_pic_200dp);
        previewsMap.put("gif", R.drawable.ic_preview_pic_200dp);
        previewsMap.put("ppt", R.drawable.ic_preview_presentation_24dp);
        previewsMap.put("pptx", R.drawable.ic_preview_presentation_24dp);
        previewsMap.put("pdf", R.drawable.ic_preview_pdf_black_200dp);
        previewsMap.put("docx", R.drawable.ic_preview_document_24dp);
        previewsMap.put("doc", R.drawable.ic_preview_document_24dp);
        previewsMap.put("mp3", R.drawable.ic_preview_audio_200dp);
        previewsMap.put("wav", R.drawable.ic_preview_audio_200dp);
        previewsMap.put("amr", R.drawable.ic_preview_audio_200dp);
        previewsMap.put("3gpp", R.drawable.ic_preview_audio_200dp);
        previewsMap.put("3gp", R.drawable.ic_preview_audio_200dp);
        previewsMap.put("mp4", R.drawable.ic_preveiw_video_200dp);
        previewsMap.put("flv", R.drawable.ic_preveiw_video_200dp);
        previewsMap.put("avi", R.drawable.ic_preveiw_video_200dp);
        previewsMap.put("mpeg", R.drawable.ic_preveiw_video_200dp);
        previewsMap.put("zip", R.drawable.ic_preview_file_unknown_format_200dp);
        previewsMap.put("gzip", R.drawable.ic_preview_file_unknown_format_200dp);
        previewsMap.put("gz", R.drawable.ic_preview_file_unknown_format_200dp);
        previewsMap.put("rar", R.drawable.ic_preview_file_unknown_format_200dp);
        previewsMap.put("txt", R.drawable.ic_preview_document_24dp);
        previewsMap.put("apk", R.drawable.ic_preview_apk_200dp);
    }
}
