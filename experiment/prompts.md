# Prompts and agent reports

Each run is a fresh, headless Claude Code session started inside an isolated copy of the repo
(see [protocol.md](protocol.md)):

```bash
claude -p "<prompt>" --model claude-opus-5-5 \
  --setting-sources project,local --disable-slash-commands --no-chrome \
  --dangerously-skip-permissions --output-format json
```

Local filesystem paths inside the reports are replaced with `<agent scratchpad>`; nothing else is edited.

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

## v2 — Arm B (feature), agent proves its tests

- **Model:** `claude-opus-5-5` (same model as the arm's v1)
- **Started:** 2026-11-16T05:22Z (UTC)
- **Duration:** about 17 minutes (from the transcript)
- **Starts from:** the arm's v1 suite. **Tag:** `v2-feature`
- **Note on the report:** the session ended with a short turn triggered by a background-task notification, so the `result` field of the JSON output held only that last line ("The progress watcher has timed out…"). The report below is the agent's final substantive message, copied verbatim from the session transcript (2026-11-16 05:22–05:40 UTC).
- **Prompt** (verbatim, the same for both v2 runs):

> Review and improve the unit tests for the checkout feature in :core and :app. For every business rule the production code enforces: (1) find the line of production code that makes the rule true; (2) change that line to break the rule — flip a comparison, drop a call, swap two calls, return the default; (3) run the tests and confirm at least one test fails on an assertion about that rule; (4) if everything stays green, fix or add a test until one fails; (5) restore the production line exactly and confirm all tests pass again. Tests must assert observable outcomes — the resulting state, the amounts, what was saved — not only that a mock was called. Do not change production code. When you finish, list each rule, the line you broke, and the test that caught it. The project is at <workspace>; use JDK 21 for Gradle (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

### The agent's final report (verbatim)

I broke 98 production lines one at a time. 95 of them made at least one test fail on an assertion. The other 3 had no effect anything outside the code can see (explained under "What survived"). Production code is back to exactly what it was (`git diff -- */src/main` is empty), and the final clean run passes all 88 tests: 61 in `:core`, 27 in `:app`.

## What I changed (tests only, nothing committed)
- **Before:** only coupon validation (`CouponValidationTest`) and coupon error messages (`CheckoutScreenCouponTest`) had tests. Pricing, the reducer, the cart, saving the cart, the coupon catalog, the quote summary, money formatting and the ViewModel had none.
- **New in `:core`:**
  - `PriceCalculatorTest`: each case compares the whole `Quote` (all six amounts).
  - `CheckoutReducerTest`, `CartTest`, `CartPersistenceTest`, `InMemoryCouponRepositoryTest`.
  - `CartTest` and `CartPersistenceTest` go through the real store and check the resulting state and what was saved.
- **New in `:app`:** `QuoteSummaryTest`, `CheckoutScreenTest`, `MoneyTest`, `CheckoutViewModelTest`.
- **Added to `CouponValidationTest`:**
  - The minimum subtotal is checked against the cart as it is when the lookup returns.
  - Other intents never start a coupon lookup.
- **No mocks.** Tests use the real classes or small fakes and assert amounts, state and saved snapshots. The one call count I check is "the coupon service was not called" (C1, C2), which is itself the rule.

## Rules → line broken → test that caught it
Line numbers are from the original files.

**PriceCalculator.kt**
| Rule | Break | Caught by |
|---|---|---|
| A coupon applies when the subtotal is ≥ its minimum | L10 `>=`→`>` | `isEligible is inclusive of the minimum` |
| Subtotal = price × quantity | L12 drop `* quantity` | `subtotal is unit price times quantity…` |
| An empty cart costs $0 (no shipping) | L15 drop the early return | `empty cart costs nothing…` |
| The discount is subtracted | L19 `subtotal - discount`→`subtotal` | `amount coupon smaller than the subtotal takes off exactly its amount` |
| Tax is charged on the discounted amount | L20 `discounted`→`subtotal` | `tax is charged on the discounted amount…` |
| Tax rounds half a cent up | L20 drop `+ 50` | `tax rounds half a cent up` |
| Shipping is free only *above* $50 | L21 `>`→`>=` | `shipping is charged at exactly the free-shipping threshold` |
| Free shipping is judged after the discount | L21 `discounted`→`subtotal` | `free shipping is judged on the discounted amount` |
| Negative points are ignored | L23 drop `coerceAtLeast(0)` | `negative points are ignored` |
| Points never push the total below 0 | L23 drop `minOf(…, beforePoints)` | `points never take the total below zero` |
| Points come off the total | L31 drop `- points` | `points come off the total after tax and shipping` |
| Amounts land in the right fields | L27/28 swap tax and shipping; L30 points→0 | `percent coupon never discounts more than its cap`; `points come off the total…` |
| A coupon below its minimum gives no discount | L36 drop the `isEligible` check | `coupon whose minimum the cart no longer meets gives no discount` |
| A percent coupon is capped | L38 drop `minOf`; `minOf`→`maxOf` | `percent coupon never discounts more than its cap`; `…below its cap gives the full percentage` |
| A percent discount rounds down | L38 round up instead | `percent discount rounds fractional cents down` |
| An amount coupon never exceeds the subtotal | L39 drop `minOf` | `amount coupon never discounts more than the subtotal` |

**PricingRules.kt**
| Rule | Break | Caught by |
|---|---|---|
| Tax rate is 13% | L4 13→12 | `plain cart pays 13 percent tax…` and others |
| Free shipping threshold is $50 | L5 5000→6000 | `shipping is free one cent above the threshold` |
| Shipping fee is $5.99 | L6 599→0 | `plain cart pays 13 percent tax and the shipping fee` |

**CheckoutReducer.kt**
| Rule | Break | Caught by |
|---|---|---|
| Changing items re-prices | L8 drop `.requoted()` | `items changed replaces the items and reprices them` |
| Using points records them and re-prices | L9 drop requote; drop the points value | `points changed records the points and reprices` |
| Validation start sets "validating" and clears the old error | L10 drop `couponError = null`; drop `true` | `validation started marks validating and clears the previous error…` |
| An applied coupon re-prices and stops "validating" | L12 drop requote; drop `isValidatingCoupon = false` | `coupon applied stores the coupon, stops validating and prices in the discount` |
| A rejected code keeps the previous coupon and its discount | L15 clear the coupon and re-price | `coupon rejected … leaves coupon and price alone`; `save stores the coupon still applied…` |
| A rejection records the error and stops "validating" | L15 drop each field | `coupon rejected records the error, stops validating…` |
| Saving leaves the state unchanged | L16 return `CheckoutState()` | `cart saved changes nothing` |
| The quote uses the applied coupon | L20 pass `null` coupon | `items changed keeps the applied coupon and points in the new price` |

**CartMiddleware.kt**
| Rule | Break | Caught by |
|---|---|---|
| Adding an existing SKU merges quantities in place | L13 never find it; L18 replace instead of add | `adding a sku already in the cart merges the quantities in place` |
| A new SKU is appended at the end | L15 prepend; drop it | `adding a new sku appends it and reprices` |
| Remove drops only that SKU | L26 `filterNot`→`filter` | `removing a sku drops only that line and reprices` |
| Using points updates the state | L28 → `Unit` | `using points records them and lowers the total` |

**CartPersistenceMiddleware.kt / InMemoryCartStore.kt**
| Rule | Break | Caught by |
|---|---|---|
| Only `SaveCart` saves | L13 `!=`→`==` | `other intents neither save nor touch the cache`, plus all the save tests |
| Saving drops the stale cached quote | L15 drop `clearCachedQuote()`; Store L11 drop `cachedQuote = null` | `save drops the stale cached quote` |
| The cache is cleared *before* saving | swap L15 and L16 | `cache is cleared before saving, so the fresh save keeps its cache` |
| The saved cart has the current items and applied code | L16 code→`null`; items→empty; drop `save`; Store L15 drop the assignment | `save stores the cart as edited, with the applied coupon code` |

**CouponMiddleware.kt / CheckoutStore.kt**
| Rule | Break | Caught by |
|---|---|---|
| Only `ApplyCoupon` triggers a lookup | L21 every intent looks up `""` | `other intents never start a coupon lookup` |
| A second Apply during a lookup is ignored | L22 drop the guard | `second apply while validating is ignored` |
| "Validating" is shown and the old error cleared | L25 drop `ValidationStarted` | `next successful apply clears the error and replaces the coupon` |
| The minimum subtotal is enforced at lookup | L28 → `if (true)` | `valid code below the minimum subtotal is rejected with BELOW_MINIMUM` |
| Error kinds stay distinct | L31, L33, L34 swap the errors | `subtotal one cent below…`; `unknown code is still rejected with NOT_FOUND`; `network failure is rejected with NETWORK` |
| The guard resets after each lookup | L38 drop `set(false)` | `next successful apply…`; `apply is accepted again once validation finishes` |
| The lookup result reaches the state | L36 drop `dispatch` | `next successful apply…` |
| Middlewares read the current state, not a snapshot | Store L22 use a snapshot | `minimum is checked against the cart as it is when the lookup returns` |
| The store applies actions and runs every middleware | Store L28 drop `update`; L21 `take(1)` | many tests, e.g. `CartTest`, `CartPersistenceTest` |

**InMemoryCouponRepository.kt**
| Rule | Break | Caught by |
|---|---|---|
| Codes match ignoring spaces and case | L18 drop `trim`; drop `uppercase` | `codes are matched ignoring surrounding spaces and case` |
| OFFLINE matches after normalisation | L19 `normalized`→`code` | `OFFLINE simulates a network failure in any case and spacing` |
| An unknown code is NotFound | L20 → `NetworkError` | `unknown code is not found` |
| Catalog terms | L11, L12, L13 change percent, cap, amount | `catalog holds the three published coupons with their terms` |

**:app**
| Rule | Break | Caught by |
|---|---|---|
| Money shows two cent digits | Money L6 `%02d`→`%d` | `MoneyTest` |
| The discount row shows only when asked, with a "-" | QuoteSummary L31 `true`; L34 drop "-" | `discount row is hidden unless asked for`; `shows every amount of the quote` |
| Shipping reads "Free" only when it is 0 on a non-empty cart | L41 drop `subtotal > 0`; drop "Free" | `empty cart shows zero shipping, not Free`; `zero shipping on a non-empty cart reads Free` |
| The points row shows only when points > 0, with a "-" | L43 `>=`; L44 drop "-" | `points row is hidden when no points are used`; `points row shows the points taken off` |
| Tax and total show the right amounts | L38, L47 wrong field | `shows every amount of the quote` |
| Remove sends that row's SKU | Screen L55 first SKU | `remove sends the sku of the row it belongs to` |
| The discount row shows only when discount > 0 | L67 `>=` | `discount row is hidden when the quote has no discount` |
| Save is disabled for an empty cart and sends `SaveCart` | L73 `true`; L71 no-op | `save is disabled for an empty cart`; `save sends SaveCart when the cart has items` |
| Apply is disabled while validating or with a blank code | L113 drop each condition | `while validating the button shows a spinner and ignores taps`; `apply is disabled until a non-blank code is typed` |
| A spinner shows while validating | L114 `if (false)` | `while validating the button shows a spinner…` |
| The error and the applied code are shown | L119, L120 `if (false)` | `shows network message`; `rejection still shows the coupon that remains applied` |
| The points field keeps digits only; empty sends 0 | L130 no filter; L136 `onUse(0)` and `?: -1` | `points field keeps only digits and Use sends them`; `Use with an empty points field sends zero` |
| A cart row shows the line total | L91 drop `* quantity` | `cart row shows quantity, unit price and line total` |
| Error messages are distinct; Apply sends the code | L141 duplicate message; L62 no-op | `shows not found message`; `apply sends the typed code` |
| The ViewModel forwards intents | VM L26 → `Unit` | `onIntent reaches the store and changes the state` |
| The app starts with the demo cart and supports coupons | VM L48 drop the seeding; L44 drop `CouponMiddleware` | `app starts with the demo cart priced`; `app wires coupon lookup into checkout` |

## What survived
- **CartMiddleware L25** (the early return when removing a SKU that isn't in the cart): without it the reducer re-prices the same items, and the state comes out equal. Nothing observable changes, so no test can catch it.
- **CartPersistenceMiddleware L17** (`dispatch(CartSaved)`): the reducer returns `CartSaved` as the same state (that's tested), so dropping the dispatch changes nothing observable.
- **CheckoutViewModel L45** (adding `CartPersistenceMiddleware` to the app): this one is a real gap. The `InMemoryCartStore` is created inside the factory, and no test can reach it without changing production code. Tapping Save in the app could stop saving and no test would notice. Injecting the cart store into the factory would make this testable.

Notes:
- **Undocumented contract:** the clear-before-save rule relies on a test fake that caches a quote on save, which is what `CartStore`'s doc comment implies ("the quote cached from the last save"). `InMemoryCartStore` doesn't cache on save, so with it the order makes no difference.
- **Screen size:** the screen tests run on a tall Robolectric screen (`w411dp-h2000dp`) so the whole `LazyColumn` is laid out. On the default screen size, rows below the fold don't exist and their assertions would fail for the wrong reason.

The mutation harness is `<agent scratchpad>`. Per-mutation results, including every test that failed, are in `results.json` in the same folder.

## v2 — Arm A (Sonnet), agent proves its tests

- **Model:** `claude-sonnet-5-5` (same model as the arm's v1)
- **Started:** 2026-11-16T05:22Z (UTC)
- **Duration:** 9.4 minutes, 18 turns
- **Starts from:** the arm's v1 suite. **Tag:** `v2-sonnet`

- **Prompt** (verbatim, the same for both v2 runs):

> Review and improve the unit tests for the checkout feature in :core and :app. For every business rule the production code enforces: (1) find the line of production code that makes the rule true; (2) change that line to break the rule — flip a comparison, drop a call, swap two calls, return the default; (3) run the tests and confirm at least one test fails on an assertion about that rule; (4) if everything stays green, fix or add a test until one fails; (5) restore the production line exactly and confirm all tests pass again. Tests must assert observable outcomes — the resulting state, the amounts, what was saved — not only that a mock was called. Do not change production code. When you finish, list each rule, the line you broke, and the test that caught it. The project is at <workspace>; use JDK 21 for Gradle (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

### The agent's final report (verbatim)

I mutation-tested every rule in `:core` and `:app`. All of them are now caught by at least one failing assertion, apart from one mutation (st2) that I judged equivalent. Production code is untouched (`git diff` on `src/main` is empty). The full suite, run with `--rerun-tasks`, passes.

I ran 104 mutations in total, each applied alone, run, and restored with `git checkout`. The harness and logs are in `<agent scratchpad>`. The first pass left 8 survivors and 1 mutation that didn't compile (co1). After the test changes, all of those are caught except st2.

## Test changes
- **`InMemoryTest`**: added a `BIG20` test (20%, minimum 10,000, cap 2,500). Nothing covered that coupon before.
- **`MiddlewareTest`**:
  - The coupon repository is now a fake keyed by code, so the test fails if the wrong code is looked up. Before, `find(any())` hid it.
  - The persistence tests used `coVerifyOrder` mocks. They now use a fake store that starts with a stale cached quote and asserts three outcomes: the snapshot saved, the stale quote cleared before the save, and `CartSaved` dispatched.
  - The "ignores other intents" tests now try several intent types and assert no actions and no repository lookups.
- **`CheckoutUiTest`**: added tests for these rules:
  - **Discount row:** the screen shows it only when there is a discount.
  - **Apply while validating:** the button is disabled and shows a spinner.
  - **No spinner otherwise:** the spinner is absent when not validating.
  - **Error message:** the error is shown ahead of "X applied".
  - **Default state:** no coupon message appears by default.

## Rules, the line broken, and the test that caught it
Line numbers are in the production files.

| Rule | Line broken | Caught by |
|---|---|---|
| Empty cart gives `EMPTY` quote | `PriceCalculator:13` removed | `PriceCalculatorTest.empty cart yields EMPTY quote` |
| Subtotal = price × qty | `:15` dropped `* quantity` | `subtotal sums quantity times price…` |
| Discount reduces taxable amount | `:17` `subtotal - discount` → `subtotal` | `amount coupon is capped at subtotal`, `free shipping is evaluated after discount` |
| Tax rounds half up | `:18` removed `+ 50` | `tax rounds half up` |
| Tax on discounted amount | `:18` `discounted` → `subtotal` | `amount coupon is capped at subtotal` |
| Free shipping strictly above threshold | `:19` `>` → `>=` | `shipping is free only strictly above threshold` |
| Free shipping judged after discount | `:19` `discounted` → `subtotal` | `free shipping is evaluated after discount` |
| Shipping fee applies when not free | `:19` swapped `0L`/`SHIPPING_FEE_CENTS` | `shipping is free only…`, `points reduce total` |
| Total includes tax and shipping | `:20` dropped each | `no coupon below free shipping threshold…`, `points are capped…` |
| Negative points ignored | `:21` removed `coerceAtLeast(0)` | `negative points are ignored` |
| Points capped at amount owed | `:21` removed `minOf` | `points are capped at amount owed` |
| Points reduce total | `:29` `beforePoints - points` → `beforePoints` | `points reduce total` |
| Coupon minimum is inclusive | `:10` `>=` → `>` | `isEligible is inclusive of minimum` |
| Ineligible coupon gives no discount | `:34` dropped `isEligible` check | `ineligible coupon gives no discount` |
| Percent math and cap | `:36` `/100` → `/10`, cap removed | `percent coupon applies percentage`, `percent coupon is capped` |
| Amount coupon capped at subtotal | `:37` removed `minOf` | `amount coupon is capped at subtotal` |
| Tax 13%, threshold 5,000, fee 599 | `PricingRules:4-6` constants | PriceCalculator tests (`tax rounds half up`, `shipping is free only strictly above threshold`, `no coupon below free shipping threshold…`) |
| Reducer: items, points, requote | `CheckoutReducer:8-9`, `:19` | `ItemsChanged stores items and requotes`, `PointsChanged requotes with points` |
| Validation start sets flag and clears error | `:10` | `validation started sets flag and clears error` |
| Coupon applied: store, clear flag, requote | `:12` | `CouponApplied sets coupon, clears flag, requotes` |
| Coupon rejected: store error, clear flag | `:14` | `CouponRejected sets error and clears flag` |
| `CartSaved` leaves state unchanged | `:15` | `CartSaved leaves state unchanged` |
| Add appends a new sku | `CartMiddleware:15` | `AddItem appends new sku` |
| Add merges quantity for an existing sku | `:18` | `AddItem merges quantity for existing sku` |
| Remove drops only that sku | `:26` | `RemoveItem removes sku` |
| Remove of an unknown sku does nothing | `:25` removed | `RemoveItem unknown sku dispatches nothing` |
| `UsePoints` carries the cents | `:28` | `UsePoints dispatches PointsChanged` |
| Coupon ignores other intents | `CouponMiddleware:17` | `coupon middleware ignores other intents` (new) |
| Ignore while a validation is running | `:18` removed | `apply while validating is ignored` |
| Validation-started is dispatched | `:20` removed | `eligible coupon is applied` |
| Eligibility uses the quote subtotal | `:23` → `true` | `coupon below minimum is rejected` |
| Error mapping (below minimum, not found, network) | `:26`, `:28`, `:29` | `coupon below minimum is rejected`, `not found is rejected`, `network error is rejected` |
| Typed code is looked up | `:21` `find("")` | `the typed code is what gets looked up` (new) |
| Final action is dispatched | `:31` removed | `eligible coupon is applied`, `CheckoutStoreTest.applying coupon end to end` |
| Persistence only on `SaveCart` | `CartPersistenceMiddleware:13` | `persistence ignores other intents` (rewritten) |
| Cached quote cleared before saving | `:15` removed, then swapped with `:16` | `SaveCart drops stale quote before saving…` (rewritten) |
| Snapshot holds items and coupon code | `:16` | `SaveCart drops stale quote…`, `SaveCart without coupon saves null code` |
| `CartSaved` dispatched | `:17` removed | `SaveCart drops stale quote…` |
| Store runs all middlewares | `CheckoutStore:21` | `CheckoutStoreTest` (3 tests) |
| Store applies the reducer | `:28` | `CheckoutStoreTest` (3 tests) |
| Middleware sees live state | `:22` | `save cart persists snapshot` |
| Repo: trim, case-insensitive, `OFFLINE`, unknown, latency | `InMemoryCouponRepository:17-20` | `InMemoryTest` (5 tests) |
| `SAVE10` and `FIVEOFF` definitions | `:11`, `:13` | `finds known coupon…`, `finds amount coupon` |
| `BIG20` definition | `:12` | `finds BIG20 coupon` (new) |
| Cart store clears quote and saves | `InMemoryCartStore:11`, `:15` | `cart store saves and clears quote` |
| Money formatting | `Money:6` | `MoneyTest.zero`, `padsCents` |
| "Free" shipping only for a non-empty cart | `QuoteSummary:41` | `emptyQuoteShowsPaidShippingNotFree` |
| Points row, discount row, tax and total values | `QuoteSummary:31-47` | `quoteSummaryShowsAllRows`, `quoteSummaryHidesOptionalRows` |
| Discount row only when discount > 0 | `CheckoutScreen:587` → `true` / `false` | `screenHidesDiscountRowWithoutDiscount`, `screenShowsDiscountRowOnlyWhenThereIsADiscount` (new) |
| Save disabled on an empty cart | `:593` | `saveCartDisabledWhenEmptyEnabledOtherwise` |
| Remove dispatches the item's sku | `:575` | `screenListsItemsAndRemoveDispatches` |
| Apply disabled while validating | `:633` | `applyIsDisabledAndShowsSpinnerWhileValidating` (new) |
| Apply disabled for a blank code | `:633` | `applyCouponDisabledUntilTextEntered` |
| Spinner while validating | `:634` | `applyIsDisabledAndShowsSpinnerWhileValidating` (new) |
| Error shown ahead of "applied" | `:639-640` swapped | `errorTakesPrecedenceOverAppliedCouponMessage` (new) |
| Points field takes digits only; empty means 0 | `:651`, `:657` | `usePointsDispatchesDigitsOnly`, `usePointsWithEmptyFieldDispatchesZero` |
| Cart row line total; error text | `:611`, `:662` | `screenListsItemsAndRemoveDispatches`, `notFoundErrorShown` |

## Not tested
**st2 (middleware order)** is the one mutation that survives. I reversed the order in `CheckoutStore:21` and nothing fails. I treated it as an equivalent mutant, because each intent is handled by exactly one middleware, so order changes no outcome. If you want order pinned anyway, say so. It would need a contract that doesn't exist yet.
