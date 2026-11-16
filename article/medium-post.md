# I tried to catch AI agents writing tests that can't fail. The bug that got through lives in code that isn't there.

*Six AI-written test suites, 15 planted bugs, and why line coverage couldn't tell the suites apart.*

---

A few months ago I reviewed an epic built mostly by AI coding agents in one of my side projects: 21
commits, a handful of tasks, a different agent session for each. The suite was green and coverage
looked healthy. Then I started breaking the code on purpose.

Four tests couldn't fail. One per task, each written by a different agent. One was named after a
field the data "travels in", and its only check was that a word appeared *somewhere* in the raw JSON
string, so it passed with the data in a field that didn't exist. The central rule of one step lived
inline in a composable with no assertion at all. A new field went to the local database and back
with no assertion in either direction. Across that review I made 26 deliberate breaks: 20 turned
some test red, and 6 exposed production lines that nothing protected.

It wasn't the first time. On a large production app I work on, a release audit found tests with high
line coverage that stayed green with the bug fix reverted, and fixes that had shipped with no test
at all. And when I asked a code-review agent for a bug hunt, it marked ten findings as confirmed or
plausible. Fixing them one by one, red test first, one "confirmed" race never reproduced and another
finding cited a guard function that doesn't exist in that module. Six of the others were real.

So I had a hypothesis, and I wanted numbers for it: **AI agents write tests that pass whether the
code is right or not.** I built a small Android project to measure it.

The hypothesis was mostly wrong. What I found instead is more useful.

## The only question that matters about a test

> If this rule broke tomorrow, would any test go red?

Line coverage doesn't answer it. Kover and JaCoCo mark a line as covered when a test *executes* it,
not when a test *checks* what it does. A test that calls `quote()` and asserts nothing covers every
line of `quote()`.

The direct way to answer the question is a mutation: break the line that enforces the rule, run the
tests, and look. If a test fails, the rule is protected (the mutant is **killed**). If everything
stays green, nothing protects it (the mutant **survived**).

## The setup

The demo is a checkout screen: Kotlin, Jetpack Compose, a small hand-rolled MVI store, money in
`Long` cents. It has nine business rules, the kind every app has:

- a coupon applies when the subtotal is **at least** its minimum;
- a percentage discount is capped;
- neither a fixed coupon nor loyalty points can pay more than what's owed;
- tax is charged **after** the discount;
- shipping is free **above** $50;
- when a code is rejected, the error shows and **the coupon already applied stays**;
- a second "Apply" while one is in flight is ignored;
- saving the cart clears the cached quote first;
- the discount row shows only when there's a discount.

For those rules I wrote 15 mutations, each a one-line patch on the line that enforces one edge of a
rule: `>=` becomes `>`, a `minOf` cap disappears, two calls swap, a Compose argument with a default
value is deleted. A small script, `mutate.sh`, applies each patch, runs the tests, reports KILLED or
SURVIVED and restores the code.

Before trusting the catalog, I had to prove every mutation is a real bug and not a change nothing
can observe. I wrote the production code test-first against my own suite, which I call the
oracle, and it catches all 15. Then I took it out of the repo. No agent ever saw it, and it isn't
in any number below.

Each test suite was written by a fresh, headless Claude Code session (`claude -p`) with none of my
plugins, hooks or skills, working in a throwaway copy of the repo that had no mutation catalog, no
PIT configuration and no git history to dig through. One run per suite, no re-rolls. The protocol was
committed before each run, including the two arms I added halfway; one follow-up run, marked below,
was decided after seeing results.

## Result one: I was wrong

The first suite came from the most capable model, Opus, with the prompt most of us type:

> Write unit tests for the checkout feature in :core and :app. Make sure they pass.

Seven minutes later it had written 88 tests. **They caught all 15 mutations.** Its PIT score was
higher than my oracle's. And this line in its report surprised me more than the numbers:

> I put 21 bugs into the production code one at a time, and each one made at least one test fail.

Nobody asked it to. It mutation-tested its own suite unprompted.

That killed my headline, so I added two arms to find where things do break: the same prompt with
two smaller models, and a more realistic task, a ticket to implement "and add tests", since that's
where most tests come from in real work.

| Suite | Tests | Mutations caught | PIT | Line coverage |
|---|---|---|---|---|
| Opus, "write unit tests" | 88 | **15/15** | 77% | 96.9% |
| Haiku, "write unit tests" | 92 | 14/15 | 79% | 96.9% |
| Sonnet, "write unit tests" | 61 | 13/15 | 76% | 88.9% |
| Opus, ticket + tests | 20 | 6/15 (6/6 in scope) | 46% | 74.0% |

Two things stand out before looking at any test.

**Coverage didn't separate the suites.** Opus and Haiku both cover 96.9% of lines. One lets a bug
through, the other doesn't.

**PIT didn't separate them much either.** All three "write tests" suites land between 76% and 79%,
and my oracle, which caught all 15 bugs, scored lower: 68%. Most of PIT's survivors sit in the state
machines Kotlin generates for `suspend` functions and in in-memory fakes the tests never touch. PIT
clearly separated the narrow ticket suite (46%) from the rest, but among suites that cover most of
the code it barely moved.

The ticket arm's 9 survivors aren't a quality problem: they're rules the ticket never touched, and
nobody asked for tests there. Inside its scope it caught every mutation.

## The two bugs that got through

Both are patterns I had seen in real code.

**Data that passes with or without the bug.** Sonnet and Haiku both tested the rejection branch like
this:

```kotlin
// arm/sonnet — CheckoutReducerTest.kt
@Test fun `CouponRejected sets error and clears flag`() {
    val s = reducer.reduce(CheckoutState(isValidatingCoupon = true), CheckoutAction.CouponRejected(CouponError.NOT_FOUND))
    assertEquals(CouponError.NOT_FOUND, s.couponError)
    assertFalse(s.isValidatingCoupon)
}
```

The rule says a rejected code *keeps the coupon already applied*. This test starts with no coupon
applied, so dropping the coupon changes nothing it can see. The mutation that clears the applied
coupon and re-prices the cart survives. The fix is one line of setup: start from a state where
SAVE10 is applied and assert it's still there, with the same total, after the rejection.

**Compose wiring through a default argument.** The checkout screen decides whether to show the
discount row:

```kotlin
QuoteSummary(quote = state.quote, showDiscountRow = state.quote.discountCents > 0)
```

`showDiscountRow` defaults to `false`. Delete the argument and everything still compiles; the row
silently never appears. Sonnet's suite does test the row, but by rendering `QuoteSummary` on its own
and passing the flag by hand:

```kotlin
// arm/sonnet — CheckoutUiTest.kt
rule.setContent { QuoteSummary(quote, showDiscountRow = true) }
// …
rule.onNodeWithTag("discountRow").assertIsDisplayed()
```

It renders `CheckoutScreen` in ten other tests, so the line that decides the flag runs and counts as
covered. None of those ten uses a quote with a discount and then looks for the row. The line is
executed, covered and unchecked.

Neither is a missing test, and neither shows up as a coverage gap. Both are tests aimed at the wrong
thing.

## Asking the agent to prove its own tests

The fix I had in mind was to put the mutation check in the prompt. A fresh agent got the existing
suite and this instruction (abridged; the full prompt is in the repo):

> For every business rule the production code enforces: (1) find the line of production code that
> makes the rule true; (2) change that line to break the rule; (3) run the tests and confirm at least
> one test fails on an assertion about that rule; (4) if everything stays green, fix or add a test
> until one fails; (5) restore the production line exactly. Tests must assert observable outcomes, not
> only that a mock was called.

The protocol put this run on the ticket arm, which had the most survivors. Opus took that suite from
6 to 15 of 15, but that mostly measures writing tests for rules the ticket never touched (its first
suite, with the plain prompt, had already caught all 15). It did run 98 mutations of its own and
flagged a real gap I hadn't noticed: the ViewModel's factory builds the cart store internally, so
nothing can test that tapping Save actually saves.

The more telling run is one I added after seeing the arm results: the same prompt on Sonnet's suite.
104 self-run mutations later, the discount-row gap was fixed. It broke the screen's argument, saw
nothing fail, and added a test that renders the screen with a discount:

```kotlin
// v2-sonnet — CheckoutUiTest.kt
val withDiscount = CheckoutState(items = listOf(CartItem("a", 1_000, 1)), quote = Quote(1_000, 500, 65, 599, 0, 1_164))
rule.setContent { CheckoutScreen(withDiscount, {}) }
rule.onNodeWithTag("discountRow").assertIsDisplayed()
```

Its report said every rule was now caught except one equivalent mutant.

**The rejection bug still survived.**

Look at the reducer branch that enforces "a rejected code keeps the coupon already applied":

```kotlin
is CheckoutAction.CouponRejected ->
    state.copy(couponError = action.error, isValidatingCoupon = false)
```

The rule is enforced by what that line **doesn't** do: it never touches `appliedCoupon`. Breaking
it means *adding* code (clear the coupon, re-price), while every example in my prompt (flip, drop,
swap, return the default) changes or removes code that's already there. Sonnet broke what it could
see, the error and the flag, watched both get caught, and moved on.

It isn't invisible to every agent. Opus caught this bug in its first suite with the plain prompt, and
the Opus agent on the ticket arm broke the rule on purpose by adding exactly that code, because its
ticket said it out loud: *"When a code is rejected, keep the coupon that was already applied."* My
read, from one run each, is that rules enforced by omission are the easiest to miss, for an agent
following a line-by-line recipe and for a person reviewing the diff.

And coverage didn't move: Sonnet's suite went from 13 to 14 caught bugs at exactly the same 88.9%.

## What this changed in how I work

The capable models don't write fake tests when the task is clean and the rules are visible in the
code. The weak tests I keep finding in real projects come from the same place as this survivor:
rules that live in a ticket, a product decision or someone's head, not in a line of code.

What I do now:

1. **Ask "what would make this test fail?" before accepting it.** If nobody can name the production
   change, the test has no target.
2. **Break the enforcing line, then look.** Flip the comparison, drop the call, swap the order,
   restore with `git checkout`. Five minutes per rule, and it's the only check that measures what a
   test is for.
3. **Write the rules down, especially the "must not change" ones.** "Keeps the previous coupon",
   "doesn't reset the form", "doesn't re-fetch": rules enforced by omission have no line to flip, and
   breaking them means adding code. Put them in the ticket or the prompt, and test them from a state
   where the thing exists.
4. **Test the composable that decides, not the one that renders.** Any parameter with a default
   value is a wire that can be cut without a compile error.
5. **Read coverage as "what never ran", nothing more.** A line at 0% is unprotected. A line at 100%
   tells you nothing.
6. **Put the mutation step in the prompt, and name the omission rules.** It costs a few minutes of
   agent time. In this experiment it closed the Compose wiring gap; it didn't find the rule that
   only a ticket stated.

## Caveats

This is one small, clean codebase with nine rules, one run per suite, and only Claude models. The
ticket was written by me, and the mutations are mine: the catalog was frozen before the first agent
ran and proven against my oracle (two were later ported to the ticket arm's own code, same change on
its version of the line). A large legacy codebase with rules nobody wrote down would almost
certainly look worse, and that's the next experiment worth doing.

## Run it yourself

Everything is public: the app, the 15 mutations, `mutate.sh`, every prompt verbatim, every agent's
own report, and the results per tag.

```bash
git clone https://github.com/eliasem-ka/tests-that-cant-fail
cd tests-that-cant-fail
git checkout v1-sonnet
./scripts/mutate.sh --all
```

You'll see the two survivors above, and which test catches each of the others.

**Repo:** https://github.com/eliasem-ka/tests-that-cant-fail
