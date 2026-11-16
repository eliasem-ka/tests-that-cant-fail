# v1 results — the AI-written suite

Measured on tag `v1-ai-suite` with the commands in [protocol.md](protocol.md).

| Measure | Value |
|---|---|
| Tests | 88 (59 in `:core`, 29 in `:app`) |
| Removed (failed on correct code) | 0 |
| Compile fixes | 0 |
| Kover line coverage (both modules) | 278/287 (96.9%) |
| Kover branch coverage | 124/135 (91.9%) |
| Catalogued mutations killed | **15 / 15** |
| PIT (`:core`) | 85/117 killed (72%) |

## Catalogued mutations

| Mutation | Rule | What breaks | Result | Caught by |
|---|---|---|---|---|
| M01 | 1 | Coupon minimum: `>=` becomes `>` | KILLED | CouponMiddlewareTest.found and eligible coupon is applied; PriceCalculatorTest$Coupons.coupon applies at exactly its minimum; +1 more |
| M02 | 2 | Percent discount no longer capped at its maximum | KILLED | PriceCalculatorTest$Coupons.percent off is capped at its maximum |
| M03 | 3 | Amount-off coupon can exceed the subtotal | KILLED | PriceCalculatorTest$Coupons.amount off never exceeds the subtotal |
| M04 | 3 | Points no longer capped at what is left to pay | KILLED | PriceCalculatorTest$LoyaltyPoints.points are capped at the total so it never goes negative |
| M05 | 3 | Negative points no longer ignored | KILLED | PriceCalculatorTest$LoyaltyPoints.negative points are ignored |
| M06 | 4 | Tax computed on the subtotal instead of the discounted amount | KILLED | CheckoutReducerTest.CouponApplied stores the coupon, stops validating and discounts the quote; PriceCalculatorTest$Coupons.amount off never exceeds the subtotal; +4 more |
| M07 | 5 | Free shipping: `>` becomes `>=` | KILLED | PriceCalculatorTest$Shipping.exactly fifty dollars still pays shipping |
| M08 | 1 | Coupon middleware skips the minimum check | KILLED | CheckoutStoreTest.coupon below the minimum is rejected and not applied; CheckoutViewModelTest.factory wiring validates coupons through the real repository and calculator; +2 more |
| M09 | 6 | A rejected code also removes the coupon already applied | KILLED | CheckoutReducerTest.CouponRejected records the error, stops validating and keeps the coupon already applied |
| M10 | 6 | A rejected code leaves the validating flag on | KILLED | CheckoutReducerTest.CouponRejected records the error, stops validating and keeps the coupon already applied; CheckoutViewModelTest.factory wiring validates coupons through the real repository and calculator |
| M11 | 6 | A network failure is reported as an unknown code | KILLED | CouponMiddlewareTest.network failure is rejected as a network error |
| M12 | 7 | A second Apply while one is in flight is no longer ignored | KILLED | CheckoutStoreTest.a second ApplyCoupon while validating is ignored; CouponMiddlewareTest.does nothing while another coupon is being validated |
| M13 | 8 | Cart saved before the cached quote is cleared | KILLED | CartPersistenceMiddlewareTest.SaveCart clears the cached quote, then saves items and coupon code, then reports CartSaved |
| M14 | 8 | Saving no longer clears the cached quote | KILLED | CartPersistenceMiddlewareTest.SaveCart clears the cached quote, then saves items and coupon code, then reports CartSaved |
| M15 | 9 | Checkout screen stops passing `showDiscountRow` (default is false) | KILLED | CheckoutScreenTest.applied coupon is confirmed and its discount shown |

Killed 15 of 15 mutations.

## PIT survivors by class

| Class | Not killed |
|---|---|
| `CartPersistenceMiddleware` | 9 |
| `CouponMiddleware` | 6 |
| `CheckoutStore` | 5 |
| `CartMiddleware` | 3 |
| `InMemoryCouponRepository` | 3 |
| `InMemoryCartStore` | 3 |
| `CartSnapshot` | 2 |
| `AmountOff` | 1 |

Status counts: KILLED 85, NO_COVERAGE 6, SURVIVED 21, TIMED_OUT 5.

For comparison, the author's oracle suite scored 80/117 (68%) on PIT and also killed 15/15.
