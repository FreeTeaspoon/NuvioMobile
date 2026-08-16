#!/usr/bin/env bash

set -euo pipefail

tag=""
to_ref="HEAD"
repository="${GITHUB_REPOSITORY:-}"
source_file=""
previous_ref=""
offline=false

usage() {
    echo "Usage: $0 --tag <fork-version> [--to <commit>] [--repository <owner/repo>] [--source-file <path>] [--previous <ref>] [--offline]" >&2
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --tag)
            tag="${2:-}"
            shift 2
            ;;
        --to)
            to_ref="${2:-}"
            shift 2
            ;;
        --repository)
            repository="${2:-}"
            shift 2
            ;;
        --source-file)
            source_file="${2:-}"
            shift 2
            ;;
        --previous)
            previous_ref="${2:-}"
            shift 2
            ;;
        --offline)
            offline=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage
            exit 1
            ;;
    esac
done

if [[ ! "$tag" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Fork release tag must use x.x.x.N, got ${tag:-<missing>}" >&2
    exit 1
fi
git cat-file -e "${to_ref}^{commit}" 2>/dev/null || {
    echo "Unknown ending commit: ${to_ref}" >&2
    exit 1
}

if [[ -n "$previous_ref" ]]; then
    git cat-file -e "${previous_ref}^{commit}" 2>/dev/null || {
        echo "Unknown previous release ref: ${previous_ref}" >&2
        exit 1
    }
else
    base_version="${tag%.*}"
    while IFS= read -r candidate; do
        [[ -n "$candidate" ]] || continue
        [[ "$candidate" == "$tag" ]] && continue
        if git merge-base --is-ancestor "$candidate" "$to_ref" 2>/dev/null; then
            previous_ref="$candidate"
            break
        fi
    done < <(git tag --list "${base_version}.*" --sort=-v:refname)

    if [[ -z "$previous_ref" ]] && git cat-file -e "refs/tags/${base_version}^{commit}" 2>/dev/null; then
        previous_ref="$base_version"
    fi
fi

write_source() {
    [[ -n "$source_file" ]] || return 0
    printf '%s\n' "$1" > "$source_file"
}

extract_changelog_section() {
    local version="$1"
    local output="$2"

    [[ -f CHANGELOG.md ]] || return 1
    awk -v version="$version" '
        /^##[[:space:]]+/ {
            heading = $0
            sub(/^##[[:space:]]+/, "", heading)
            sub(/^\[/, "", heading)
            sub(/\].*$/, "", heading)
            sub(/[[:space:]].*$/, "", heading)
            if (heading == version) {
                found = 1
                next
            }
            if (found) exit
        }
        found { print }
        END { if (!found) exit 1 }
    ' CHANGELOG.md > "$output" || return 1

    awk 'NF && $0 !~ /^###[[:space:]]+/ { has_content = 1 } END { exit has_content ? 0 : 1 }' "$output"
}

changelog_file="${TMPDIR:-/tmp}/nuvio-fork-changelog.$$.md"
trap 'rm -f "$changelog_file"' EXIT

if extract_changelog_section "$tag" "$changelog_file"; then
    cat "$changelog_file"
    write_source "CHANGELOG.md ${tag}"
    exit 0
fi

generated_notes=""
if [[ -n "$previous_ref" ]]; then
    generator_args=(--from "$previous_ref" --to "$to_ref")
    [[ -n "$repository" ]] && generator_args+=(--repository "$repository")
    [[ "$offline" == true ]] && generator_args+=(--offline)
    generated_notes="$(bash ./scripts/generate-release-notes.sh "${generator_args[@]}" 2>/dev/null || true)"
    if [[ -n "$generated_notes" ]]; then
        printf '%s\n' "$generated_notes" | sed 's/^/- /'
        write_source "commit range ${previous_ref}..${to_ref}"
        exit 0
    fi
fi

generated_notes="$(git log --no-merges --format='%h %s' -n 20 "$to_ref" 2>/dev/null || true)"
if [[ -n "$generated_notes" ]]; then
    printf '%s\n' "$generated_notes" | sed 's/^/- /'
    write_source "recent commit summary at ${to_ref}"
else
    echo "- No user-facing changes were recorded for this release."
    write_source "no release-note commits"
fi
