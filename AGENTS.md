# Repository instructions

## CI and tests

Before changing pipelines or test setup, read the
[shared CI contract](https://github.com/edigonzales/hop-plugin-ci/blob/main/docs/ci-contract.md).
The documentation follows `main`; use the interfaces at this repo's actual
workflow/helper revisions and preserve existing pins and `ci-ref` values.

Run the commands below from this repository root in Bash, using Python 3, Maven
and JDK 21 (`JAVA_HOME` and `PATH` pointing to that JDK). Compatibility jobs also
use JDK 25. For headless Linux SWT tests, run Maven under `xvfb-run -a`.
Set `HOP_CI_DIR` to an absolute checkout of `hop-plugin-ci` at the helper revision
used by this repo's workflow, then prepare the same Maven repositories as CI:

```bash
CI_TEST_TMP="$(mktemp -d)"
export MAVEN_SETTINGS="$CI_TEST_TMP/maven-settings.xml"
python3 "$HOP_CI_DIR/scripts/write_maven_settings.py" --output "$MAVEN_SETTINGS"
```

### Library verification

See [.github/workflows/maven.yml](.github/workflows/maven.yml). The canonical cell
is Ubuntu 24.04/JDK 21; compatibility includes Windows 2025 and macOS 15.

```bash
mvn -s "$MAVEN_SETTINGS" -U -B -ntp clean package
```

For compatibility use `clean test` instead. The canonical artifact descriptors
include the parent POM, core/UI JARs and POMs, and sources/javadoc classifiers.
This is a Maven library bundle, not an installable Hop ZIP; there is no installed
Hop E2E stage in this workflow.

### After snapshot publication only

```bash
python3 scripts/verify-snapshot.py
```

This requires Maven, JDK 21 and network access to the published snapshot matching
the root POM version. It creates a fresh temporary Maven repository, compiles a
consumer and checks the parent/core/UI artifacts and classifiers. It does not
validate an unpublished local build and is not a standard local test command.
The workflow runs it after snapshot deployment, allowed on main pushes and manual
runs on main, after verify succeeds. Library release publication is unsupported
by the current shared workflow.
