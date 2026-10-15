package com.example.elsa_store.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** So khớp không phân biệt dấu tiếng Việt: "giay the thao" khớp "Giày Thể Thao", "den" khớp "đen". */
public final class TextNormalizer {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private TextNormalizer() {}

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD);
        String plain = MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
        return SPACES.matcher(plain).replaceAll(" ").strip();
    }

    /** Một trong hai chuỗi chứa chuỗi kia khi bỏ dấu và hoa thường. */
    public static boolean looselyMatches(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        return !x.isEmpty() && !y.isEmpty() && (x.contains(y) || y.contains(x));
    }
}
