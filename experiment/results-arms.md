# Results — the two extra arms

Each arm was measured on its tag with the same commands as v1 ([protocol.md](protocol.md)).

| Suite | Tests | Catalogued mutations killed | PIT `:core` (detected) | Kover lines |
|---|---|---|---|---|
| v1 — Opus, write-tests prompt | 88 | 15/15 | 90/117 (77%) | 278/287 (96.9%) |
| Arm A — Sonnet, write-tests prompt | 61 | 13/15 | 89/117 (76%) | 255/287 (88.9%) |
| Arm A — Haiku, write-tests prompt | 92 | 14/15 | 93/117 (79%) | 278/287 (96.9%) |
| Arm B — Opus, ticket + tests | 20 | 6/15 | 55/119 (46%) | 214/289 (74.0%) |

Arm B's agent wrote its own production code, so its catalog is in `mutations/feature-arm/` on branch `arm/feature`. Within the ticket's scope (rules 1, 6 and 7: M01, M08–M12) it killed 6 of 6. The nine survivors are rules the ticket never touched, which nobody asked it to test.

## sonnet

| Mutation | Rule | What breaks | Result | Caught by |
|---|---|---|---|---|
| M01 | 1 | Coupon minimum: `>=` becomes `>` | KILLED | MiddlewareTest.eligible coupon is applied; PriceCalculatorTest.isEligible is inclusive of minimum |
| M02 | 2 | Percent discount no longer capped at its maximum | KILLED | PriceCalculatorTest.percent coupon is capped |
| M03 | 3 | Amount-off coupon can exceed the subtotal | KILLED | PriceCalculatorTest.amount coupon is capped at subtotal |
| M04 | 3 | Points no longer capped at what is left to pay | KILLED | PriceCalculatorTest.points are capped at amount owed |
| M05 | 3 | Negative points no longer ignored | KILLED | PriceCalculatorTest.negative points are ignored |
| M06 | 4 | Tax computed on the subtotal instead of the discounted amount | KILLED | PriceCalculatorTest.amount coupon is capped at subtotal |
| M07 | 5 | Free shipping: `>` becomes `>=` | KILLED | PriceCalculatorTest.shipping is free only strictly above threshold |
| M08 | 1 | Coupon middleware skips the minimum check | KILLED | MiddlewareTest.coupon below minimum is rejected |
| M09 | 6 | A rejected code also removes the coupon already applied | **SURVIVED** | - |
| M10 | 6 | A rejected code leaves the validating flag on | KILLED | CheckoutReducerTest.CouponRejected sets error and clears flag |
| M11 | 6 | A network failure is reported as an unknown code | KILLED | MiddlewareTest.network error is rejected |
| M12 | 7 | A second Apply while one is in flight is no longer ignored | KILLED | MiddlewareTest.apply while validating is ignored |
| M13 | 8 | Cart saved before the cached quote is cleared | KILLED | MiddlewareTest.SaveCart clears cached quote, saves snapshot, dispatches CartSaved |
| M14 | 8 | Saving no longer clears the cached quote | KILLED | MiddlewareTest.SaveCart clears cached quote, saves snapshot, dispatches CartSaved |
| M15 | 9 | Checkout screen stops passing `showDiscountRow` (default is false) | **SURVIVED** | - |

Killed 13 of 15 mutations.

## haiku

| Mutation | Rule | What breaks | Result | Caught by |
|---|---|---|---|---|
| M01 | 1 | Coupon minimum: `>=` becomes `>` | KILLED | CheckoutStoreTest.a second coupon request while one is in flight is dropped; CouponMiddlewareTest.applies a coupon when the subtotal exactly meets its minimum; +2 more |
| M02 | 2 | Percent discount no longer capped at its maximum | KILLED | PriceCalculatorTest.percent coupon is capped at its maximum discount |
| M03 | 3 | Amount-off coupon can exceed the subtotal | KILLED | PriceCalculatorTest.amount coupon is capped at the subtotal |
| M04 | 3 | Points no longer capped at what is left to pay | KILLED | PriceCalculatorTest.loyalty points never exceed the amount due |
| M05 | 3 | Negative points no longer ignored | KILLED | PriceCalculatorTest.negative loyalty points are treated as zero |
| M06 | 4 | Tax computed on the subtotal instead of the discounted amount | KILLED | CheckoutReducerTest.CouponApplied stores the coupon, stops validating and requotes; CheckoutStoreTest.applying a coupon reaches the state with its discount in the quote; +2 more |
| M07 | 5 | Free shipping: `>` becomes `>=` | KILLED | PriceCalculatorTest.free-shipping threshold is judged on the discounted amount; PriceCalculatorTest.shipping is charged at or below the free-shipping threshold |
| M08 | 1 | Coupon middleware skips the minimum check | KILLED | CouponMiddlewareTest.rejects a found coupon whose minimum the cart does not meet |
| M09 | 6 | A rejected code also removes the coupon already applied | **SURVIVED** | - |
| M10 | 6 | A rejected code leaves the validating flag on | KILLED | CheckoutReducerTest.CouponRejected stores the error and stops validating; CheckoutStoreTest.a rejected coupon surfaces its error and leaves the cart alone |
| M11 | 6 | A network failure is reported as an unknown code | KILLED | CouponMiddlewareTest.rejects a network failure as NETWORK |
| M12 | 7 | A second Apply while one is in flight is no longer ignored | KILLED | CheckoutStoreTest.a second coupon request while one is in flight is dropped; CouponMiddlewareTest.ignores a new request while a validation is already in flight |
| M13 | 8 | Cart saved before the cached quote is cleared | KILLED | CartPersistenceMiddlewareTest.SaveCart clears the cached quote before saving a snapshot and dispatches CartSaved; CheckoutStoreTest.SaveCart persists the current cart and coupon code |
| M14 | 8 | Saving no longer clears the cached quote | KILLED | CartPersistenceMiddlewareTest.SaveCart clears the cached quote before saving a snapshot and dispatches CartSaved; CheckoutStoreTest.SaveCart persists the current cart and coupon code |
| M15 | 9 | Checkout screen stops passing `showDiscountRow` (default is false) | KILLED | CheckoutScreenTest.an applied coupon is confirmed and its discount row is shown |

Killed 14 of 15 mutations.

## feature (opus)

| Mutation | Rule | What breaks | Result | Caught by |
|---|---|---|---|---|
| M01 | 1 | Coupon minimum: `>=` becomes `>` | KILLED | CouponValidationTest.minimum is checked against the cart at validation time; CouponValidationTest.next successful apply clears the error and replaces the coupon; +1 more |
| M02 | 2 | Percent discount no longer capped at its maximum | **SURVIVED** | - |
| M03 | 3 | Amount-off coupon can exceed the subtotal | **SURVIVED** | - |
| M04 | 3 | Points no longer capped at what is left to pay | **SURVIVED** | - |
| M05 | 3 | Negative points no longer ignored | **SURVIVED** | - |
| M06 | 4 | Tax computed on the subtotal instead of the discounted amount | **SURVIVED** | - |
| M07 | 5 | Free shipping: `>` becomes `>=` | **SURVIVED** | - |
| M08 | 1 | Coupon middleware skips the minimum check | KILLED | CouponValidationTest.[2] BELOW_MINIMUM; CouponValidationTest.subtotal one cent below the minimum is rejected; +1 more |
| M09 | 6 | A rejected code also removes the coupon already applied | KILLED | CouponValidationTest.[1] NOT_FOUND; CouponValidationTest.[2] BELOW_MINIMUM; +1 more |
| M10 | 6 | A rejected code leaves the validating flag on | KILLED | CouponValidationTest.network failure is rejected with NETWORK; CouponValidationTest.valid code below the minimum subtotal is rejected with BELOW_MINIMUM |
| M11 | 6 | A network failure is reported as an unknown code | KILLED | CouponValidationTest.[3] NETWORK; CouponValidationTest.network failure is rejected with NETWORK |
| M12 | 7 | A second Apply while one is in flight is no longer ignored | KILLED | CouponValidationTest.concurrent applies on a multi-threaded dispatcher start only one lookup; CouponValidationTest.second apply while validating is ignored |
| M13 | 8 | Cart saved before the cached quote is cleared | **SURVIVED** | - |
| M14 | 8 | Saving no longer clears the cached quote | **SURVIVED** | - |
| M15 | 9 | Checkout screen stops passing `showDiscountRow` (default is false) | **SURVIVED** | - |

Killed 6 of 15 mutations.

## Why M09 and M15 survived

**M09** (a rejected code also removes the coupon already applied) survived in Sonnet and Haiku. Both test the rejection starting from a state with no coupon applied, so removing the applied coupon changes nothing the test can see:

```kotlin
// arm/sonnet — core/src/test/kotlin/com/eliaseguizabal/checkout/store/CheckoutReducerTest.kt
@Test fun `CouponRejected sets error and clears flag`() {
    val s = reducer.reduce(CheckoutState(isValidatingCoupon = true), CheckoutAction.CouponRejected(CouponError.NOT_FOUND))
    assertEquals(CouponError.NOT_FOUND, s.couponError)
    assertFalse(s.isValidatingCoupon)
}
```

**M15** (the checkout screen stops passing `showDiscountRow`) survived in Sonnet. Its row test renders `QuoteSummary` directly and passes the flag by hand. The suite renders `CheckoutScreen` in ten other tests, so the deciding line is covered, but none of them uses a quote with a discount and looks for the row:

```kotlin
// arm/sonnet — app/src/test/kotlin/com/eliaseguizabal/checkout/app/CheckoutUiTest.kt
rule.setContent { QuoteSummary(quote, showDiscountRow = true) }
// …
rule.onNodeWithTag("discountRow").assertIsDisplayed()
```
