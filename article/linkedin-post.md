I set out to prove that AI agents write unit tests that can't fail. The data proved me wrong.

I built a small Android checkout with 9 business rules and 15 planted bugs, then had fresh Claude Code agents write the tests without seeing the bugs.

→ Opus, with a plain "write unit tests" prompt: 88 tests, all 15 bugs caught. It even mutation-tested its own suite, unasked.
→ Haiku: 14 of 15. Sonnet: 13 of 15. (One run each; not a benchmark.)
→ Line coverage: 96.9% for both Opus and Haiku. It couldn't tell them apart.

The bug that slipped past both smaller models: "a rejected coupon code keeps the coupon already applied". Their tests started from a cart with no coupon, so dropping it changed nothing.

Then I asked an agent to break every rule itself. It fixed the other gap, not this one: the rule is enforced by code that *doesn't* touch the coupon, so breaking it means adding code, not flipping a line.

The weak tests I keep finding in real projects come from the same place: rules that live in tickets and heads, not in code.

The one question for every test: if this rule broke tomorrow, would anything go red?

Full write-up: ARTICLE_URL
Code, prompts and every result: https://github.com/eliasem-ka/tests-that-cant-fail

#AndroidDev #Kotlin #Testing
