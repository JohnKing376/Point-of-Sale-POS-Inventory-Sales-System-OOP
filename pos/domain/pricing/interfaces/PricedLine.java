package pos.domain.pricing.interfaces;

import pos.domain.product.Category;

public interface PricedLine {
    int getProductId();
    Category getCategory();
    long getUnitPrice();
    int getQuantity();
    long lineTotal();
}
