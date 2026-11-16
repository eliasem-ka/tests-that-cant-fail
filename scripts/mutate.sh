#!/usr/bin/env bash
#
# Break one business rule on purpose and see whether any test notices.
#
#   scripts/mutate.sh M01 [M02 ...]   run the given mutations
#   scripts/mutate.sh --all           run every mutation in mutations/catalog.tsv
#   scripts/mutate.sh --check         only check that every patch still applies
#
# For each mutation: apply the patch, run the tests, report the result, restore.
#
#   KILLED         at least one test failed  -> some test protects this rule
#                  (any failing test counts; the log in build/mutate/ shows why)
#   SURVIVED       every test stayed green   -> nothing protects this rule
#   COMPILE ERROR  the build broke           -> not evidence either way
#   PATCH FAILED   the patch no longer applies to the code
#
# Override the test command with TEST_CMD and the catalog folder with MUTATIONS_DIR
# (default: mutations). Works with macOS's stock bash 3.2.

set -u
cd "$(dirname "$0")/.." || exit 1

# --no-build-cache: a cached green run must never stand in for a real one.
TEST_CMD=${TEST_CMD:-"./gradlew :core:test :app:testDebugUnitTest --continue --no-build-cache --console=plain"}
MUTATIONS_DIR=${MUTATIONS_DIR:-mutations}
CATALOG=$MUTATIONS_DIR/catalog.tsv
LOG_DIR=build/mutate
APPLIED=""

restore() {
  if [ -n "$APPLIED" ]; then
    # shellcheck disable=SC2046  # repo paths contain no spaces
    git apply -R "$APPLIED" 2>/dev/null || git checkout -- $(git apply --numstat "$APPLIED" | cut -f3)
    APPLIED=""
  fi
}
exec 3>&2  # the terminal, even while a test run's output is redirected to a log
trap restore EXIT
trap 'restore; printf "\nInterrupted - production code restored.\n" >&3; exit 130' INT TERM

patch_for() {
  for f in "$MUTATIONS_DIR"/"$1"-*.patch; do
    [ -e "$f" ] && echo "$f"
    return
  done
}

clean_results() {
  rm -rf core/build/test-results app/build/test-results
}

failed_tests() {
  # Prints the first two failing tests as "Class.test", plus how many more failed.
  files=$(find core/build/test-results app/build/test-results -name '*.xml' 2>/dev/null)
  [ -z "$files" ] && return 0
  # shellcheck disable=SC2086
  awk '
      /<testcase / {
        name = $0; sub(/.*<testcase name="/, "", name); sub(/".*/, "", name)
        cls = $0; sub(/.*classname="/, "", cls); sub(/".*/, "", cls); sub(/.*\./, "", cls)
      }
      /<failure/ { sub(/\(\)$/, "", name); print cls "." name }
    ' $files 2>/dev/null | sort -u | awk '
      NR <= 2 { out = out (NR > 1 ? "; " : "") $0 }
      END { if (NR > 2) out = out "; +" (NR - 2) " more"; print out }
    '
}

run_tests() { # $1 = log file; returns the test command's exit code
  clean_results
  eval "$TEST_CMD" >"$1" 2>&1
}

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "DIRTY TREE: commit or stash your changes first (mutations are restored with git)." >&2
  exit 1
fi

if [ $# -eq 0 ]; then
  sed -n '3,9p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
fi

IDS=""
CHECK_ONLY=false
case "$1" in
  --all) IDS=$(cut -f1 "$CATALOG") ;;
  --check) IDS=$(cut -f1 "$CATALOG"); CHECK_ONLY=true ;;
  *) IDS="$*" ;;
esac

if $CHECK_ONLY; then
  status=0
  for id in $IDS; do
    patch=$(patch_for "$id")
    if [ -n "$patch" ] && git apply --check "$patch" 2>/dev/null; then
      echo "$id ok"
    else
      echo "$id PATCH FAILED"; status=3
    fi
  done
  exit $status
fi

mkdir -p "$LOG_DIR"
echo "Baseline: running the tests on the unmutated code..." >&2
if ! run_tests "$LOG_DIR/baseline.log"; then
  echo "BASELINE RED: the tests fail before any mutation, so no result would mean anything." >&2
  echo "Failing: $(failed_tests)" >&2
  echo "Log: $LOG_DIR/baseline.log" >&2
  exit 2
fi

echo "| Mutation | Rule | What breaks | Result | Caught by |"
echo "|---|---|---|---|---|"
killed=0
total=0
skipped=0
status=0
for id in $IDS; do
  rule=$(awk -F'\t' -v id="$id" '$1 == id { print $2 }' "$CATALOG")
  desc=$(awk -F'\t' -v id="$id" '$1 == id { print $3 }' "$CATALOG")
  patch=$(patch_for "$id")
  total=$((total + 1))
  echo "Running $id..." >&2

  if [ -z "$patch" ] || ! git apply --check "$patch" 2>/dev/null; then
    echo "| $id | $rule | $desc | PATCH FAILED | |"
    skipped=$((skipped + 1))
    status=3
    continue
  fi

  APPLIED="$patch"
  git apply "$patch"
  run_tests "$LOG_DIR/$id.log"
  rc=$?
  failures=$(failed_tests)
  restore

  if ! git diff --quiet; then
    echo "Could not restore the code after $id - stopping." >&2
    exit 4
  fi

  if [ $rc -eq 0 ]; then
    echo "| $id | $rule | $desc | **SURVIVED** | - |"
  elif [ -n "$failures" ]; then
    echo "| $id | $rule | $desc | KILLED | $failures |"
    killed=$((killed + 1))
  else
    echo "| $id | $rule | $desc | COMPILE ERROR | see $LOG_DIR/$id.log |"
    skipped=$((skipped + 1))
  fi
done

echo
ran=$((total - skipped))
if [ $skipped -gt 0 ]; then
  echo "Killed $killed of $ran mutations ($skipped more did not apply or did not compile)."
else
  echo "Killed $killed of $total mutations."
fi
exit $status
