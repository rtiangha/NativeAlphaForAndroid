package com.cylonid.nativealpha.util;


import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Utility {

    public static void setViewAndChildrenEnabled(View view, boolean enabled) {

        view.setClickable(enabled);
        if (enabled) {
            view.setAlpha(1.0f);
        }
        else {
            view.setAlpha(0.75f);
        }

        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                setViewAndChildrenEnabled(child, enabled);
            }
        }
    }



    public static void Assert(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static String getFileNameFromDownload(String url, String content_disposition, String mime_type) {
        String file_name = null;
        if (content_disposition != null && !content_disposition.equals("")) {
            Pattern pattern = Pattern.compile("filename\\*=UTF-8''([^;\\s]+)", Pattern.CASE_INSENSITIVE);
            Matcher m = pattern.matcher(content_disposition);
            if (m.find()) {
                try {
                    file_name = java.net.URLDecoder.decode(m.group(1), "UTF-8");
                } catch (Exception e) {
                    file_name = m.group(1);
                }
            } else {
                pattern = Pattern.compile("filename=\"?([^\";\r\n]+)\"?", Pattern.CASE_INSENSITIVE);
                m = pattern.matcher(content_disposition);
                if (m.find()) {
                    file_name = m.group(1).trim();
                }
            }
        }
        if (file_name == null) {
            file_name = URLUtil.guessFileName(url, content_disposition, mime_type);
        }

        return file_name;
    }

}
