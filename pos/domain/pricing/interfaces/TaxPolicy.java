package pos.domain.pricing.interfaces;

public interface TaxPolicy {
    long taxFor(PricedLine line, long taxableAmount);

    String describe();
}
