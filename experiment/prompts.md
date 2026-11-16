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
