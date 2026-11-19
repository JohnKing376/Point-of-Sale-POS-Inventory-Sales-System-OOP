package pos.domain.pricing.tax;

import pos.domain.pricing.interfaces.PricedLine;
import pos.domain.pricing.interfaces.TaxPolicy;

public class TaxExempt implements TaxPolicy {
    @Override
    public long taxFor(PricedLine line, long taxableAmount) {
        return 0;
    }

    @Override
    public String describe() {
        return "Tax exempt";
    }
}
