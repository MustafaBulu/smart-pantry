package com.mustafabulu.smartpantry.core.util;

public final class NameFormatter {

    private NameFormatter() {
    }

    public static String capitalizeFirstLetter(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        int firstCodePoint = trimmed.codePointAt(0);
        int firstLength = Character.charCount(firstCodePoint);
        String first = new String(Character.toChars(Character.toUpperCase(firstCodePoint)));
        return first + trimmed.substring(firstLength);
    }
}
