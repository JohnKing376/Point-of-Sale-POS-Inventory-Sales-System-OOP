package pos.domain.pricing.discount;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pos.domain.pricing.interfaces.Discount;
import pos.domain.pricing.interfaces.PricedLine;

public abstract class OrderLevelDiscount implements Discount {

    protected abstract long totalOff(long subTotal);

    @Override
    public final Map<PricedLine, Long> amountsOff(List<? extends PricedLine> lines) {
        long subTotal = 0;
        for (PricedLine line : lines) {
            subTotal += line.lineTotal();
        }
        long total = Math.max(0, Math.min(totalOff(subTotal), subTotal));
        return spreadProportionally(total, lines, subTotal);
    }

    private static Map<PricedLine, Long> spreadProportionally(
            long total, List<? extends PricedLine> lines, long subTotal) {
        Map<PricedLine, Long> shares = new HashMap<>();
        if (total == 0 || subTotal == 0) {
            return shares;
        }

        Map<PricedLine, Long> remainders = new HashMap<>();
        long given = 0;
        for (PricedLine line : lines) {
            long weighted = Math.multiplyExact(total, line.lineTotal());
            shares.put(line, weighted / subTotal);
            remainders.put(line, weighted % subTotal);
            given += weighted / subTotal;
        }

        List<PricedLine> byRemainder = new ArrayList<>(lines);
        byRemainder.sort(Comparator.comparingLong((PricedLine line) -> remainders.get(line)).reversed());
        for (int k = 0; k < total - given; k++) {
            shares.merge(byRemainder.get(k), 1L, Long::sum);
        }
        return shares;
    }
}
