#!/usr/bin/env bash
# Dumps the server thread's stack so the Chunky generation loop can be located.
#
# The plugin's own counter cannot distinguish "the loop is stuck" from "the loop is running but
# counting differently", and both look identical from the outside. A stack trace answers it in one
# shot: whatever the loop is doing right now is in the frames.
set -u

SERVER_ROOT="${1:-/mnt/AI/mnt/ai/neoforbirckit}"

pid=""
for entry in /proc/[0-9]*; do
	candidate=$(tr '\0' ' ' < "$entry/cmdline" 2>/dev/null) || continue
	case "$candidate" in
		*unix_args*|*neoforbirckit*) pid=${entry#/proc/} ;;
	esac
done 2>/dev/null

if [ -z "$pid" ]; then
	echo "no server process is running; start it, run /chunky start, then run this again"
	exit 1
fi

echo "server pid $pid"
echo

dump=$(mktemp)
jstack "$pid" > "$dump" 2>/dev/null || {
	echo "jstack failed; is it on PATH?"
	exit 1
}

echo '=== the server thread ==='
awk '/^"Server thread"/,/^$/' "$dump" | head -40

echo
echo '=== anything mentioning chunky ==='
grep -n -i -B2 -A18 'chunky' "$dump" | head -80

echo
echo '=== anything sitting in a chunk load or a wait ==='
grep -n -B6 -A12 -E 'getChunkFuture|managedBlock|ServerChunkCache|generat' "$dump" | head -80

echo
echo "(the full dump is at $dump)"
