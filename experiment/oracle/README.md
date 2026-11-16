# The oracle

These are the tests the author wrote, test-first, while building the production code. They exist
for one reason: to prove that every mutation in the catalog is a real, observable bug. All 15 are
caught by this suite.

They were kept out of the repository until the experiment was over, and they never ran in any of
the measurements: no agent saw them, and no number in `experiment/` includes them.

They keep the paths they had in the project (`core/src/test/...`, `app/src/test/...`). To run them,
copy them into those folders on any tag except the feature-arm ones (`feature-base`,
`v1-feature*`, `v2-feature`), whose production code differs:

```bash
cp -R experiment/oracle/core/src/test core/src/
cp -R experiment/oracle/app/src/test app/src/
./scripts/mutate.sh --all
```

PIT on `:core` with the oracle alone: 80/117 mutants detected (68%). Most survivors are in
coroutine state machines generated for `suspend` functions and in the in-memory fakes, which the
oracle never uses.
