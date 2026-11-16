# Experiment protocol

Written and committed **before** any test suite was generated. The tags in this repo are the record:

| Tag | What it contains |
|---|---|
| `v0-no-tests` | Production code, the mutation catalog, `scripts/mutate.sh`, this protocol. No tests. |
| `v1-ai-suite` | The tests an AI agent wrote from a plain "write unit tests" prompt, as delivered. |
| `v2-agent-proves-tests` | The same tests after a second agent was asked to prove each one by breaking the code. |

## The question

If a business rule in the code broke, would any test notice?

Each of the 15 mutations in [`mutations/`](../mutations/README.md) breaks one rule with one small,
realistic change (a flipped comparison, a dropped call, two calls swapped, a removed argument).
A suite is measured by how many of those it catches (**KILLED**) and how many it lets through
(**SURVIVED**). Line coverage (Kover) and PIT's automated mutation score are reported next to it.

## v1: the AI-written suite

- **Who:** a fresh Claude Code agent session that has never seen this repo, working agentically: it
  can read the code, run Gradle and iterate until its tests pass — the way developers use agents day
  to day. The model id is recorded in [`prompts.md`](prompts.md).
- **Isolation:** the agent works in a throwaway copy of `v0-no-tests` with `mutations/`, `scripts/`,
  `experiment/` and `docs/` removed. It never sees the catalog, this protocol or the article.
- **Prompt:** exactly this, plus one environment sentence with the project path and the JDK to use:

  > Write unit tests for the checkout feature in :core and :app. Make sure they pass.

- **One run, final.** No re-rolls. If a run is unusable (for example, it writes no tests), that is
  reported and a second run is labelled as such.
- **Output handling:** tests are copied back exactly as delivered; only files under `*/src/test/`
  are taken. A test that fails against the correct code is deleted, listed in the results and
  excluded from the numbers (otherwise it would "catch" every mutation). Only changes needed to
  compile are allowed, and each one is listed.

## v2: the agent proves its tests

- **Who:** a *new* fresh agent session, same model, same isolation, starting from the v1 tests.
- It does **not** see the mutation catalog or the v1 results.
- **Prompt:** exactly this, plus the same environment sentence:

  > Review and improve the unit tests for the checkout feature in :core and :app. For every business rule the production code enforces: (1) find the line of production code that makes the rule true; (2) change that line to break the rule — flip a comparison, drop a call, swap two calls, return the default; (3) run the tests and confirm at least one test fails on an assertion about that rule; (4) if everything stays green, fix or add a test until one fails; (5) restore the production line exactly and confirm all tests pass again. Tests must assert observable outcomes — the resulting state, the amounts, what was saved — not only that a mock was called. Do not change production code. When you finish, list each rule, the line you broke, and the test that caught it.

- Same one-run and output-handling rules. Any production change left behind is discarded and reported.

## Measuring

On each tag, from a clean checkout:

```bash
./scripts/mutate.sh --all          # the 15 catalogued mutations
./gradlew koverXmlReport           # line coverage
./gradlew :core:pitest             # PIT mutation score (core module)
```

## Disclosure: the oracle

The production code was written test-first by the author against a private **oracle** suite. The
oracle exists for one reason: to prove that every catalogued mutation is a real, observable bug
(all 15 are caught by it). It never ran in the v1 or v2 measurements and was kept out of the
repository until the experiment was over; it is published in [`oracle/`](oracle/) afterwards.

If an article pattern does not show up naturally in v1, it may be illustrated with a clearly
labelled example under `reconstructed/`. Those examples are never counted in any number.

## Amendment: what the isolated copies leave out

Recorded during the v1 run, before any of its output was read. Besides `mutations/`, `scripts/`,
`experiment/` and `docs/`, each isolated copy also drops:

- the PIT plugin and its configuration (`core/build.gradle.kts`, the root build and the version
  catalog), because it tells the agent the project is measured with mutation testing;
- the lines of `.gitignore` that mention private planning folders and the oracle;
- the git history: the copy is a single fresh commit, so the removed files can't be found in it.

Nothing else differs from the tag. Only files under `*/src/test/` are copied back.

## Amendment: two more arms (decided after v1, before running them)

v1 caught all 15 mutations. That answers the question for one setup: the strongest model, a small
clean codebase, and a task that is only about tests. The real-world cases that motivated this repo
looked different, so two arms are added. Everything below was written and committed before either
arm ran.

### Arm A — model

The v1 prompt and isolation, unchanged, with two other models: `claude-sonnet-5-5` and
`claude-haiku-5-5`. One run each, same output-handling rules. Each lives on its own branch
(`arm/sonnet`, `arm/haiku`, cut from `v0-no-tests`) and is tagged `v1-sonnet` / `v1-haiku`.
Measured with the full catalog.

### Arm B — tests as part of a feature

In real projects most tests are written alongside a change, not in a dedicated "write tests" task.
This arm starts from `v0-no-tests` with the coupon-failure behaviour taken out
(branch `arm/feature`, tag `feature-base`):

- `CouponError` has only `NOT_FOUND`;
- `CouponMiddleware` has no in-flight guard, no minimum check, and reports every failure as
  `NOT_FOUND`;
- the reducer drops the applied coupon (and re-quotes) when a code is rejected;
- the screen shows one message for every error.

The agent (`claude-opus-5-5`, same isolation) gets this prompt, verbatim, plus the environment
sentence:

> Implement this ticket in the checkout feature and add tests.
>
> Coupon validation improvements:
> 1. If the code is valid but the cart subtotal is below the coupon's minimum, reject it with a new `CouponError.BELOW_MINIMUM`.
> 2. If the coupon lookup fails with a network error, reject it with a new `CouponError.NETWORK` instead of `NOT_FOUND`.
> 3. When a code is rejected, keep the coupon that was already applied, and its discount.
> 4. Ignore a second Apply while a code is still being validated.
>
> Show a message for each error on the checkout screen.

Its production code is its own, so the catalogued patches for the files it touches may no longer
apply. For those, the mutation is **ported**: same rule, same kind of minimal change, applied to the
line that enforces the rule in the agent's implementation. Ported patches live in
`mutations/feature-arm/` with a note pointing at the original. Patches for files it didn't touch are
used as they are. Tagged `v1-feature`.

### Where v2 runs

v2 (the "prove your tests" prompt) runs on the arm with the most surviving mutations, starting from
that arm's suite. If every arm kills all of its mutations, v2 is not run, and the article says so.
