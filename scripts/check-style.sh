#!/usr/bin/env bash
#
# check-style.sh - writing-style guard.
#
# Fails when a file this repository publishes holds a character that our style
# forbids. Run it before every commit.
#
# Exit codes:
#   0  the tree is clean
#   1  a forbidden character was found
#   2  the check itself could not run
#
# It reads the tracked files, and not the whole working tree. A local note, a
# build directory and an editor's own file are not published, and a guard that
# fails on them is a guard people learn to skip.
#
# The forbidden characters are written as escapes, so that this file does not
# hold the thing it bans, and the exit code of grep is read rather than hidden:
# a check that swallows a failure reports success over a real violation.

set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

# U+2014 EM DASH and U+2013 EN DASH, as escapes so that this file holds neither.
em_dash=$'\u2014'
en_dash=$'\u2013'

hits="$(git ls-files -z | xargs -0 grep -n "[${em_dash}${en_dash}]" --binary-files=without-match)"
status=$?

# xargs answers 123 when one grep in the batch found nothing, which is the usual
# case here. Anything else that is not 0 or 1 is the check itself failing.
if [ "$status" != 0 ] && [ "$status" != 1 ] && [ "$status" != 123 ]; then
    echo "check-style: the search failed with status ${status}" >&2
    exit 2
fi

if [ -n "$hits" ]; then
    echo "Forbidden long dash. Use a colon, a comma, a full stop, or brackets."
    echo "$hits" | sed 's/^/  /'
    exit 1
fi

echo "check-style: clean"
exit 0
