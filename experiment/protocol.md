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
