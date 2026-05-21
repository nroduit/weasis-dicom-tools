# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Java 17 DICOM toolkit built on top of [dcm4che3](https://github.com/dcm4che/dcm4che) (currently 5.34.2) and [weasis-core-img](https://github.com/nroduit/weasis-core-img) (currently 4.13.0, our OpenCV wrapping). It exposes a DICOM network API (C-Echo / C-Find / C-Get / C-Move / C-Store, STOW-RS, Modality Worklist), DICOM file utilities (transcoder, image I/O via a `DicomImageReader` ImageIO SPI, masking / de-identification, presentation states, LUTs), DICOM hanging protocol parsing, and anatomic-region code mapping. Consumed downstream by Weasis, Karnak, and weasis-pacs-connector.

Artifact version tracks the underlying dcm4che release: `${dcm4che.version}` (e.g. `5.34.2`), with optional `sha1` / `changelist` qualifiers driven by `flatten-maven-plugin`.

## Build & test

Prerequisites: JDK 17+, Maven 3.8.1+.

- Full build (from repo root): `mvn clean install`
- Build/test the module only: `mvn -f weasis-dicom-tools clean install`
- Unit tests only: `mvn -f weasis-dicom-tools test`
- Single test class / method: `mvn -f weasis-dicom-tools test -Dtest=TranscoderTest` or `-Dtest=TranscoderTest#methodName`
- Coverage report (JaCoCo, used by CI/SonarCloud): `mvn -Pcoverage -f weasis-dicom-tools verify`
- Integration tests against a real PACS: `mvn -f weasis-dicom-tools -Pdicom-integration verify` (see [Integration tests](#integration-tests-it-suffix))
- Apply formatting (**before committing only**, not as part of routine edits): `mvn -f weasis-dicom-tools spotless:apply` (google-java-format + EPL-2.0/Apache-2.0 license header). Spotless is configured but not bound to a phase, so it is run explicitly and only when preparing a commit.

Tests require the OpenCV native library (the DICOM image pipeline depends on it). Maven unpacks it into `weasis-dicom-tools/target/lib/<os-name>-<cpu-name>/` during `generate-test-resources` and Surefire is invoked with `-Djava.library.path` pointing there (see `argLine` in `weasis-dicom-tools/pom.xml`). The active OS/arch profile in the root `pom.xml` selects which native classifier is unpacked, so a plain `mvn test` only works on linux-x86_64/aarch64, macosx-x86_64/aarch64, or windows-x86_64.

Surefire runs with `<parallel>all</parallel>`. That means tests must not rely on shared mutable JVM state (default `Locale`, default `TimeZone`, system properties, static caches, network ports) without explicit save/restore — see [Risks](#risks-matrix).

## Project layout

Two-module Maven build:

- Root `pom.xml` — BOM (`weasis-dicom-tools-bom`) that pins `dcm4che.version` and `weasis.core.img.version`, and declares the five OpenCV native classifiers (linux-aarch64, linux-x86-64, macosx-aarch64, macosx-x86-64, windows-x86-64) via OS/arch profiles.
- `weasis-dicom-tools/` — the actual library (packaging `jar`).

Source under `weasis-dicom-tools/src/main/java`:

- `org.dcm4che3.img` — DICOM image pipeline. `DicomImageReader` (ImageIO SPI registered via `META-INF/javax.imageio.spi.ImageReaderSpi`), `DicomImageReadParam` / `DicomTranscodeParam` / `ImageTranscodeParam`, `Transcoder` (dcm2dcm + dcm2image entry points), `DicomImageAdapter`, `DicomMetaData`, `DicomOutputData`, `ImageRendering`, `DicomImageUtils`, `DicomJpegWriteParam`.
- `org.dcm4che3.img.lut` — DICOM Modality / VOI / Presentation LUT (`presets.xml` ships built-in W/L presets).
- `org.dcm4che3.img.data`, `.op`, `.stream`, `.util` — DICOM pixel data records, mask area op, file input streams, date/time + dicom-element helpers.
- `org.dcm4che3.imageio.codec` — codec adapters (jpeg/mp4/mpeg) that integrate dcm4che codecs with our image pipeline.
- `org.dcm4che3.tool.{findscu,movescu,getscu,storescu,storescp}` — command-line entry points adapted from dcm4che. Plus `org.dcm4che3.tool.common` shared option parsing.
- `org.weasis.dicom.op` — high-level DICOM SCU API: `Echo`, `CFind`, `CGet`, `CMove`, `CStore`, `CGetForward`. Each call returns a `DicomState` you can poll for progress / status / response identifiers.
- `org.weasis.dicom.param` — connection parameters (`DicomNode`, `AdvancedParams`, `ConnectOptions`, `TlsOptions`, progress listeners, attribute editors).
- `org.weasis.dicom.web` — STOW-RS (`DicomStowRS`), multipart helpers, WADO / QIDO clients.
- `org.weasis.dicom.tool` — DICOM toolset (worklist SCU, Modality Worklist SCP, listener, dicomizer, forward).
- `org.weasis.dicom.hp` (+ `.enums`, `.filter`, `.plugins`, `.spi`) — Hanging Protocol parsing (DICOM HP IODs).
- `org.weasis.dicom.macro` — DICOM macro helpers (`Code`, `ItemCode`, …) used by `ref/`.
- `org.weasis.dicom.ref` — anatomic-region code dictionaries (`BodyPart`, `SurfacePart`, `AnatomicModifier`, `AnatomicBuilder`, `AnatomicRegion`). Backed by localized `.properties` resource bundles (en + fr).
- `org.weasis.dicom.mf`, `.geom`, `.util` — manifest format, 3-D geometry helpers (`Vector3`, orientation), generic utilities.

Generated resources at build time: `org.dcm4che` `uids.xml` and `dataelements.xml` are unpacked into `target/dict/` by `maven-dependency-plugin` and added as a resource root so they end up on the classpath.

## Tests

Layout under `weasis-dicom-tools/src/test/java`:

- Unit tests mirror the main-source packages.
- Integration tests live in `org.weasis.dicom.real` with the **`IT` suffix** (e.g. `CMoveIT`, `EchoIT`, `StowIT`). They are excluded from the default Surefire run and only execute under the `dicom-integration` profile via `maven-failsafe-plugin`.
- Test fixtures (DICOM files, hanging-protocol XML, mpeg samples, presentation states) live in `src/test/resources/`.

### Framework

- **JUnit 6** (`org.junit.jupiter.*`) and **Mockito** only. Do **not** add AssertJ (`org.assertj.*`) — use JUnit's built-in `Assertions` (`assertEquals`, `assertThrows`, `assertAll`, …) for assertions. Parameterized tests use `junit-jupiter-params`.
- Several tests pin the default `Locale` / `TimeZone` for deterministic formatting and date parsing. Use the project's small extension instead of pulling junit-pioneer back:
  - `org.weasis.dicom.junit.DefaultLocale(language = "en", country = "US")`
  - `org.weasis.dicom.junit.DefaultTimeZone("UTC")`
  - Both are class- or method-level, walk `@Nested`/enclosing classes, and save/restore the previous value in `afterEach` (see `LocaleTimeZoneExtension`).
- Tests follow the same Spotless / formatting rules as production code.

### Integration tests (`IT` suffix)

`org.weasis.dicom.real.*IT` tests talk to a real DICOM SCP. Configuration is loaded from `src/test/resources/dicom-configs/<config-name>.properties` by `DicomTestConfig`. Select with `-Ddicom.test.config=<name>` (default: `public-server`, the public testing PACS at `www.dicomserver.co.uk`). `secure-server.properties` is for TLS-enabled targets; `local-dcm4che.properties` for a local dcm4che container.

These tests hit the network, so they're slow, occasionally flaky if the public server is busy or filtered, and only run on demand (`-Pdicom-integration`). Don't add them to the default Surefire run.

## Risks matrix

Things that fail subtly in this codebase, with how to spot and avoid them:

| Risk | Where it bites | How to detect | How to avoid |
|---|---|---|---|
| **OpenCV native missing** | Any test touching `DicomImageReader` / `Transcoder` / `ImageRendering` throws `UnsatisfiedLinkError` or returns empty `PlanarImage` | First failing test in image-pipeline classes; CI on an unsupported OS/arch | Run on linux/macos/windows x86-64 or aarch64; verify `target/lib/<os>-<arch>/` is populated; never `-Dmaven.test.skip` past the `generate-test-resources` step |
| **`<parallel>all</parallel>` Surefire** | Tests sharing JVM-global state (default Locale, default TimeZone, system properties, static caches in `AnatomicBuilder`, file handles in `Transcoder`) flake under load | Test passes solo but fails in the full suite; the same test passes again on rerun | Use `@DefaultLocale` / `@DefaultTimeZone` from `org.weasis.dicom.junit` for locale / timezone; never `System.setProperty` without restore; isolate temp-file outputs per test with `@TempDir` |
| **STOW-RS `DicomStowRSTest$Real_Compressed_Image_Payload_Tests`** | Known to occasionally fail with `IOException: HTTP/1.1 header parser received no bytes` under parallel load (the embedded HTTP test server races with the client). Pre-existing flake — not a regression | Single failure in `should_create_stream_payload_with_unknown_size`; passes when run isolated (`-Dtest=DicomStowRSTest`) | Rerun the suite; if it persists, run `DicomStowRSTest` solo to confirm regression vs. flake |
| **Default `Locale` leak across tests** | Tests in `ref/AnatomicBuilderTest`, `AnatomicRegionTest`, `geom/Vector3OrientationTests`, `dcm4che3/img/util/DicomUtilsTest` assume `en_US` for formatting / resource bundle resolution. A test that calls `Locale.setDefault(...)` without restoring will poison later tests | Localised string asserts fail on developer machines whose JVM default is non-English; ResourceBundle returns the wrong properties file | Always use `@DefaultLocale` (which restores in `afterEach`). For `ResourceBundle`-driven code, also `ResourceBundle.clearCache()` if a previous test pinned a bundle |
| **Default `TimeZone` leak** | `DateTimeUtilsTest`, `DicomUtilsTest` convert `java.util.Date` via the default zone | Time-of-day off by N hours, dates shifted by ±1 day | Use `@DefaultTimeZone("UTC")` (or `"Europe/Paris"`, depending on the test); prefer DICOM date/time APIs that take an explicit `TimeZone` |
| **DICOM dictionary unpack** | `org.dcm4che3.data.Tag.*` lookups fail or return `?` if `target/dict/{uids.xml,dataelements.xml}` is missing from the classpath | `MalformedURLException` / unknown-tag warnings at startup | Don't `mvn process-classes` standalone — always run at least up to `generate-resources`; never delete `target/dict` mid-build |
| **dcm4che `Tool` classes in `org.dcm4che3.tool.*`** | These were adapted from dcm4che's CLI tools and partially mutate static state (logging, `Connection` registries). Running two scu/scp tools in the same JVM can interfere | Tests in `org.dcm4che3.tool.*` flake when run in parallel with `org.weasis.dicom.op.*` | If you add a new SCU test, prefer the `org.weasis.dicom.op.*` API over the `org.dcm4che3.tool.*` entry points |
| **Integration tests against `www.dicomserver.co.uk`** | The public PACS rate-limits, sometimes returns stale UIDs, and the data set can change | `CFindIT`, `CMoveIT`, `CGetIT` return empty / wrong responses | Treat IT failures as a signal to check `public-server.properties` test data; switch to a local dcm4che container (`-Ddicom.test.config=local-dcm4che`) for stable runs |
| **`flatten-maven-plugin` output** | The plugin generates `.flattened-pom.xml` during `process-resources` (BOM mode at root, ossrh mode in the child). Hand-edits get lost on next build | A POM change you made is reverted at install time | Always edit `pom.xml`, never `.flattened-pom.xml` |
| **OpenCV / `weasis-core-img` version coupling** | `weasis.core.img.version` (in root `pom.xml`) must match an artifact published at `mvn-repo/org/weasis/core/`. Bump it and CI fails with `Could not find artifact …weasis-core-img:jar:<v>` | Build fails at dependency resolution; no compile diagnostic | When bumping `weasis.core.img.version`, verify the release exists at https://github.com/nroduit/mvn-repo/tree/master/org/weasis/core/weasis-core-img before pushing |
| **Native classifier mismatch** | The `copy` execution of `maven-dependency-plugin` copies one classifier (`${os-name}-${cpu-name}`) into `target/lib/...`. If the active profile doesn't match, you get the wrong `.so/.dylib/.dll` | `UnsatisfiedLinkError: no opencv_java in java.library.path` or `Wrong ELF class` | Check `-X` Maven output for the active profile; on macOS arm64 ensure the `macosx-aarch64` profile activates |

## Updating dependencies

The dependency graph is small and explicit:

- `dcm4che.version` (root `pom.xml`) — main DICOM stack. Bumping it bumps the artifact version too (the root `<revision>` is `${dcm4che.version}`).
- `weasis.core.img.version` (root `pom.xml`) — our OpenCV wrapping. Must exist in `mvn-repo` before bumping.
- `slf4j.version`, `junit-bom.version`, `mockito.version` (module `pom.xml`) — test/log stack.
- All `org.weasis.thirdparty.org.opencv:libopencv_java` / `opencv_java` native classifiers carry the same version as `weasis.core.img.version` (resolved transitively from `weasis-core-img-bom`).

### Adding or removing a dependency

1. Edit `weasis-dicom-tools/pom.xml`. Add the dep to `<dependencies>`; if it has multiple coordinates (BOM-style) put the version pin in `<dependencyManagement>` and the bare coordinate in `<dependencies>` like the existing dcm4che / junit / mockito entries.
2. Don't add provided/runtime libraries that aren't already in the dcm4che / weasis-core-img transitive closure unless required — downstream OSGi consumers (Weasis) need to ship every transitive `compile`-scope dep.
3. Run `mvn -f weasis-dicom-tools -q -DskipTests compile` then `mvn -f weasis-dicom-tools -q test-compile` to confirm resolution and recompile.
4. Run the affected test classes (`-Dtest=…`) before running the full suite — it's faster and isolates the change.
5. Apply Spotless before committing: `mvn -f weasis-dicom-tools spotless:apply`.

### junit-pioneer was removed

`org.junit-pioneer:junit-pioneer` was previously used for `@DefaultLocale` and `@DefaultTimeZone`. It's been removed in favor of a small in-repo extension (`org.weasis.dicom.junit.DefaultLocale`, `org.weasis.dicom.junit.DefaultTimeZone`, `LocaleTimeZoneExtension`, all under `src/test/java/`). The annotation surface and semantics are intentionally the same (class- or method-level, walks `@Nested`/enclosing classes, restores the previous value in `afterEach`), so migrating a test is a one-line import swap:

```diff
- import org.junitpioneer.jupiter.DefaultLocale;
- import org.junitpioneer.jupiter.DefaultTimeZone;
+ import org.weasis.dicom.junit.DefaultLocale;
+ import org.weasis.dicom.junit.DefaultTimeZone;
```

Do not reintroduce the junit-pioneer dependency. If you need another pioneer extension (`@SetSystemProperty`, `@CartesianTest`, `@RetryingTest`, …), prefer extending `LocaleTimeZoneExtension`'s pattern: a tiny annotation + a `BeforeEachCallback` / `AfterEachCallback` extension scoped to this project.

## CI

`.github/workflows/maven.yml` runs `mvn -Pcoverage -f weasis-dicom-tools -B verify ...sonar` on Ubuntu only. SonarCloud coverage therefore reflects the linux-x86-64 native path. Integration tests are not run in CI (no `-Pdicom-integration`).

## Conventions

- Formatter: google-java-format via Spotless. License header (EPL-2.0 OR Apache-2.0) is also enforced by Spotless — keep it on new files.
- **Javadoc**: keep it minimal. **Private methods**: one-line comment max, or none if the name and signature are self-explanatory. **Public/protected API**: as compact as possible — one short sentence describing intent, `@param` / `@return` / `@throws` only when they add information the signature doesn't already convey. Never restate the method name in prose, never document obvious getters/setters, never leave `TODO`-style placeholders.
- **Code quality**: favor readability and maintainability — small focused methods, expressive names, early returns. Remove redundant code (unused imports/locals, dead branches, duplicated logic, defensive null checks for values that cannot be null, comments that duplicate the code). Prefer extracting a private helper over copy-pasting a block.
- **Tests**: see [Tests](#tests). JUnit 6 + Mockito + project-local extensions only; no AssertJ; no junit-pioneer.