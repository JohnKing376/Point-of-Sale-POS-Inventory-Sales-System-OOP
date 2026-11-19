package pos.domain.pricing.discount;

import pos.domain.Money;

public class PercentageDiscount extends OrderLevelDiscount {
    private final int rateBps;

    public PercentageDiscount(int rateBps) {
        if (rateBps < 1 || rateBps > 10000) {
            throw new IllegalArgumentException("Discount rate must be between 1 and 10000 basis points");
        }
        this.rateBps = rateBps;
    }

    public static PercentageDiscount ofPercent(int percent) {
        if (percent < 1 || percent > 100) {
            throw new IllegalArgumentException("Percentage must be between 1 and 100");
        }
        return new PercentageDiscount(percent * 100);
    }

    @Override
    protected long totalOff(long subTotal) {
        return Money.applyRate(subTotal, rateBps);
    }

    @Override
    public String describe() {
        return Money.percent(rateBps) + " off";
    }
}
