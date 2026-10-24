package dev.materialshare.common;

import java.math.BigInteger;
import java.util.OptionalInt;

/** Reads a count typed by a player. */
public final class Counts {
    private Counts() {
    }

    /** The number typed for an item that stacks to 64 (kept for callers that do not know the item). */
    public static OptionalInt parse(String text) {
        return parse(text, 64);
    }

    /**
     * Accepts "12", " 7 ", "1,000", "1 000" and full-width digits; "3s" or "3st" is three stacks and "1sb" one shulker box
     * (27 stacks); parts can be added: "2s+10". Negative numbers become 0 and huge numbers become Integer.MAX_VALUE
     * (the server then clamps to the required amount). Anything else is not a number.
     */
    public static OptionalInt parse(String text, int stackSize) {
        if (text == null) {
            return OptionalInt.empty();
        }
        String cleaned = text.strip().replace(",", "").replace(" ", "").toLowerCase(java.util.Locale.ROOT);
        if (cleaned.isEmpty()) {
            return OptionalInt.empty();
        }
        BigInteger sum = BigInteger.ZERO;
        boolean negative = false;
        String[] terms = cleaned.split("\\+", -1);
        for (String term : terms) {
            if (term.startsWith("-") && terms.length == 1) {
                negative = true;
                term = term.substring(1);
            }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\p{Nd}+)(sb|st|s)?").matcher(term);
            if (!m.matches()) {
                return OptionalInt.empty();
            }
            StringBuilder ascii = new StringBuilder();
            for (char c : m.group(1).toCharArray()) {
                ascii.append(Character.forDigit(Character.digit(c, 10), 10));
            }
            BigInteger value = new BigInteger(ascii.toString());
            int stack = Math.max(1, stackSize);
            if ("s".equals(m.group(2)) || "st".equals(m.group(2))) {
                value = value.multiply(BigInteger.valueOf(stack));
            } else if ("sb".equals(m.group(2))) {
                value = value.multiply(BigInteger.valueOf(27L * stack));
            }
            sum = sum.add(value);
        }
        if (negative) {
            return OptionalInt.of(0);
        }
        return OptionalInt.of(sum.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 ? Integer.MAX_VALUE : sum.intValue());
    }

    /**
     * The collected count after importing what the player carries: never lower than before (items already handed in
     * no longer sit in the inventory) and never above what is required.
     */
    public static int imported(int current, int available, int required) {
        return Math.max(current, Math.max(0, Math.min(available, required)));
    }
}
