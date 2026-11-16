# Mutation catalog

Each mutation breaks one business rule with one minimal change to the line that enforces it.
They were written from the rules, before any agent-written test existed (the author's oracle, which validates them, came first), and frozen before the first AI suite was generated.

| Id | Rule | What breaks | File |
|---|---|---|---|
| M01 | 1 | Coupon minimum: `>=` becomes `>` | `PriceCalculator.kt` |
| M02 | 2 | Percent discount no longer capped at its maximum | `PriceCalculator.kt` |
| M03 | 3 | Amount-off coupon can exceed the subtotal | `PriceCalculator.kt` |
| M04 | 3 | Points no longer capped at what is left to pay | `PriceCalculator.kt` |
| M05 | 3 | Negative points no longer ignored | `PriceCalculator.kt` |
| M06 | 4 | Tax computed on the subtotal instead of the discounted amount | `PriceCalculator.kt` |
| M07 | 5 | Free shipping: `>` becomes `>=` | `PriceCalculator.kt` |
| M08 | 1 | Coupon middleware skips the minimum check | `CouponMiddleware.kt` |
| M09 | 6 | A rejected code also removes the coupon already applied | `CheckoutReducer.kt` |
| M10 | 6 | A rejected code leaves the validating flag on | `CheckoutReducer.kt` |
| M11 | 6 | A network failure is reported as an unknown code | `CouponMiddleware.kt` |
| M12 | 7 | A second Apply while one is in flight is no longer ignored | `CouponMiddleware.kt` |
| M13 | 8 | Cart saved before the cached quote is cleared | `CartPersistenceMiddleware.kt` |
| M14 | 8 | Saving no longer clears the cached quote | `CartPersistenceMiddleware.kt` |
| M15 | 9 | Checkout screen stops passing `showDiscountRow` (default is false) | `CheckoutScreen.kt` |

Rules: 1 coupon minimum · 2 percent cap · 3 amount-off and points never exceed what is owed · 4 tax after discount · 5 free shipping above $50 · 6 a rejected code keeps the previous coupon and total · 7 a second Apply in flight is ignored · 8 clear the cached quote before saving · 9 the discount row is shown only when there is a discount
