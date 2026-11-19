package pos.domain.pricing.discount;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pos.domain.pricing.interfaces.Discount;
import pos.domain.pricing.interfaces.PricedLine;

public class BuyXGetYFree implements Discount {
    private final int productId;
    private final int buyQty;
    private final int freeQty;

    public BuyXGetYFree(int productId, int buyQty, int freeQty) {
        if (productId <= 0 || buyQty <= 0 || freeQty <= 0) {
            throw new IllegalArgumentException("Product ID and quantities must be greater than 0");
        }
        this.productId = productId;
        this.buyQty = buyQty;
        this.freeQty = freeQty;
    }

    @Override
    public Map<PricedLine, Long> amountsOff(List<? extends PricedLine> lines) {
        Map<PricedLine, Long> amounts = new HashMap<>();
        for (PricedLine line : lines) {
            if (line.getProductId() == productId) {
                int groupSize = buyQty + freeQty;
                int freeUnits = (line.getQuantity() / groupSize) * freeQty;
                amounts.put(line, freeUnits * line.getUnitPrice());
            }
        }
        return amounts;
    }

    @Override
    public String describe() {
        return "Buy " + buyQty + " get " + freeQty + " free (product #" + productId + ")";
    }
}
