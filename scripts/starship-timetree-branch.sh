#!/bin/bash

# Helper for Starship prompt.
# Print the current TimeTree branch name (e.g. "test-branch")
# OR the short commit id if HEAD is detached.
#
# Starship usage:
#   In starship.toml we define:
#
#     [custom.timetree]
#     command = "timetree-current-branch.sh"
#     when = "timetree-current-branch.sh"
#     format = "on [ $output]($style) "
#     style = "bold purple"
#
#      [directory]
#      truncation_length = 1
#
#      truncate_to_repo = false
#      truncation_symbol = ""

# Walk up from $PWD to find the repo root
find_timetree_root() {
    local dir="$PWD"
    while [ "$dir" != "/" ]; do
        if [ -d "$dir/.timetree" ]; then
            echo "$dir"
            return 0
        fi
        dir="$(dirname "$dir")"
    done
    return 1
}

root=$(find_timetree_root) || exit 1

# If we're literally in the internal .timetree folder, don't show anything.
# This avoids duplicate "on <branch>" segments when cd'ing into .timetree itself.
case "$PWD" in
    "$root/.timetree" | "$root/.timetree"/*)
        exit 1
        ;;
esac

headFile="$root/.timetree/HEAD"
[ -f "$headFile" ] || exit 1

headRef=$(cat "$headFile")

if [[ "$headRef" == ref:* ]]; then
    branch="${headRef##*/}"
    echo "$branch"
    exit 0
fi

short="${headRef:0:7}"
echo "$short"
exit 0
