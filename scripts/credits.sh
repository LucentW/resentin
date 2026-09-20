#!/bin/sh
# credits.sh — writes app/src/main/assets/credits/credits-data.js, the
# build's git credits payload consumed by the /credits easter egg
# (assets/credits/creditsText.js's buildResentinBlock()).
#
# Mirrors grappa-irc's infra/packaging/credits.sh (that project is Resentin's
# server counterpart, and its own credits roll — replicated in
# assets/credits/ — uses the exact same mechanism): a build-time script
# rather than something baked in by the app itself, because the contributor
# roll is a fact about THIS repo's git history and a WebView asset has no
# git of its own to ask.
#
# Run by the "resentin-credits" step in .github/workflows/build.yml, with
# `fetch-depth: 0` on checkout so `git shortlog` sees the full history — a
# shallow clone would silently bake a near-empty roll. Safe to run locally
# too (`./scripts/credits.sh`) for a `./gradlew assembleDebug` that carries
# real contributor names instead of the committed placeholder.
#
# POSIX sh: nothing here needs bash, and matching credits.sh keeps the two
# scripts easy to compare side by side.
set -eu

# shellcheck disable=SC1007
SCRIPT_DIR="$(CDPATH= cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1007
REPO_ROOT="$(CDPATH= cd "${SCRIPT_DIR}/.." && pwd)"
OUT="${REPO_ROOT}/app/src/main/assets/credits/credits-data.js"

sha=""
date=""
shortlog=""
shallow=""

# `.git` absent (a source tarball, not how this repo ships today but the
# same honest-degrade posture as grappa's script) yields nulls/empty rather
# than failing the build.
if [ -e "${REPO_ROOT}/.git" ]; then
	sha="$(git -C "${REPO_ROOT}" rev-parse --short HEAD 2>/dev/null || true)"
	date="$(git -C "${REPO_ROOT}" log -1 --format=%cI 2>/dev/null || true)"
	# grappa-irc issue 1773: on a SHALLOW clone, `shortlog` aggregates over
	# history it doesn't have, and answers CONFIDENTLY WRONG rather than
	# emptily — it credits the whole roll to whoever authored the one commit
	# that happened to be fetched, in the exact shape a correct answer has.
	# CI passes `fetch-depth: 0` (see the header comment) so this never fires
	# there; the gate exists for the local run this script's own docstring
	# invites, where a shallow clone is plausible. sha/date stay: a shallow
	# repo knows both exactly, and nulling them would claim no history at
	# all, a different (and false) fact. `--is-shallow-repository` needs git
	# 2.15+; an older git leaves this empty, which reads as not-shallow and
	# preserves the previous (ungated) behavior exactly.
	shallow="$(git -C "${REPO_ROOT}" rev-parse --is-shallow-repository 2>/dev/null || true)"
	if [ "${shallow}" != "true" ]; then
		# --no-merges: a merge commit must not credit the merger with the
		# work of whoever authored the branch.
		shortlog="$(git -C "${REPO_ROOT}" shortlog -sn --no-merges HEAD 2>/dev/null || true)"
	fi
fi

# `<git author name><TAB><nick>`, `#` comments and blanks skipped. Optional —
# an author missing from the table just renders by bare name.
NICKS="${SCRIPT_DIR}/contributors"
[ -r "${NICKS}" ] || NICKS=/dev/null

{
	printf 'window.RESENTIN_CREDITS = '
	printf '%s' "${shortlog}" | LC_ALL=C awk -v sha="${sha}" -v head_date="${date}" '
		function jsonstr(s,   out, i, c) {
			out = "\""
			for (i = 1; i <= length(s); i++) {
				c = substr(s, i, 1)
				if (c == "\\") {
					out = out "\\\\"
				} else if (c == "\"") {
					out = out "\\\""
				} else if (c < " ") {
					out = out " "
				} else {
					out = out c
				}
			}
			return out "\""
		}

		function jsonornull(s) {
			return s == "" ? "null" : jsonstr(s)
		}

		function trim(s) {
			sub(/^[ \t\r]+/, "", s)
			sub(/[ \t\r]+$/, "", s)
			return s
		}

		function nickof(name) {
			return (name in nick) ? jsonstr(nick[name]) : "null"
		}

		pass == 1 {
			if ($0 ~ /^[ \t]*(#|$)/) {
				next
			}
			tab = index($0, "\t")
			if (tab == 0) {
				next
			}
			nick[trim(substr($0, 1, tab - 1))] = trim(substr($0, tab + 1))
			next
		}

		{
			tab = index($0, "\t")
			if (tab == 0) {
				next
			}
			name = substr($0, tab + 1)
			# dependabot[bot] and its siblings: dropped where the roll is
			# born, not hidden in the renderer.
			if (length(name) > 5 && substr(name, length(name) - 4) == "[bot]") {
				next
			}
			if (n > 0) {
				rows = rows ","
			}
			rows = rows "{\"name\":" jsonstr(name) ",\"nick\":" nickof(name) \
				",\"commits\":" ($1 + 0) "}"
			n++
		}

		END {
			printf "{\"sha\":%s,\"date\":%s,\"contributors\":[%s]}",
				jsonornull(sha), jsonornull(head_date), rows
		}
	' pass=1 "${NICKS}" pass=2 -
	printf ';\n'
} > "${OUT}"
