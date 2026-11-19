package pos;

import pos.domain.product.Category;
import pos.domain.product.Inventory;
import pos.domain.product.Product;

public final class SampleData {
    private SampleData() {}

    public static void seed(Inventory inventory) {
        inventory.addProduct(new Product(1, "Indomie Chicken", Category.FOOD, 25000, 40));
        inventory.addProduct(new Product(2, "Peak Milk Tin", Category.FOOD, 70000, 25));
        inventory.addProduct(new Product(3, "Bluetooth Speaker", Category.ELECTRONICS, 1500000, 5));
        inventory.addProduct(new Product(4, "Phone Charger", Category.ELECTRONICS, 350000, 12));
        inventory.addProduct(new Product(5, "Ankara Shirt", Category.CLOTHING, 800000, 8));
        inventory.addProduct(new Product(6, "Notebook", Category.OTHER, 50000, 0));
    }
}
