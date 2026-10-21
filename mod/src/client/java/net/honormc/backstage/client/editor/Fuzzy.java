package net.honormc.backstage.client.editor;

import java.util.Locale;

public final class Fuzzy {
    public static final int NO_MATCH = Integer.MIN_VALUE;

    private Fuzzy() {
    }

    public static int score(String query, String text) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return 0;
        }
        String t = text.toLowerCase(Locale.ROOT);
        int index = t.indexOf(q);
        if (index >= 0) {
            return 1000 - index * 10 - (t.length() - q.length()) + (index == 0 || boundary(t, index) ? 200 : 0);
        }
        int score = 0;
        int at = 0;
        int last = -2;
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (c == ' ') {
                continue;
            }
            int found = t.indexOf(c, at);
            if (found < 0) {
                return NO_MATCH;
            }
            score += found == last + 1 ? 15 : boundary(t, found) ? 10 : 1;
            score -= Math.min(found - at, 10);
            last = found;
            at = found + 1;
        }
        return score;
    }

    private static boolean boundary(String text, int index) {
        if (index == 0) {
            return true;
        }
        char before = text.charAt(index - 1);
        return before == ' ' || before == '_' || before == '-' || before == '.' || before == ':' || before == '#';
    }
}
