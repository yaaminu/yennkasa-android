package com.pairapp.util;

import android.content.Context;
import android.graphics.Typeface;


import java.util.HashMap;

/**
 * Created by Null-Pointer on 9/27/2015.
 */
public class TypeFaceUtil {

    public static final String PREFIX_ASSET = "asset:";
    public static final String ROBOTO_REGULAR_TTF = "Roboto-Regular.ttf";
    public static final String ROBOTO_LIGHT_TTF = "Roboto-Light.ttf";
    public final static String ROBOTO_BOLD_TTF = "Roboto-Bold.ttf";
    public final static String two_d_font = "2Dumb.ttf";

    private TypeFaceUtil() {
    }

    static Typeface loadFromAssets(String fontName) {
        return load(Config.getApplicationContext(), PREFIX_ASSET + fontName, Typeface.NORMAL);
    }

    @SuppressWarnings("unused")
    static Typeface loadFromAssets(String fontName, int style) {
        return load(Config.getApplicationContext(), PREFIX_ASSET + fontName, style);
    }

    private static final HashMap<String, Typeface> sCachedFonts = new HashMap<>();


    /**
     * @param familyName if start with 'asset:' prefix, then load font from asset folder.
     * @return the typeface with that particular name
     */
    private static Typeface load(Context context, String familyName, int style) {
        if (familyName != null && familyName.startsWith(PREFIX_ASSET))
            synchronized (sCachedFonts) {
                try {
                    if (!sCachedFonts.containsKey(familyName)) {
                        final Typeface typeface = Typeface.createFromAsset(context.getAssets(), familyName.substring(PREFIX_ASSET.length()));
                        sCachedFonts.put(familyName, typeface);
                        return typeface;
                    }
                } catch (Exception e) {
                    return Typeface.DEFAULT;
                }

                return sCachedFonts.get(familyName);
            }

        return Typeface.create(familyName, style);
    }
}
