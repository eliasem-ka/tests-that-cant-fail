# Feature-arm mutation catalog

The same 15 mutations, regenerated against the production code the feature-arm agent wrote.
Each one makes the same change to the same rule. Two had to be ported because the agent enforced the rule on a different line:

| Id | Rule | What breaks | Note |
|---|---|---|---|
| M01 | 1 | Coupon minimum: `>=` becomes `>` | same change, regenerated against this code |
| M02 | 2 | Percent discount no longer capped at its maximum | same change, regenerated against this code |
| M03 | 3 | Amount-off coupon can exceed the subtotal | same change, regenerated against this code |
| M04 | 3 | Points no longer capped at what is left to pay | same change, regenerated against this code |
| M05 | 3 | Negative points no longer ignored | same change, regenerated against this code |
| M06 | 4 | Tax computed on the subtotal instead of the discounted amount | same change, regenerated against this code |
| M07 | 5 | Free shipping: `>` becomes `>=` | same change, regenerated against this code |
| M08 | 1 | Coupon middleware skips the minimum check | ported: the agent checks the minimum against calculator.subtotal(state().items) |
| M09 | 6 | A rejected code also removes the coupon already applied | same change, regenerated against this code |
| M10 | 6 | A rejected code leaves the validating flag on | same change, regenerated against this code |
| M11 | 6 | A network failure is reported as an unknown code | same change, regenerated against this code |
| M12 | 7 | A second Apply while one is in flight is no longer ignored | ported: the agent guards with an AtomicBoolean instead of state().isValidatingCoupon |
| M13 | 8 | Cart saved before the cached quote is cleared | same change, regenerated against this code |
| M14 | 8 | Saving no longer clears the cached quote | same change, regenerated against this code |
| M15 | 9 | Checkout screen stops passing `showDiscountRow` (default is false) | same change, regenerated against this code |
