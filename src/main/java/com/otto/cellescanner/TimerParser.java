package com.otto.cellescanner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the "Tilbage: ..." line on a celle sign into total remaining seconds.
 *
 * Supported units: d (days), t (hours), m (minutes).
 * Examples: "Tilbage: 5d 18t", "Tilbage: 8t", "Tilbage: 45m", "Tilbage: 1t 20m"
 */
public class TimerParser {

    private static final Pattern PREFIX = Pattern.compile("^Tilbage:\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*([dtm])", Pattern.CASE_INSENSITIVE);

    /**
     * @return total remaining seconds, or -1 if the line could not be parsed.
     */
    public static long parseSeconds(String line) {
        if (line == null) {
            return -1;
        }

        String text = PREFIX.matcher(line.trim()).replaceFirst("").trim();
        if (text.isEmpty()) {
            return -1;
        }

        Matcher matcher = TOKEN.matcher(text);
        long totalSeconds = 0;
        boolean foundAny = false;

        while (matcher.find()) {
            foundAny = true;
            long value = Long.parseLong(matcher.group(1));
            char unit = Character.toLowerCase(matcher.group(2).charAt(0));

            switch (unit) {
                case 'd':
                    totalSeconds += value * 86400L;
                    break;
                case 't':
                    totalSeconds += value * 3600L;
                    break;
                case 'm':
                    totalSeconds += value * 60L;
                    break;
                default:
                    break;
            }
        }

        return foundAny ? totalSeconds : -1;
    }
}
