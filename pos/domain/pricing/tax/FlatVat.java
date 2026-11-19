package pos.domain.pricing.tax;

import pos.domain.Money;
import pos.domain.pricing.interfaces.PricedLine;
import pos.domain.pricing.interfaces.TaxPolicy;

public class FlatVat implements TaxPolicy {
    public static final int STANDARD_RATE_BPS = 750;

    private final int rateBps;

    public FlatVat(int rateBps) {
        if (rateBps < 0 || rateBps > 10000) {
            throw new IllegalArgumentException("VAT rate must be between 0 and 10000 basis points");
        }
        this.rateBps = rateBps;
    }

    public FlatVat() {
        this(STANDARD_RATE_BPS);
    }

    @Override
    public long taxFor(PricedLine line, long taxableAmount) {
        return Money.applyRate(taxableAmount, rateBps);
    }

    @Override
    public String describe() {
        return "VAT " + Money.percent(rateBps);
    }
}
