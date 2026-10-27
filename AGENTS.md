# AGENTS.md

Guidance for AI coding agents and human contributors working in this repository.
The nearest `AGENTS.md` up the directory tree applies.

## Project overview

A Java 17 DICOM toolkit built on [dcm4che3](https://github.com/dcm4che/dcm4che) and [weasis-core-img](https://github.com/nroduit/weasis-core-img) (the OpenCV wrapping); their versions are `dcm4che.version` and `weasis.core.img.version` in the root `pom.xml`.
It provides a DICOM network API (C-ECHO, C-FIND, C-GET, C-MOVE, C-STORE, STOW-RS, Modality Worklist), DICOM file utilities (transcoder, image I/O through a `DicomImageReader` ImageIO SPI, masking and de-identification, presentation states, LUTs), hanging protocol parsing and anatomic region codes.
It is used downstream by Weasis, Karnak and weasis-pacs-connector.
The artifact version tracks the dcm4che release (`<revision>` is `${dcm4che.version}`), with optional `sha1` / `changelist` qualifiers resolved by `flatten-maven-plugin`.
It requires **JDK 17+** (`java-version` in the root `pom.xml`) and Maven **3.6.3+** (enforced).

## Commands

```bash
mvn clean install                                           # full build, from the repository root
mvn -f weasis-dicom-tools clean install                     # the module only
mvn -f weasis-dicom-tools test                              # unit tests only
mvn -f weasis-dicom-tools test -Dtest=TranscoderTest#methodName    # one test class or method
mvn -Pcoverage -f weasis-dicom-tools verify                 # JaCoCo coverage, as CI and SonarCloud
mvn -f weasis-dicom-tools -Pdicom-integration verify        # integration tests against a real PACS
mvn -f weasis-dicom-tools spotless:apply                    # format and license header, before committing
```

Spotless is configured but not bound to a phase: run it explicitly when preparing a commit, not as part of routine edits.

Tests need the OpenCV native library, because the DICOM image pipeline depends on it.
Maven unpacks it into `weasis-dicom-tools/target/lib/<os-name>-<cpu-name>/` during `generate-test-resources`, and Surefire runs with `-Djava.library.path` pointing there (see `argLine` in `weasis-dicom-tools/pom.xml`).
The active OS and architecture profile of the root `pom.xml` selects the native classifier, so a plain `mvn test` works only on linux-x86_64/aarch64, macosx-x86_64/aarch64 or windows-x86_64.

## Definition of done

A change is complete when:

- `mvn -f weasis-dicom-tools spotless:apply` has been run and `mvn -f weasis-dicom-tools verify` passes.
- New behavior has tests, and a fixed bug has a test that failed before the fix.
- The build adds no new compiler or Sonar warning in the changed code.
- A doc in `docs/` that describes a changed concept is updated in the same change; `docs/RISK_COVERAGE.md` follows the tests of the risk categories it lists.
- The description of the change says how it was verified.

## Project layout

A two-module Maven build:

- Root `pom.xml`: the BOM (`weasis-dicom-tools-bom`) that pins `dcm4che.version` and `weasis.core.img.version` and declares the five OpenCV native classifiers (linux-aarch64, linux-x86-64, macosx-aarch64, macosx-x86-64, windows-x86-64) through OS and architecture profiles.
- `weasis-dicom-tools/`: the library itself (packaging `jar`).

Packages under `weasis-dicom-tools/src/main/java`:

- `org.dcm4che3.img`: the DICOM image pipeline.
  `DicomImageReader` (ImageIO SPI registered in `META-INF/javax.imageio.spi.ImageReaderSpi`), `DicomImageReadParam` / `DicomTranscodeParam` / `ImageTranscodeParam`, `Transcoder` (dcm2dcm and dcm2image entry points), `DicomImageAdapter`, `DicomMetaData`, `DicomOutputData`, `ImageRendering`, `DicomImageUtils`, `DicomJpegWriteParam`.
- `org.dcm4che3.img.lut`: DICOM Modality, VOI and Presentation LUTs; configured W/L presets come from a `ModalityPresetProvider` set by the host (none by default).
- `org.dcm4che3.img.data`, `.op`, `.stream`, `.util`: DICOM pixel data records, the mask area operation, file input streams, date/time and DICOM element helpers.
- `org.dcm4che3.imageio.codec`: codec adapters (JPEG, MP4, MPEG) that integrate the dcm4che codecs with the image pipeline.
- `org.dcm4che3.tool.{findscu,movescu,getscu,storescu,storescp}`: command-line entry points adapted from dcm4che, with shared option parsing in `org.dcm4che3.tool.common`.
- `org.weasis.dicom.op`: the high-level DICOM SCU API: `Echo`, `CFind`, `CGet`, `CMove`, `CStore`, `CGetForward`.
  Each call returns a `DicomState` to poll for progress, status and response identifiers.
- `org.weasis.dicom.param`: connection parameters (`DicomNode`, `AdvancedParams`, `ConnectOptions`, `TlsOptions`, progress listeners, attribute editors).
- `org.weasis.dicom.web`: STOW-RS (`DicomStowRS`), the UPS-RS worklist client (`UpsRS`, a REST port of dcm4che `upsscu`; `UpsWorkitem` builds the N-CREATE data set of an IHE AIW-I Task Requestor; OAuth2 through the host-supplied `AuthorizationProvider`), multipart helpers, WADO and QIDO clients.
- `org.weasis.dicom.tool`: the DICOM toolset (worklist SCU, Modality Worklist SCP, listener, dicomizer, forward).
- `org.weasis.dicom.hp` (with `.enums`, `.filter`, `.plugins`, `.spi`): hanging protocol parsing (DICOM HP IODs).
- `org.weasis.dicom.macro`: DICOM macro helpers (`Code`, `ItemCode`, …) used by `ref`.
- `org.weasis.dicom.ref`: anatomic region code dictionaries (`BodyPart`, `SurfacePart`, `AnatomicModifier`, `AnatomicBuilder`, `AnatomicRegion`), region groups (`RegionGroup`, `RegionGroups`, `regionGroups.json`) and the anatomy notation (`AnatomySelector`); concepts in `docs/Anatomic-Regions.md`.
  Backed by localized `.properties` resource bundles (en and fr).
- `org.weasis.dicom.mf`, `.geom`, `.util`: the manifest format, 3D geometry helpers (`Vector3`, orientation), generic utilities.

At build time, the dcm4che `uids.xml` and `dataelements.xml` are unpacked into `target/dict/` by `maven-dependency-plugin` and added as a resource root, so they end up on the class path.

CI (`.github/workflows/maven.yml`) runs `mvn -Pcoverage -f weasis-dicom-tools -B verify` on Ubuntu only, so SonarCloud coverage reflects the linux-x86-64 native path.
Integration tests do not run in CI.

## Code conventions

- **DICOM conformance**: follow the DICOM standard (PS3.x).
  An intentional deviation (tolerating malformed files, working around non-conformant vendor or modality output, relaxing a rule for robustness) carries a short comment where it happens, saying what the standard requires, why the code diverges, and citing the part and section when possible (for example `// PS3.5 §6.2 requires even-length values; accept odd length from non-conformant writers`).
- **IHE and FHIR alignment**: where applicable, also follow the IHE integration profiles (Radiology TF: SWF.b, PIR, XDS-I.b, IID, BIR, KIN; IT Infrastructure TF: ATNA, CT) and the HL7 FHIR resources and terminology bindings (`ImagingStudy`, `ImagingSelection`, `Endpoint`, SNOMED CT, LOINC, DICOM CID mappings).
  Prefer the constrained behavior of a profile over a looser reading of the base standard when both apply.
  Flag intentional deviations the same way, citing the profile and transaction or the FHIR resource (for example `// IHE RAD-69 requires …; relaxed because …`).
- **Untrusted input**: DICOM files and DICOMweb or DIMSE responses come from outside; validate lengths, counts and offsets before allocating or indexing, and never let a malformed value cause an unbounded allocation or loop.
- **Formatting and headers**: google-java-format through Spotless, which also enforces the EPL-2.0 OR Apache-2.0 license header; keep it on new files.
- **Logging**: use SLF4J with `{}` placeholders, never `System.out`, `System.err` or `printStackTrace()` in library code.
  The command-line tools under `org.dcm4che3.tool.*` and the codec benchmark may print to the console.
  Never log patient identifiers above `DEBUG`.
- **Native resources**: release every `PlanarImage` / `Mat` deterministically (try-with-resources or `release()`).
- **Javadoc**: keep it minimal.
  A private method gets at most a one-line comment, or none when its name and signature are clear.
  A public or protected API gets one short sentence on its intent, with `@param` / `@return` / `@throws` only when they add what the signature does not say.
  Never restate the method name, never document obvious getters and setters, never leave `TODO` placeholders.
- **Code quality**: small focused methods, expressive names, early returns.
  Remove redundant code: unused imports and locals, dead branches, duplicated logic, null checks for values that cannot be null, comments that repeat the code.
  Extract a private helper rather than copy a block.

## Testing

Layout under `weasis-dicom-tools/src/test/java`:

- Unit tests mirror the main packages.
- Integration tests live in `org.weasis.dicom.real` with the **`IT` suffix** (for example `CMoveIT`, `EchoIT`, `StowIT`).
  They are excluded from the default Surefire run and execute only under the `dicom-integration` profile through `maven-failsafe-plugin`.
- Test fixtures (DICOM files, hanging protocol XML, MPEG samples, presentation states) live in `src/test/resources/`.

### Framework

- Use **JUnit 6** (`org.junit.jupiter.*`) and **Mockito** only; parameterized tests use `junit-jupiter-params`.
  Do not add AssertJ (`org.assertj.*`): use JUnit's `Assertions` (`assertEquals`, `assertThrows`, `assertAll`, …).
- Tests that need a fixed default `Locale` or `TimeZone` use the project extension, never `Locale.setDefault` without a restore:
  - `org.weasis.dicom.junit.DefaultLocale(language = "en", country = "US")`
  - `org.weasis.dicom.junit.DefaultTimeZone("UTC")`
  - Both apply at class or method level, walk `@Nested` and enclosing classes, and restore the previous value in `afterEach` (see `LocaleTimeZoneExtension`).
- **junit-pioneer was removed** in favor of this extension; do not reintroduce it.
  Migrating a test is an import swap from `org.junitpioneer.jupiter.DefaultLocale` / `DefaultTimeZone` to `org.weasis.dicom.junit.DefaultLocale` / `DefaultTimeZone`.
  For another pioneer-like extension (`@SetSystemProperty`, `@RetryingTest`, …), follow the same pattern: a small annotation with a `BeforeEachCallback` / `AfterEachCallback` extension in this project.
- Tests write files only under a `@TempDir` and follow the same Spotless rules as production code.

### Integration tests (`IT` suffix)

`org.weasis.dicom.real.*IT` tests talk to a real DICOM SCP.
`DicomTestConfig` loads their configuration from `src/test/resources/dicom-configs/<config-name>.properties`, selected with `-Ddicom.test.config=<name>` (default `public-server`, the public testing PACS at `www.dicomserver.co.uk`).
`secure-server.properties` is for TLS-enabled targets and `local-dcm4che.properties` for a local dcm4che container.
These tests hit the network, so they are slow and occasionally flaky when the public server is busy or filtered; never add them to the default Surefire run.

## Risks

Things that fail subtly in this codebase, with how to spot and avoid them:

| Risk | Where it bites | How to detect | How to avoid |
|---|---|---|---|
| **OpenCV native missing** | Any test touching `DicomImageReader` / `Transcoder` / `ImageRendering` throws `UnsatisfiedLinkError` or returns an empty `PlanarImage` | First failing test in image pipeline classes; CI on an unsupported OS or architecture | Run on linux, macOS or Windows x86-64 or aarch64; check that `target/lib/<os>-<arch>/` is populated; never `-Dmaven.test.skip` past the `generate-test-resources` step |
| **Shared JVM state** | Tests sharing JVM-global state (default `Locale`, default `TimeZone`, system properties, static caches in `AnatomicBuilder`, file handles in `Transcoder`) flake when tests run concurrently or in another order | A test passes alone but fails in the full suite, then passes again on rerun | Use `@DefaultLocale` / `@DefaultTimeZone` from `org.weasis.dicom.junit`; never `System.setProperty` without a restore; isolate temporary outputs per test with `@TempDir` |
| **STOW-RS `DicomStowRSTest$Real_Compressed_Image_Payload_Tests`** | Occasionally fails with `IOException: HTTP/1.1 header parser received no bytes` under load (the embedded HTTP test server races with the client); a known flake, not a regression | A single failure in `should_create_stream_payload_with_unknown_size` that passes when run alone (`-Dtest=DicomStowRSTest`) | Rerun the suite; if it persists, run `DicomStowRSTest` alone to tell a regression from the flake |
| **Default `Locale` leak** | `ref/AnatomicBuilderTest`, `AnatomicRegionTest`, `geom/Vector3OrientationTests` and `dcm4che3/img/util/DicomUtilsTest` assume `en_US` for formatting and resource bundle resolution; a test that calls `Locale.setDefault(...)` without a restore poisons later tests | Localized string assertions fail on machines whose JVM default is not English; `ResourceBundle` returns the wrong properties file | Always use `@DefaultLocale`; for code driven by `ResourceBundle`, also call `ResourceBundle.clearCache()` if a previous test pinned a bundle |
| **Default `TimeZone` leak** | `DateTimeUtilsTest` and `DicomUtilsTest` convert `java.util.Date` through the default zone | Time of day off by N hours, dates shifted by one day | Use `@DefaultTimeZone("UTC")` (or the zone the test needs); prefer DICOM date/time APIs that take an explicit `TimeZone` |
| **DICOM dictionary unpack** | `org.dcm4che3.data.Tag.*` lookups fail or return `?` when `target/dict/{uids.xml,dataelements.xml}` is missing from the class path | `MalformedURLException` or unknown-tag warnings at startup | Never run `mvn process-classes` alone; always run at least up to `generate-resources`; never delete `target/dict` during a build |
| **dcm4che `Tool` classes in `org.dcm4che3.tool.*`** | Adapted from the dcm4che CLI tools, they partly mutate static state (logging, `Connection` registries); two SCU/SCP tools in the same JVM can interfere | Tests in `org.dcm4che3.tool.*` flake when run concurrently with `org.weasis.dicom.op.*` | For a new SCU test, prefer the `org.weasis.dicom.op.*` API over the `org.dcm4che3.tool.*` entry points |
| **Integration tests against `www.dicomserver.co.uk`** | The public PACS rate-limits, sometimes returns stale UIDs, and its data can change | `CFindIT`, `CMoveIT`, `CGetIT` return empty or wrong responses | Check the test data of `public-server.properties`; switch to a local dcm4che container (`-Ddicom.test.config=local-dcm4che`) for stable runs |
| **`flatten-maven-plugin` output** | The plugin generates `.flattened-pom.xml` during `process-resources` (BOM mode at the root, ossrh mode in the child); edits to it are lost on the next build | A POM change is reverted at install time | Always edit `pom.xml`, never `.flattened-pom.xml` |
| **`weasis-core-img` version coupling** | `weasis.core.img.version` must match an artifact published in `mvn-repo/org/weasis/core/`; bumping it to an unpublished version fails CI with `Could not find artifact …weasis-core-img:jar:<v>` | The build fails at dependency resolution, with no compile diagnostic | Before bumping, check that the release exists in [mvn-repo](https://github.com/nroduit/mvn-repo/tree/master/org/weasis/core/weasis-core-img) |
| **Native classifier mismatch** | The `copy` execution of `maven-dependency-plugin` copies one classifier (`${os-name}-${cpu-name}`) into `target/lib/...`; when the active profile does not match, the wrong `.so` / `.dylib` / `.dll` is used | `UnsatisfiedLinkError: no opencv_java in java.library.path` or `Wrong ELF class` | Check the active profile in the `-X` Maven output; on macOS arm64 make sure the `macosx-aarch64` profile activates |

## Updating dependencies

The dependency graph is small and explicit:

- `dcm4che.version` (root `pom.xml`): the main DICOM stack.
  Bumping it bumps the artifact version too (the root `<revision>` is `${dcm4che.version}`).
- `weasis.core.img.version` (root `pom.xml`): the OpenCV wrapping; it must exist in `mvn-repo` before the bump.
- `slf4j.version` (root `pom.xml`), `junit-bom.version` and `mockito.version` (module `pom.xml`): the logging and test stack.
- The `org.weasis.thirdparty.org.opencv:libopencv_java` / `opencv_java` native classifiers carry the version of `weasis.core.img.version` (resolved transitively from `weasis-core-img-bom`).

To add or remove a dependency:

1. Edit `weasis-dicom-tools/pom.xml`.
   Add the dependency to `<dependencies>`; when it has several coordinates (BOM style), put the version in `<dependencyManagement>` and the bare coordinate in `<dependencies>`, like the dcm4che, JUnit and Mockito entries.
2. Do not add libraries outside the dcm4che / weasis-core-img transitive closure unless required: downstream OSGi consumers (Weasis) have to ship every transitive `compile` dependency.
3. Run `mvn -f weasis-dicom-tools -q -DskipTests compile`, then `mvn -f weasis-dicom-tools -q test-compile`, to confirm resolution.
4. Run the affected test classes (`-Dtest=…`) before the full suite.
5. Apply Spotless before committing: `mvn -f weasis-dicom-tools spotless:apply`.

## Writing conventions

These rules apply to Markdown docs, Javadoc, comments, log messages and commit messages.

- **American English**: center, color, gray, behavior, normalize, initialize, artifact, license (noun).
  DICOM keywords, dcm4che names and external API names keep their own spelling.
- **Markdown**:
  - One sentence per line, so that diffs show the changed sentence only.
  - A blank line before and after headings, lists, tables, code blocks and quotes; never more than one blank line.
  - A hard line break is a trailing `\`, never two trailing spaces.
  - CommonMark with GFM tables and fenced code blocks that name their language (`java`, `bash`, `text`, …); no raw HTML.
  - Relative links between files in the repository.
- `.editorconfig` sets UTF-8, LF and a final newline; `.markdownlint.yaml` holds the Markdown rules a linter can check.

## Git and commits

- One logical change per commit; never mix formatting or refactoring with a functional change.
- Subject line: an imperative sentence that says what the change does, capitalized, without a trailing period, at most about 72 characters (for example `Reject STOW-RS responses that do not confirm the storage`).
  Reference the issue as `#123` when there is one.
- A body, after a blank line, explains why when the subject is not enough.
- Never push, force-push, rewrite published history or create a release unless explicitly asked.
- `CHANGELOG.md` is generated by the `changelog` workflow: never edit it by hand.

## Boundaries

### Never

- Commit patient data: test fixtures are synthetic or anonymized, with no names, IDs, dates of birth or other identifiers left in the DICOM attributes or the pixels; keep them small.
- Commit secrets or credentials: the TLS and server configurations in `dicom-configs/` hold only public test values.
- Edit generated or external content: `target/`, `.flattened-pom.xml`, `CHANGELOG.md`, the unpacked dcm4che dictionaries.
- Add integration tests to the default Surefire run, or disable Spotless, tests or the enforcer to make a build pass.

### Ask first

- Adding, upgrading or removing a dependency or a Maven plugin, or bumping `dcm4che.version` (which changes the artifact version).
  A new dependency must have a license compatible with EPL-2.0 OR Apache-2.0.
- Changing or removing a public API: Weasis, Karnak and weasis-pacs-connector use it.
  Deprecate with `@Deprecated` rather than remove.
- Changing the CI, benchmark or release workflows.
