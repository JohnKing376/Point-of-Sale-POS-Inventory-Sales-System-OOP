package pos.domain.pricing.interfaces;

import java.util.List;
import java.util.Map;

public interface Discount {
    Map<PricedLine, Long> amountsOff(List<? extends PricedLine> lines);

    String describe();
}
