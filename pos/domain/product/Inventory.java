package pos.domain.product;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Inventory {
    private final Map<Integer, Product> products = new TreeMap<>();

    public void addProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }
        if (products.containsKey(product.getId())) {
            throw new IllegalArgumentException("Product with ID " + product.getId() + " already exists");
        }
        products.put(product.getId(), product);
    }

    public Product getById(int id) {
        Product product = products.get(id);
        if (product == null) {
            throw new IllegalArgumentException("Product with ID " + id + " does not exist");
        }
        return product;
    }

    public void restock(int id, int qty) {
        getById(id).increaseStock(qty);
    }

    public void removeStock(int id, int qty) {
        getById(id).decreaseStock(qty);
    }

    public boolean isInStock(int id, int qty) {
        Product product = products.get(id);
        return product != null && product.getStockQuantity() >= qty;
    }

    public List<Product> listAll() {
        return List.copyOf(products.values());
    }
}
