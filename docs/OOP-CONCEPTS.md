# OOP Concepts in This Project

This file explains how the POS system shows **composition**, **aggregation** and the **four principles of OOP**: encapsulation, abstraction, inheritance and polymorphism. Each point names the class and method where you can see it in the code.

---

## Composition and aggregation

Both describe one object containing others. The difference is **who controls the parts' lifetime**.

### Composition: `Order` ◆── `OrderItem`

In composition, the parts belong to the whole: they are created by it, can't exist without it, and disappear with it.

- **`Order` creates its own items.** In [`Order.addItem()`](../pos/domain/order/Order.java), the order runs `items.add(new OrderItem(product, qty))`. Nothing outside builds an `OrderItem` and hands it in.
- **No other class can create or change an item.** The [`OrderItem`](../pos/domain/order/OrderItem.java) constructor and `increaseQuantity()` have no `public` keyword, so only classes in the `order` package (that is, `Order`) can call them. The UI can't write `new OrderItem(...)`; it has to go through `order.addItem(...)`.
- **No other class can change the list.** `Order.getItems()` returns `List.copyOf(items)`, a read-only snapshot.
- **An item only makes sense inside its order.** "3 × Indomie at ₦250" with no order attached means nothing. When an order is discarded, its items go with it.

> Real-world comparison: an invoice and its line items. Tear up the invoice and the lines are gone too.

### Aggregation: `Inventory` ◇── `Product`, and `OrderItem` ──→ `Product`

In aggregation, the whole uses parts that exist independently: they are made elsewhere and outlive any one container.

- **Products are created outside the inventory.** [`SampleData.seed()`](../pos/SampleData.java) calls `new Product(...)` and then passes each one to [`Inventory.addProduct()`](../pos/domain/product/Inventory.java). The inventory only stores references to them in a `Map`.
- **`OrderItem` points to a `Product` but doesn't own it.** The same "Bluetooth Speaker" object is shared by the inventory and by every order that contains it. Cancelling an order doesn't delete the product.
- **One detail mixes the two ideas on purpose.** An item copies the product's price when it's added (`this.unitPrice = product.getPrice()` in the `OrderItem` constructor). The item owns that price snapshot but only borrows the product's identity, so a price change doesn't affect an order that's already open.
- **`CheckoutService` → `Inventory` is aggregation too.** [`CheckoutService`](../pos/services/CheckoutService.java) receives an `Inventory` that was created somewhere else (`Main` and `GuiMain` pass it in), uses it, and doesn't own it.

### Summary

| Relationship | Kind | Who creates the part? | Does the part outlive the whole? |
|---|---|---|---|
| `Order` → `OrderItem` | Composition | `Order` itself | No |
| `Inventory` → `Product` | Aggregation | Outside code (`SampleData`) | Yes |
| `OrderItem` → `Product` | Aggregation (reference) | The inventory side | Yes |
| `CheckoutService` → `Inventory` | Aggregation (passed in through the constructor) | `Main` / `GuiMain` | Yes |

> **Note:** Java has no keyword for composition. You get it through design choices: create the parts inside the whole, keep their constructors non-public, and never hand out the internal list.

---

## The four principles of OOP

### 1. Encapsulation: each object protects its own data

Fields are `private` and can only be changed through methods that check the rules.

- **[`Product`](../pos/domain/product/Product.java)** keeps `stockQuantity` private. The only way to reduce it is `decreaseStock()`, which refuses a quantity of zero or less and refuses to go below zero. Stock can never become negative, because no other code can set it directly.
- **[`Order`](../pos/domain/order/Order.java) enforces its own life cycle.** `addItem()`, `removeItem()`, `applyDiscount()` and `setTaxPolicy()` all check that the status is `OPEN` first. `markPaid()` refuses an empty order. Once an order is paid, nothing can change it.
- **`Order` doesn't blindly trust its own discount.** `Order.lineDiscounts()` checks every amount a discount returns. It rejects negative amounts, amounts bigger than the line, and amounts for lines that aren't in the order.

**Benefit:** the rules live in one place, so neither the GUI nor the console can break them, even by accident.

### 2. Abstraction: depend on what something does, not how it does it

`Order` holds a `Discount` and a `TaxPolicy`. Both are interfaces that only state what can be done:

```java
// Discount
Map<PricedLine, Long> amountsOff(List<? extends PricedLine> lines);

// TaxPolicy
long taxFor(PricedLine line, long taxableAmount);
```

- `Order` has no idea whether it's dealing with "10% off" or "buy 2 get 1 free".
- **[`PricedLine`](../pos/domain/pricing/interfaces/PricedLine.java)** is another abstraction. Pricing rules see a line only as "product ID, category, price, quantity", never as a full `OrderItem`. That is what keeps the `pricing` package independent of the `order` package.
- **[`CheckoutService.checkout()`](../pos/services/CheckoutService.java)** hides a multi-step process behind one call: check stock for every line, deduct it, mark the order paid and build a `Receipt`. The UI just calls `checkout(order)`.

### 3. Inheritance: share common behaviour through a base class

[`OrderLevelDiscount`](../pos/domain/pricing/discount/OrderLevelDiscount.java) is an **abstract class** that does the hard part: splitting a discount fairly across the lines in proportion to their value. It leaves one blank for subclasses to fill in:

```java
protected abstract long totalOff(long subTotal);
```

- [`PercentageDiscount`](../pos/domain/pricing/discount/PercentageDiscount.java) and [`FixedAmountDiscount`](../pos/domain/pricing/discount/FixedAmountDiscount.java) both `extends OrderLevelDiscount`. They differ only in how they compute that total.
- The splitting code is written once and reused by both.
- `amountsOff()` is marked `final`, so subclasses can't override it and break the splitting rule. This design is called the **Template Method pattern**.

Interfaces give a second, lighter kind of inheritance. `OrderItem implements PricedLine`, and every discount and tax class implements its interface.

### 4. Polymorphism: one call, many behaviours

This is the core of the project. Look at this line in `Order.taxTotal()`:

```java
total += taxPolicy.taxFor(item, item.lineTotal() - discounts.get(item));
```

That single line behaves differently depending on which object is behind `taxPolicy` at runtime:

| Object behind `taxPolicy` | What that line does |
|---|---|
| [`FlatVat`](../pos/domain/pricing/tax/FlatVat.java) | 7.5% on every line |
| [`CategoryTax`](../pos/domain/pricing/tax/CategoryTax.java) | 7.5%, but 0 for exempt categories such as FOOD |
| [`TaxExempt`](../pos/domain/pricing/tax/TaxExempt.java) | Always 0 |

The same applies to `discount.amountsOff(...)` in `Order.lineDiscounts()`, across the four discount classes:

| Object behind `discount` | Amount off |
|---|---|
| [`NoDiscount`](../pos/domain/pricing/discount/NoDiscount.java) | Nothing |
| `PercentageDiscount` | A percentage of the subtotal, spread across lines |
| `FixedAmountDiscount` | A fixed amount, capped at the subtotal, spread across lines |
| [`BuyXGetYFree`](../pos/domain/pricing/discount/BuyXGetYFree.java) | Free units on one product's line only |

When you pick an option in the GUI, it builds a different object and passes it to the order. **The order's code never changes.**

- **Open/closed principle:** to add a "Student discount" or a "Luxury tax", write one new class that implements the interface. `Order`, `CheckoutService`, `Receipt` and the UI all keep working unchanged.
- **Null objects:** `NoDiscount` and `TaxExempt` stand in for "nothing applies". Instead of `if (discount != null)` checks everywhere, "no discount" is just another object that returns nothing off.

---

## How it all fits together at checkout

1. **The user adds a product.** The UI looks up a shared `Product` in the `Inventory` (**aggregation**) and calls `order.addItem()`. The `Order` creates the `OrderItem` itself (**composition**) and checks that it's still open (**encapsulation**).
2. **The user picks "10% off" and "food exempt".** The UI gives the order a `PercentageDiscount` and a `CategoryTax`, which the order only sees as `Discount` and `TaxPolicy` (**abstraction**).
3. **Totals are calculated.** `PercentageDiscount` reuses the splitting logic it inherits from `OrderLevelDiscount` (**inheritance**), and `Order` calls `amountsOff` and `taxFor` without knowing which classes are behind them (**polymorphism**).
4. **The user checks out.** `checkout(order)` runs the whole multi-step process in one call (**abstraction**), and `Product.decreaseStock()` guarantees stock never goes negative (**encapsulation**).

---

## Quick reference

| Concept | Where to look |
|---|---|
| Composition | `Order` + `OrderItem` (package-private constructor, `List.copyOf`) |
| Aggregation | `Inventory` + `Product`, `OrderItem.product`, `CheckoutService.inventory` |
| Encapsulation | `Product.decreaseStock()`, `Order` status checks, `Order.lineDiscounts()` |
| Abstraction | `Discount`, `TaxPolicy`, `PricedLine`, `CheckoutService.checkout()` |
| Inheritance | `OrderLevelDiscount` → `PercentageDiscount`, `FixedAmountDiscount` |
| Polymorphism | `Order.taxTotal()`, `Order.lineDiscounts()` |
