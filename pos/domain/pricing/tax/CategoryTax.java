package pos.domain.pricing.tax;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import pos.domain.Money;
import pos.domain.pricing.interfaces.PricedLine;
import pos.domain.pricing.interfaces.TaxPolicy;
import pos.domain.product.Category;

public class CategoryTax implements TaxPolicy {
    private final int rateBps;
    private final Set<Category> exemptCategories;

    public CategoryTax(int rateBps, Set<Category> exemptCategories) {
        if (rateBps < 0 || rateBps > 10000) {
            throw new IllegalArgumentException("VAT rate must be between 0 and 10000 basis points");
        }
        Objects.requireNonNull(exemptCategories, "Exempt categories cannot be null");

        this.rateBps = rateBps;
        EnumSet<Category> copy = EnumSet.noneOf(Category.class);
        copy.addAll(exemptCategories);
        this.exemptCategories = Collections.unmodifiableSet(copy);
    }

    public CategoryTax(Set<Category> exemptCategories) {
        this(FlatVat.STANDARD_RATE_BPS, exemptCategories);
    }

    @Override
    public long taxFor(PricedLine line, long taxableAmount) {
        if (exemptCategories.contains(line.getCategory())) {
            return 0;
        }
        return Money.applyRate(taxableAmount, rateBps);
    }

    @Override
    public String describe() {
        String base = "VAT " + Money.percent(rateBps);
        if (exemptCategories.isEmpty()) {
            return base;
        }
        String names = exemptCategories.stream().map(Enum::name).collect(Collectors.joining(", "));
        return base + ", " + names + " exempt";
    }
}
