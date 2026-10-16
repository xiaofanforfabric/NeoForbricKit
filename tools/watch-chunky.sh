#!/usr/bin/env bash
# Watches whether Chunky is actually producing new chunk data, while the server is running.
#
# The point is to answer one question that a stopped server cannot: is the generation loop
# advancing? A region file that changes size while the server runs means chunks are being written;
# one that never changes means the loop is stuck, and no amount of looking at the plugin's own
# comma counter will tell you which of those it is.
set -u

SERVER_ROOT="${1:-/mnt/AI/mnt/ai/neoforbirckit}"
REGION="$SERVER_ROOT/world/dimensions/minecraft/overworld/region"
TASK="$SERVER_ROOT/Chunky/tasks/overworld.properties"

echo "watching $REGION"
echo

for round in $(seq 1 12); do
	printf '--- %s ---\n' "$(date '+%H:%M:%S')"

	# Region files that exist right now, by size. A file appearing or growing is the signal.
	total=$(find "$REGION" -name '*.mca' -printf '%s\n' 2>/dev/null | awk '{s+=$1} END {print s+0}')
	count=$(find "$REGION" -name '*.mca' 2>/dev/null | wc -l)
	printf '  region files: %s   bytes: %s\n' "$count" "$total"

	# Newest three by mtime, so a file being written is visible by name.
	find "$REGION" -name '*.mca' -printf '%T@ %p %s\n' 2>/dev/null | sort -rn | head -3 |
		while read -r _ path size; do printf '    %s  %s\n' "$(basename "$path")" "$size"; done

	if [ -f "$TASK" ]; then
		# The plugin's own counter, for comparison. It is not the evidence; the files are.
		printf '  chunky: %s\n' "$(tr '\n' ' ' < "$TASK")"
	else
		printf '  chunky: no task file\n'
	fi
	echo
	sleep 5
done
