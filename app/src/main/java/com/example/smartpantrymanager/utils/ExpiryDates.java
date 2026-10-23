package com.example.smartpantrymanager.utils;

import java.util.Calendar;

/**
 * Tiny date helper that works on "epoch days" (whole days since 1970-01-01) so the app needs
 * neither java.time (API 26+, minSdk here is 24) nor core-library desugaring, and so the logic
 * is trivially unit-testable with a fixed "today".
 *
 * Accepted stored formats: yyyy-MM-dd, yyyy/MM/dd (year first) and dd-MM-yyyy, dd/MM/yyyy
 * (day first). Anything else is treated as "no expiry date".
 */
public final class ExpiryDates {
    /** Returned by {@link #parse(String)} when the text is empty or not a valid date. */
    public static final long NO_DATE = Long.MIN_VALUE;

    private ExpiryDates() {}

    public static long today() {
        Calendar c = Calendar.getInstance();
        return epochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    public static long parse(String text) {
        if (text == null) return NO_DATE;
        String t = text.trim();
        if (t.length() > 10) t = t.substring(0, 10);               // tolerate a trailing time part
        String[] p = t.split("[-/]");
        if (p.length != 3) return NO_DATE;
        try {
            int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]), c = Integer.parseInt(p[2]);
            int y, m, d;
            if (p[0].length() == 4) { y = a; m = b; d = c; }        // yyyy-MM-dd
            else if (p[2].length() == 4) { y = c; m = b; d = a; }   // dd-MM-yyyy (day first)
            else return NO_DATE;
            if (m < 1 || m > 12 || d < 1 || d > daysInMonth(y, m)) return NO_DATE;
            return epochDay(y, m, d);
        } catch (NumberFormatException e) {
            return NO_DATE;
        }
    }

    /** Days from today until expiry (negative = already expired), or NO_DATE if there is no date. */
    public static long daysUntil(String expiry, long today) {
        long e = parse(expiry);
        return e == NO_DATE ? NO_DATE : e - today;
    }

    private static int daysInMonth(int y, int m) {
        switch (m) {
            case 2: return ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) ? 29 : 28;
            case 4: case 6: case 9: case 11: return 30;
            default: return 31;
        }
    }

    /** Days-from-civil algorithm (H. Hinnant); exact for the proleptic Gregorian calendar. */
    static long epochDay(int year, int month, int day) {
        long y = year - (month <= 2 ? 1 : 0);
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }
}
