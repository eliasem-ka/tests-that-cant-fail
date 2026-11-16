# v2 results — the agent proves its tests

v2 gives a fresh agent the arm's v1 suite and the "break each rule yourself" prompt ([protocol.md](protocol.md)).

The pre-registered rule put v2 on the arm with the most survivors: Arm B (feature), with 9. Those nine
are rules the ticket never touched, so v2 there mostly measures *writing tests for untested code*.
v2 was **also** run on Sonnet, the arm with the most survivors inside its own scope (M09, M15).
That second run was decided after seeing the arm results.

| Suite | Tests | Catalogued mutations killed | PIT `:core` (detected) | Kover lines |
|---|---|---|---|---|
| Arm B (feature) v1 → v2 | 20 → 88 | 6/15 → **15/15** | 55/119 (46%) → 91/119 (76%) | 74.0% → 96.9% |
| Sonnet v1 → v2 | 61 → 69 | 13/15 → **14/15** | 89/117 (76%) → 90/117 (77%) | 88.9% → 88.9% |

## What the Sonnet run shows

M15 (the screen stops passing `showDiscountRow`) is now caught: the agent broke the screen's
argument and added a test that renders `CheckoutScreen` instead of `QuoteSummary` alone.

M09 (a rejected code also removes the coupon already applied) still survives, and the agent's
report says every rule is caught. Both are true from where it stood. The rule "a rejected code keeps
the previous coupon" is enforced by what the reducer **doesn't** do: the `CouponRejected` branch
never touches `appliedCoupon`. There is no line to break, so a method that starts from "find the line
that makes the rule true" can't find it. The agent broke the lines that exist (the error and the flag)
and they were caught. The Arm B agent caught the same rule because its ticket stated it.

Kover didn't move (88.9% before and after) while protection went from 13 to 14 of 15.

## Arm B (feature) v2

| Mutation | Rule | What breaks | Result | Caught by |
|---|---|---|---|---|
| M01 | 1 | Coupon minimum: `>=` becomes `>` | KILLED | CouponValidationTest.minimum is checked against the cart as it is when the lookup returns; CouponValidationTest.minimum is checked against the cart at validation time; +5 more |
| M02 | 2 | Percent discount no longer capped at its maximum | KILLED | PriceCalculatorTest.percent coupon never discounts more than its cap |
| M03 | 3 | Amount-off coupon can exceed the subtotal | KILLED | PriceCalculatorTest.amount coupon never discounts more than the subtotal |
| M04 | 3 | Points no longer capped at what is left to pay | KILLED | PriceCalculatorTest.points never take the total below zero |
| M05 | 3 | Negative points no longer ignored | KILLED | PriceCalculatorTest.negative points are ignored |
| M06 | 4 | Tax computed on the subtotal instead of the discounted amount | KILLED | CheckoutReducerTest.coupon applied stores the coupon, stops validating and prices in the discount; CheckoutReducerTest.items changed keeps the applied coupon and points in the new price; +7 more |
| M07 | 5 | Free shipping: `>` becomes `>=` | KILLED | PriceCalculatorTest.shipping is charged at exactly the free-shipping threshold |
| M08 | 1 | Coupon middleware skips the minimum check | KILLED | CouponValidationTest.[2] BELOW_MINIMUM; CouponValidationTest.subtotal one cent below the minimum is rejected; +1 more |
| M09 | 6 | A rejected code also removes the coupon already applied | KILLED | CartPersistenceTest.save stores the coupon still applied, not a code that was just rejected; CheckoutReducerTest.coupon rejected records the error, stops validating and leaves coupon and price alone; +3 more |
| M10 | 6 | A rejected code leaves the validating flag on | KILLED | CheckoutReducerTest.coupon rejected records the error, stops validating and leaves coupon and price alone; CouponValidationTest.network failure is rejected with NETWORK; +1 more |
| M11 | 6 | A network failure is reported as an unknown code | KILLED | CouponValidationTest.[3] NETWORK; CouponValidationTest.network failure is rejected with NETWORK |
| M12 | 7 | A second Apply while one is in flight is no longer ignored | KILLED | CouponValidationTest.concurrent applies on a multi-threaded dispatcher start only one lookup; CouponValidationTest.second apply while validating is ignored |
| M13 | 8 | Cart saved before the cached quote is cleared | KILLED | CartPersistenceTest.cache is cleared before saving, so the fresh save keeps its cache |
| M14 | 8 | Saving no longer clears the cached quote | KILLED | CartPersistenceTest.save drops the stale cached quote |
| M15 | 9 | Checkout screen stops passing `showDiscountRow` (default is false) | KILLED | CheckoutScreenTest.discount row appears when the quote has a discount |

Killed 15 of 15 mutations.

## Sonnet v2

| Mutation | Rule | What breaks | Result | Caught by |
|---|---|---|---|---|
| M01 | 1 | Coupon minimum: `>=` becomes `>` | KILLED | MiddlewareTest.eligible coupon is applied; MiddlewareTest.the typed code is what gets looked up; +1 more |
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
| M13 | 8 | Cart saved before the cached quote is cleared | KILLED | MiddlewareTest.SaveCart drops stale quote before saving the snapshot and dispatches CartSaved |
| M14 | 8 | Saving no longer clears the cached quote | KILLED | MiddlewareTest.SaveCart drops stale quote before saving the snapshot and dispatches CartSaved |
| M15 | 9 | Checkout screen stops passing `showDiscountRow` (default is false) | KILLED | CheckoutUiTest.screenShowsDiscountRowOnlyWhenThereIsADiscount |

Killed 14 of 15 mutations.
