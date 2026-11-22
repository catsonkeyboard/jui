package jui.kit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import java.util.Locale;

/**
 * Money as exact minor units, after the MoneyInput: values live in
 * cents (or the currency's minor unit), never in floats. Formatting goes
 * through BigDecimal with the currency's real fraction digits, and the
 * same grouping the input shows.
 */
public final class Currency {

    /** The minor units a currency carries: cents for USD, 0 for JPY. */
    public static int fractionDigits(java.util.Currency currency) {
        try {
            return currency.getDefaultFractionDigits();
        } catch (IllegalArgumentException e) {
            return 2;
        }
    }

    /** Formats minor units as the currency reads them, with its symbol. */
    public static String format(long minor, java.util.Currency currency, String symbolOverride) {
        int digits = fractionDigits(currency);
        BigDecimal amount = BigDecimal.valueOf(minor, digits);
        String symbol = symbolOverride != null ? symbolOverride : currency.getSymbol();
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.getDefault());
        DecimalFormat f = digits == 0
                ? new DecimalFormat("#,##0", symbols)
                : new DecimalFormat("#,##0." + "0".repeat(digits), symbols);
        return symbol + f.format(amount);
    }

    /** Formats with the default locale's currency. */
    public static String format(long minor) {
        return format(minor, Currency.get(), null);
    }

    /** The host's default currency. */
    public static java.util.Currency get() {
        try {
            return java.util.Currency.getInstance(Locale.getDefault());
        } catch (Throwable e) {
            return java.util.Currency.getInstance("USD");
        }
    }

    /** Parses what the user typed into minor units: "1,234.56" → 123456. */
    public static long parseMinor(String typed, java.util.Currency currency) {
        int digits = fractionDigits(currency);
        String clean = typed == null ? "" : typed.replace(",", "").replace(" ", "");
        if (clean.isEmpty()) {
            return 0;
        }
        try {
            return new BigDecimal(clean).movePointRight(digits)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return 0;
        }
    }

    /** Divides minor units into whole and fraction parts for rolling digit faces. */
    public static long whole(long minor, java.util.Currency currency) {
        return minor / pow10(fractionDigits(currency));
    }

    public static long fraction(long minor, java.util.Currency currency) {
        return minor % pow10(fractionDigits(currency));
    }

    private static long pow10(int n) {
        long v = 1;
        for (int i = 0; i < n; i++) {
            v *= 10;
        }
        return v;
    }

    private Currency() {}
}
