# Prompts and agent reports

Each run is a fresh, headless Claude Code session started inside an isolated copy of the repo
(see [protocol.md](protocol.md)):

```bash
claude -p "<prompt>" --model claude-opus-5-5 \
  --setting-sources project,local --disable-slash-commands --no-chrome \
  --dangerously-skip-permissions --output-format json
```

`--setting-sources project,local` and `--disable-slash-commands` keep the author's own plugins,
hooks and skills out of the session. A smoke test of the same command answered
"No skills or plugins are loaded in this session."

## v1 — the AI-written suite

- **Model:** `claude-opus-5-5`
- **Started:** 2026-11-16T04:59Z (UTC)
- **Turns / duration:** 24 turns, 7.3 minutes
- **Prompt** (verbatim; `<workspace>` stands for the throwaway directory path):

> Write unit tests for the checkout feature in :core and :app. Make sure they pass. The project is at <workspace>; use JDK 21 for Gradle (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

- **What came back:** 11 test files (88 tests), all under `*/src/test/`. No production file changed.
  All 88 pass on the correct code, so none were removed and nothing needed a compile fix.

### The agent's final report (verbatim)

I added 88 unit tests (59 in `:core`, 29 in `:app`) and they all pass on a clean run (`./gradlew :core:test :app:testDebugUnitTest --rerun-tasks`). I also checked that the tests can fail: I put 21 bugs into the production code one at a time, and each one made at least one test fail. Afterwards I restored the source files. Only new test files were added, and nothing is committed.

**`:core`** (JUnit 5, coroutines-test, MockK)
- **`PriceCalculatorTest`**: tax rounding at exactly half a cent and either side of it, the $50.00 free-shipping cutoff, tax and shipping worked out on the discounted subtotal, the percent-off cap, the amount-off limit, coupon minimums (a cart exactly at the minimum qualifies), and loyalty points (capped at the total, negative amounts ignored). I worked out the expected amounts by hand; the tests don't compute them with the calculator.
- **`CheckoutReducerTest`**: one or more tests for each action, covering re-quoting, the coupon-validation flag, clearing errors, and keeping an already-applied coupon when a later one is rejected.
- **`CartMiddlewareTest`**, **`CouponMiddlewareTest`**, **`CartPersistenceMiddlewareTest`**: which actions each one dispatches, the guard that blocks a second coupon check while one is running, the coupon minimum being checked against the cart as it is when the lookup returns, and the cached quote being cleared before the cart is saved.
- **`InMemoryDataTest`**: coupon codes match regardless of case and spaces, the `OFFLINE` network-error case, the simulated delay, and the in-memory cart store.
- **`CheckoutStoreTest`**: the full store with the real middlewares, on simulated time.

**`:app`** (JUnit 4, Robolectric, Compose UI tests)
- **`MoneyTest`**: `formatCents` output.
- **`CheckoutViewModelTest`**: that intents reach the store, and that the real `Factory` loads the demo cart (total $82.44) and checks coupons correctly.
- **`QuoteSummaryTest`** and **`CheckoutScreenTest`**: when the discount, points and "Free" shipping rows appear, the Apply and Save buttons being enabled or disabled, the text for each coupon error, an error showing instead of the "applied" message, the points field accepting only digits, and the intent each button sends.

**Two things in the code you may want to look at.** I didn't change either:
1. If a coupon is applied and the cart then drops below its minimum, the discount goes to $0 but the coupon stays applied, so the screen still says "SAVE10 applied". My test only checks the $0 discount, so it doesn't lock in the "applied" message.
2. `CartSnapshot` doesn't save loyalty points. Also, `InMemoryCartStore.cachedQuote` is never set anywhere, so `clearCachedQuote()` currently has nothing to clear.

**A pitfall if you add store tests:** `advanceUntilIdle()` doesn't run work started on `backgroundScope`. My first store tests used `backgroundScope` and failed because the store never ran, so `CheckoutStoreTest` uses the test's own scope instead.

## Arm A — Sonnet

- **Model:** `claude-sonnet-5-5`
- **Started:** 2026-11-16T05:12Z (UTC)
- **Turns / duration:** 11 turns, 2.1 minutes
- **Tag:** `v1-sonnet` (branch `arm/sonnet`)
- **Prompt** (verbatim):

> Write unit tests for the checkout feature in :core and :app. Make sure they pass. The project is at <workspace>; use JDK 21 for Gradle (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

### The agent's final report (verbatim)

I added unit tests for the checkout feature in `:core` and `:app`. All 61 pass (0 failures) with `./gradlew :core:test :app:testDebugUnitTest` on JDK 21.

**`:core`** (JUnit 5, MockK, coroutines-test), in `core/src/test/kotlin/com/eliaseguizabal/checkout/`:
- `domain/PriceCalculatorTest` checks:
  - tax rounding;
  - the free-shipping threshold and whether it applies before or after the discount;
  - the percent and amount caps on coupons;
  - coupon eligibility;
  - points capping and negative points.
- `store/CheckoutReducerTest` covers each action.
- `store/MiddlewareTest` covers `CartMiddleware`, `CouponMiddleware` and `CartPersistenceMiddleware`, including the cases where they ignore an intent.
- `store/CheckoutStoreTest` runs the whole store end to end: add item, apply coupon, reject coupon, save cart.
- `data/InMemoryTest` covers the in-memory coupon repository and cart store.

**`:app`** (JUnit 4 and Robolectric with Compose UI tests), in `app/src/test/kotlin/com/eliaseguizabal/checkout/app/`:
- `MoneyTest` checks `formatCents`.
- `CheckoutUiTest` covers the checkout screen and the quote summary. It checks which rows show, the enabled and disabled states of the Apply and Save buttons, the intents each button dispatches, and the three coupon error messages.

I didn't write tests for `CheckoutViewModel` or `MainActivity`, since both are mostly wiring.

## Arm A — Haiku

- **Model:** `claude-haiku-5-5`
- **Started:** 2026-11-16T05:12Z (UTC)
- **Turns / duration:** 47 turns, 6.6 minutes
- **Tag:** `v1-haiku` (branch `arm/haiku`)
- **Prompt** (verbatim):

> Write unit tests for the checkout feature in :core and :app. Make sure they pass. The project is at <workspace>; use JDK 21 for Gradle (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

### The agent's final report (verbatim)

I added 92 unit tests across `:core` (61) and `:app` (31). All pass with JDK 21, and `./gradlew :core:test :app:testDebugUnitTest` is green. Nothing is committed; the new files are untracked under `core/src/test/` and `app/src/test/`.

**What's covered**

- **`:core`**
  - `PriceCalculator`: subtotal, tax rounding, shipping threshold (5000 vs 5001), percent and amount coupons with caps and minimums, loyalty points clamping.
  - `CheckoutReducer`: each action's state change and requote.
  - `CartMiddleware`, `CouponMiddleware`, `CartPersistenceMiddleware`: each intent, error mapping, the in-flight coupon guard, and the clear-quote-then-save ordering.
  - `CheckoutStore`: end-to-end flows on the real reducer and middlewares.
  - `InMemoryCouponRepository` and `InMemoryCartStore`.
- **`:app`**
  - `formatCents`.
  - `CheckoutViewModel`, including the demo-cart seed.
  - `QuoteSummary` and `CheckoutScreen` under Robolectric. These check rendering, intents emitted by taps and typing, and the points field filtering non-digits.

I hand-computed the expected values from the formulas rather than copying the code's output. To check the suite isn't vacuous, I temporarily changed the tax rate from 13 to 12: 12 core tests failed. I then restored the file, and the tree is clean apart from the new test directories.

**Decisions to review**

- The Compose screen tests scroll the lazy list to reach the "Save cart" button and the discount row, which sit below the fold in Robolectric's viewport. I didn't change any app code for this.
- I used JUnit 5 for `:core` and JUnit 4 for `:app`, matching each module's declared dependencies.

**Possible bug, not tested**

`CheckoutReducer` never clears `appliedCoupon`. If a coupon is applied, the cart is then edited below that coupon's minimum, and the UI still shows "CODE applied" while the discount silently becomes 0. I didn't write a test that enshrines either behavior. This needs a product decision on whether it should clear the coupon.

## Arm B — tests as part of a feature

- **Model:** `claude-opus-5-5`
- **Started:** 2026-11-16T05:13Z (UTC)
- **Turns / duration:** 13 turns, 3.1 minutes
- **Tag:** `v1-feature` (branch `arm/feature`)
- **Prompt** (verbatim):

> Implement this ticket in the checkout feature and add tests.
>
> Coupon validation improvements:
> 1. If the code is valid but the cart subtotal is below the coupon's minimum, reject it with a new `CouponError.BELOW_MINIMUM`.
> 2. If the coupon lookup fails with a network error, reject it with a new `CouponError.NETWORK` instead of `NOT_FOUND`.
> 3. When a code is rejected, keep the coupon that was already applied, and its discount.
> 4. Ignore a second Apply while a code is still being validated.
>
> Show a message for each error on the checkout screen.
>
> The project is at <workspace>; use JDK 21 for Gradle (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

### The agent's final report (verbatim)

I've implemented all four ticket items and added messages to the checkout screen. All 20 new tests pass: 14 in `core` and 6 Robolectric UI tests in `app`. Nothing is committed yet.

**What changed**
1. **Below minimum:** `CouponMiddleware` checks the cart subtotal against the coupon's minimum after a successful lookup. If it's too low, the code is rejected with `CouponError.BELOW_MINIMUM`. The check uses the cart as it is when the lookup finishes. A subtotal exactly at the minimum is accepted.
2. **Network error:** a failed lookup now gives `CouponError.NETWORK`. An unknown code still gives `NOT_FOUND`.
3. **Keep the applied coupon:** the reducer's `CouponRejected` branch now only sets the error and stops the spinner. The coupon that was already applied, and its discount, stay as they were.
4. **Ignore a second Apply:** the middleware holds an atomic flag while a lookup is running. Extra Apply requests do nothing, and the flag is cleared in a `finally` block so it never gets stuck on.
5. **Screen:** each error has its own message. The error and the "SAVE10 applied" line now both show, because a rejected code no longer removes the coupon. Before, only one of them could show.

I also added `PriceCalculator.subtotal(items)` so the price calculation and the minimum check use the same subtotal.

**Tests**
- `core/src/test/.../CouponValidationTest.kt` uses the real store, reducer and middleware with a fake repository whose lookups I can pause. It covers each error, the exact-minimum and one-cent-below cases, and keeping the coupon after each of the three errors. It also checks that a second Apply during a lookup is ignored, and that Apply works again once the lookup finishes. One test sends 200 Apply requests at once across several threads and checks that only one lookup starts.
- `app/src/test/.../CheckoutScreenCouponTest.kt` checks each error message, that the error and the applied coupon show together, and that Apply sends the typed code.

To check that the core tests can actually fail, I undid each fix one at a time. Removing the minimum check, the network mapping, the keep-coupon change, the Apply guard, or the guard reset each broke 2–5 tests. One case didn't fail: guarding with a plain `state().isValidatingCoupon` check instead of the atomic flag. Two Apply requests would have to land in a tiny window for that to go wrong, and the 200-request test didn't hit it. The atomic flag rules that case out, but no test proves it's needed.

**One thing to know:** if the screen is closed while a lookup is running, the state can be left showing "validating". This was already true before my change and doesn't matter in the current app, because the state goes away with the screen.
