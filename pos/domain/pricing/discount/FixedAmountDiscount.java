package pos.domain.pricing.discount;

import pos.domain.Money;

public class FixedAmountDiscount extends OrderLevelDiscount {
    private final long amount;

    public FixedAmountDiscount(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Discount amount cannot be negative");
        }
        this.amount = amount;
    }

    @Override
    protected long totalOff(long subTotal) {
        return Math.min(amount, subTotal);
    }

    @Override
    public String describe() {
        return Money.naira(amount) + " off";
    }
}
