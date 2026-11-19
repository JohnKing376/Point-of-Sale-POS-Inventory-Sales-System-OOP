package pos.domain.pricing.discount;

import java.util.List;
import java.util.Map;

import pos.domain.pricing.interfaces.Discount;
import pos.domain.pricing.interfaces.PricedLine;

public class NoDiscount implements Discount {
    @Override
    public Map<PricedLine, Long> amountsOff(List<? extends PricedLine> lines) {
        return Map.of();
    }

    @Override
    public String describe() {
        return "No discount";
    }
}
