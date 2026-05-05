# Fork Release Versioning

This fork should publish release versions as four numeric segments:

```text
x.x.x.N
```

The first three segments match the upstream project release. The fourth
segment identifies this fork's build of that upstream release.

Examples:

```text
Upstream release: 0.1.11
Fork release:     0.1.11.1

Upstream release: 0.1.11
Second fork build: 0.1.11.2

Upstream release: 0.1.12
Fork release:     0.1.12.1
```

## Version Source

The app version is read from:

```text
iosApp/Configuration/Version.xcconfig
```

Keep it in this shape for fork releases:

```text
CURRENT_PROJECT_VERSION=52
MARKETING_VERSION=0.1.12.1
```

`CURRENT_PROJECT_VERSION` must match the official NuvioMobile release APK
version code, even if that differs from the checked-in upstream version file or
means lowering this fork's version code. Release automation must not bump it
just because this fork is publishing another `x.x.x.N` build. `MARKETING_VERSION`
must remain a four-part fork version.

## Release Rule

Use this rule when preparing a fork release:

1. Find the upstream version, for example `0.1.11`.
2. Set `MARKETING_VERSION` to `0.1.11.1` for the first fork build.
3. If another fork build is needed for the same upstream version, increment
   only the fourth segment, for example `0.1.11.2`.
4. Keep `CURRENT_PROJECT_VERSION` identical to the official NuvioMobile release
   code. Increment only the fourth `MARKETING_VERSION` segment for fork-only
   builds.

Do not publish fork releases using the upstream three-part version, such as
`0.1.11`. That tag belongs to the upstream project.

## Workflow Guard

Release workflows should fail if `MARKETING_VERSION` is not four numeric
segments:

```bash
if ! [[ "${version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::Fork releases must use x.x.x.N, got ${version}"
  exit 1
fi
```

The current-branch release workflow may read a three-part upstream app version
from `MARKETING_VERSION`, but it must publish a four-part fork release tag by
choosing the next available suffix for that base version. For example, if the
app version is `0.1.13` and releases `0.1.13.1` and `0.1.13.2` already exist,
the next automatic fork release is `0.1.13.3`.

## Upstream Release Notes

When a workflow needs upstream release notes, derive the upstream tag by
removing the fork segment:

```bash
fork_version="${version}"
upstream_tag="${fork_version%.*}"
```

For example, `0.1.11.1` maps back to upstream tag `0.1.11`.

## Scheduled Upstream Builds

If a workflow starts from an upstream tag, it should not publish that tag
directly. Convert it to a fork tag before creating the release:

```bash
upstream_version="${upstream_tag#v}"
fork_tag="${upstream_version}.1"
```

Use `upstream_tag` only for fetching or merging official source. Use
`fork_tag` for this repository's Git tag and GitHub release.
