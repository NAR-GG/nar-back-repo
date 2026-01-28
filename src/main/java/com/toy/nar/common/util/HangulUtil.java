package com.toy.nar.common.util;

public class HangulUtil {

    // 초성 리스트
    private static final char[] CHOSUNG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    /**
     * 문자열의 초성을 추출합니다.
     * 예: "한화생명" -> "ㅎㅎㅅㅁ"
     * 예: "Gen.G" -> "Gen.G" (한글 아니면 그대로)
     */
    public static String extractChosung(String text) {
        if (text == null)
            return "";

        StringBuilder sb = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (ch >= 0xAC00 && ch <= 0xD7A3) { // 한글 범위
                int uniVal = ch - 0xAC00;
                int chosungIndex = ((uniVal - (uniVal % 28)) / 28) / 21;
                sb.append(CHOSUNG[chosungIndex]);
            } else {
                // 한글이 아니면 그대로(공백 포함)
                if (Character.isLetterOrDigit(ch) || Character.isWhitespace(ch)) {
                    sb.append(ch);
                }
            }
        }
        return sb.toString();
    }
}
