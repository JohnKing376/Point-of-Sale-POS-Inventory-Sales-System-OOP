package pos.domain;

import java.math.BigDecimal;
import java.util.Locale;

public final class Money {
    private Money() {}

    public static String naira(long kobo) {
        String sign = kobo < 0 ? "-" : "";
        long abs = Math.abs(kobo);
        return String.format(Locale.ROOT, "%s₦%,d.%02d", sign, abs / 100, abs % 100);
    }

    public static long applyRate(long amount, int bps) {
        return Math.floorDiv(Math.multiplyExact(amount, (long) bps) + 5000, 10000);
    }

    public static String percent(int bps) {
        return BigDecimal.valueOf(bps, 2).stripTrailingZeros().toPlainString() + "%";
    }
}
