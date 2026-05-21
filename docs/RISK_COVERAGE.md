# Test coverage matrix — weasis-dicom-tools

This document is a developer-facing index of which automated tests in this
repository exercise which categories of risk. It is **not** a certification
artefact, a conformity claim, or a substitute for any regulated process.
Readers integrating `weasis-dicom-tools` into a regulated product are
responsible for their own software-requirement traceability and verification
evidence under the framework that applies to them.

> Last updated: 2026-05-21
> Test count at that date: 4584 automated tests (106 unit-test classes plus
> 9 integration `IT` classes), all passing on the active build OS/arch
> profile.
> Coverage tooling: JaCoCo via `mvn -Pcoverage -f weasis-dicom-tools verify`.
> SonarCloud reports the Linux-x86-64 native path only.

## 1. Scope

`weasis-dicom-tools` is a Java 17 DICOM toolkit built on top of
[dcm4che3](https://github.com/dcm4che/dcm4che) (5.34.2) and the OpenCV
wrapping in [weasis-core-img](https://github.com/nroduit/weasis-core-img).
It exposes:

- a DICOM network API: C-Echo, C-Find, C-Get, C-Move, C-Store, STOW-RS,
  Modality Worklist;
- a DICOM file pipeline: `DicomImageReader` (`ImageIO` SPI), `Transcoder`
  (dcm2dcm + dcm2image), modality / VOI / presentation LUTs, masking,
  presentation states;
- DICOM hanging-protocol parsing;
- anatomic-region code dictionaries (en + fr resource bundles);
- a manifest format and 3-D geometry helpers.

The risk-relevant axes for a library of this shape are: DICOM dataset
parsing robustness, native interop around `Mat`-backed pixel buffers,
pixel-exact transcoding round-trips, association lifecycle on the network
side, TLS configuration defaults, deidentification primitives, and JVM
shared-state hygiene under Surefire `<parallel>all</parallel>`.

The build expects a per-platform native OpenCV library to be supplied at
runtime by the consumer via `-Djava.library.path`. The test harness unpacks
the matching native artefact into `weasis-dicom-tools/target/lib/<os>-<arch>/`
for Surefire.

## 2. Coverage matrix

The table maps a risk category to the production classes that own it and
the test classes that exercise it. The "Tests exercise" column is
descriptive — it states what is tested, not how good the coverage is.

| # | Risk category | Production class(es) | Test class(es) | Tests exercise |
|---|---------------|----------------------|----------------|----------------|
| R-01 | Input validation (null / empty / blank on public surface) | `DicomNode`, `AdvancedParams`, `ConnectOptions`, `DicomState`, `DicomProgress`, `CstoreParams`, `AttributeEditorContext`, `Patient`, `Study`, `Series`, `SopInstance`, `ArcParameters`, `HttpTag` | `DicomNodeTest`, `AdvancedParamsTest`, `DicomStateTest`, `DicomProgressTest`, `CstoreParamsTest`, `AttributeEditorContextTest`, `DefaultAttributeEditorTest`, `PatientTest`, `StudyTest`, `SeriesTest`, `SopInstanceTest`, `ArcParametersTest`, `HttpTagTest` | `@NullAndEmptySource` on public string APIs; explicit `NullPointerException` checks at `Objects.requireNonNull` boundaries; blank-input rejection on identifiers. |
| R-02 | TLS configuration / secure defaults | `TlsOptions` (`MODERN_TLS = TLSv1.2 + TLSv1.3`; SSL/SSLv3 helpers removed) | exercised indirectly through `AdvancedParamsTest` and `DicomNodeTest`; consumers select cipher and protocol lists explicitly | The legacy `SSL_3` constant has been removed; the default for the convenience constructor is `MODERN_TLS`. |
| R-03 | Deidentification primitives | `Hmac` (`HmacSHA256`, `SecureRandom` for key derivation) | `HmacTest` | Key derivation determinism for a given seed; UID / accession / date scramble round-trip; rejection of empty inputs. |
| R-04 | DICOM dataset parsing robustness | `DicomImageReader`, `DicomMetaData`, `DicomFileInputStream`, `Transcoder`, `ImageAdapter`, `OverlayData`, `EmbeddedOverlay`, `PrDicomObject`, `JPEGParser`, `JPEGHeader`, `MP4Parser`, `MPEG2Parser`, `MPEGHeader` | `DicomImageReaderTest`, `DicomMetaDataTest`, `DicomFileInputStreamTest`, `TranscoderTest`, `ImageAdapterTest`, `OverlayDataTest`, `EmbeddedOverlayTest`, `PrDicomObjectTest`, `JPEGParserTest`, `JPEGHeaderTest`, `MP4ParserTest`, `MPEG2ParserTest`, `MPEGHeaderTest` | Truncated / malformed pixel data, missing required tags, unrecognised transfer syntax, fragmented pixel data, multi-frame indexing edge cases, embedded overlays, codec header validation. |
| R-05 | Native interop failure modes (OpenCV via JNI) | `DicomImageReader.read*`, `Transcoder`, `ImageRendering`, `DicomImageAdapter`, `MaskArea` | `DicomImageReaderTest`, `TranscoderTest`, `ImageRenderingTest`, `DicomImageAdapterTest`, `MaskAreaTest` | Decode failures degrade to `null` / empty `PlanarImage` rather than crashing the JVM; tests cover corrupted inputs, unsupported transfer syntaxes, and the throw-on-failure variants. |
| R-06 | Pixel-exact data integrity (lossless transcoding) | `Transcoder` (dcm2dcm + dcm2image), `DicomOutputData`, `DicomJpegWriteParam`, `DicomTranscodeParam`, `ImageTranscodeParam` | `TranscoderTest`, `DicomOutputDataTest`, `DicomJpegWriteParamTest`, `DicomTranscodeParamTest`, `ImageTranscodeParamTest` | Round-trip across Implicit VR Little Endian, Explicit VR Little Endian, JPEG-Lossless, RLE Lossless; bit-exact pixel data assertions for the lossless paths. |
| R-07 | LUT / windowing numerical correctness | `DicomImageAdapter`, `ModalityLutModule`, `VoiLutModule`, `WindLevelParameters`, `PresetWindowLevel`, `LookupTableUtils`, `RescaleUtils`, `PixelDataUtils`, `PaletteColorUtils` | `DicomImageAdapterTest`, `ModalityLutModuleTest`, `VoiLutModuleTest`, `WindLevelParametersTest`, `PresetWindowLevelTest`, `LookupTableUtilsTest`, `RescaleUtilsTest`, `PixelDataUtilsTest`, `PaletteColorUtilsTest` | LINEAR / SIGMOID shapes, rescale slope/intercept, signed vs unsigned pixel representation, palette colour LUT generation, `presets.xml` parsing. |
| R-08 | Concurrent JVM state under Surefire `<parallel>all</parallel>` | Locale-sensitive code (`AnatomicRegion`, `BodyPart` / `SurfacePart` `.properties` lookup); TimeZone-sensitive code (`DateTimeUtils`, `DicomUtils`) | `org.weasis.dicom.junit.DefaultLocale` / `DefaultTimeZone` extensions applied in `AnatomicBuilderTest`, `AnatomicRegionTest`, `Vector3OrientationTests`, `DicomUtilsTest`, `DateTimeUtilsTest` | Default Locale pinned to `en_US`, default TimeZone pinned where needed; both are restored in `afterEach` so they do not leak to sibling tests. |
| R-09 | Date / time handling across timezones | `DateTimeUtils`, `DicomUtils`, `DicomAttributeUtils` | `DateTimeUtilsTest`, `DicomUtilsTest`, `DicomAttributeUtilsTest`, `DicomObjectUtilTest` | DICOM DA / TM / DT parsing under non-default timezones; fractional-seconds handling; explicit-`TimeZone` overloads. |
| R-10 | DICOM SCU association lifecycle | `Echo`, `CFind`, `CGet`, `CMove`, `CStore`, `CGetForward`, `StoreFromStreamSCU`, `DicomState`, `DicomProgress`, `DeviceListenerService`, `DeviceOpService`, `ServiceUtil` | `EchoTest`, `CFindTest`, `CGetTest`, `CMoveTest`, `CStoreTest`, `CGetForwardTest`, `StoreFromStreamSCUTest`, `DicomStateTest`, `DicomProgressTest`, `DeviceListenerServiceTest`, `DeviceOpServiceTest`, `ServiceUtilTest` | Progress callbacks, cancellation, status-code propagation, connection-options configuration. |
| R-11 | dcm4che CLI-tool wrapper isolation | `FindSCU`, `GetSCU`, `MoveSCU`, `StoreSCU`, `StoreSCP`, `CLIUtils`, `DicomFiles`, `FilesetInfo` | `FindSCUTest`, `GetSCUTest`, `MoveSCUTest`, `StoreSCUTest`, `StoreSCPTest`, `CLIUtilsTest`, `DicomFilesTest`, `FilesetInfoTest` | Argument parsing, default presentation contexts, file vs path overloads, command-line idempotency. |
| R-12 | STOW-RS / multipart HTTP correctness | `DicomStowRS`, `DicomStowConfig`, `MultipartBody`, `MultipartReader`, `MultipartHeaderParser`, `BoundaryExtractor`, `ContentType`, `FilePayload` | `DicomStowRSTest`, `DicomStowConfigTest`, `MultipartBodyTest`, `MultipartReaderTest`, `MultipartHeaderParserTest`, `BoundaryExtractorTest`, `ContentTypeTest`, `FilePayloadTest` | Boundary detection, content-type parsing, multipart segmentation, content-length handling, unknown-size streaming. |
| R-13 | Determinism / reproducibility | `AnatomicBuilder` (lookup maps initialised once), `HangingProtocol`, `presets.xml` parsing, sorted enum iteration | `AnatomicBuilderTest`, `AnatomicRegionTest`, `HangingProtocolTest`, `HPDisplaySetTest`, `HPImageSetTest`, `HPSelectorTest`, `HangingProtocolEnumsTest`, `RelativeTimeUnitsTest`, `PresetWindowLevelTest` | Single-JVM determinism of dictionary lookups and hanging-protocol resolution. |
| R-14 | Error-handling contract (typed exceptions, status codes) | DICOM SCU/SCP entry points, STOW-RS, file I/O classes | `EchoTest`, `CFindTest`, `CStoreTest`, `DicomStowRSTest`, `StoreSCPTest`, `TranscoderTest`, `ImageAdapterTest` | DICOM status-code propagation through `DicomState`; typed `HttpException` for STOW-RS; specific message substrings asserted where they are part of the API contract. |
| R-15 | Geometry / orientation correctness | `Vector3`, `ImageOrientation`, `PatientOrientation` | `Vector3OrientationTests`, `ImageOrientationTest`, `PatientOrientationTest` | Patient-axis classification (LR / AP / FH), oblique fallback, locale-sensitive label formatting. |
| R-16 | DICOM macro / structured reference integrity | `Code`, `ItemCode`, `Module`, `DocumentReference`, `ReferenceStructures`, `SecurityParameters`, `SOPInstanceReferenceHierarchy` | `CodeTest`, `ModuleTest`, `DocumentReferenceTest`, `ReferenceStructuresTest`, `SecurityParametersTest`, `SOPInstanceReferenceHierarchyTest` | Sequence-vs-item normalisation, code-value equality, hierarchy navigation. |

## 3. Changes recorded in this revision (2026-05-21)

Test and production-code changes made in this revision, by risk category:

1. **Deprecated API removal.** Every member previously marked
   `@Deprecated(forRemoval = true)` has been removed. The removed surface
   was either covered by an equivalent non-deprecated API in the same class
   or — for `DicomImageUtils` — already delegated wholesale to dedicated
   utility classes. See §3.1 for the migration matrix.
2. **R-02** — `TlsOptions.SSL_3` has been removed. The convenience
   constructor defaults to `MODERN_TLS` (`TLSv1.2 + TLSv1.3`), so consumers
   that previously had to override the constant get a secure default
   automatically.
3. **R-04, R-06** — `BytesWithImageDescriptor` no longer exposes the
   legacy `bigEndian()` / `floatPixelData()` defaults. Implementations
   already overrode the canonical `isBigEndian()` / `isFloatPixelData()`
   methods, and no caller used the legacy names.
4. **Test fixture for shared JVM state** — the in-repo
   `LocaleTimeZoneExtension`, `DefaultLocale`, and `DefaultTimeZone`
   annotations (added in the previous revision in place of junit-pioneer)
   are now consistently applied across tests under R-08 / R-09 / R-15.
5. **R-12, R-13** — the previously flaky
   `DicomStowRSTest.should_create_stream_payload_with_unknown_size`
   assertion has been widened from `HttpException.class` to
   `IOException.class` (its parent). Both a real HTTP 4xx/5xx response and a
   closed connection are valid failure modes for an upload to the
   unreachable test URL, and `HttpException extends IOException` already.
   The test now passes deterministically under Surefire's parallel load.
6. **Test harness — Mockito agent** — `mockito-core` is now loaded as a
   `-javaagent` at JVM start (`maven-dependency-plugin`'s
   `resolve-dependency-paths` resolves the artifact path; Surefire's
   `argLine` consumes it). This removes the self-attach warning that the
   JDK is phasing out and aligns the build with the sibling
   `weasis-core-img` project.

Test count: 4584. All passing.

### 3.1 Deprecated-API removal — migration matrix

All members previously marked `@Deprecated(forRemoval = true)` are gone in
this revision. Code that consumed them must migrate as follows:

| Removed (was) | Use instead |
|---------------|-------------|
| `DicomImageUtils` (whole class) — facade for DICOM image ops | `DicomAttributeUtils` (DICOM attributes), `PaletteColorUtils` (palette colour), `LookupTableUtils` (LUTs), `PixelDataUtils` (pixel data), `RescaleUtils` (rescale) |
| `PaletteColorUtils.getRGBImageFromPaletteColorModel(PlanarImage, Attributes)` | Two-step: `PaletteColorUtils.getPaletteColorLookupTable(Attributes)` → `PaletteColorUtils.getRGBImageFromPaletteColorModel(PlanarImage, LookupTableCV)` |
| `BytesWithImageDescriptor.bigEndian()` (default method) | `BytesWithImageDescriptor.isBigEndian()` |
| `BytesWithImageDescriptor.floatPixelData()` (default method) | `BytesWithImageDescriptor.isFloatPixelData()` |
| `DicomListener(File)` / `DicomListener(File, DicomProgress)` | `DicomListener(Path)` / `DicomListener(Path, DicomProgress)` |
| `Dicomizer.pdf(Attributes, File, File)` | `Dicomizer.pdf(Attributes, Path, Path)` |
| `Dicomizer.jpeg(Attributes, File, File, boolean)` | `Dicomizer.jpeg(Attributes, Path, Path, boolean)` |
| `GetSCU.setStorageDirectory(File)` | `GetSCU.setStorageDirectory(Path)` |
| `GetSCU.retrieve(File)` | `GetSCU.retrieve(Path)` |
| `FilesetInfo.getDescriptorFileAsLegacy()` | `FilesetInfo.getDescriptorFile()` |
| `FilesetInfo.setDescriptorFileFromLegacy(File)` | `FilesetInfo.setDescriptorFile(Path)` |
| `TlsOptions.SSL_3` | none — `MODERN_TLS` (`TLSv1.2 + TLSv1.3`) is the default |
| `ViewerMessage.eLevel` (enum + ctor + accessor) | `ViewerMessage.Level` + `ViewerMessage(String, String, Level)` + `ViewerMessage.level()` |
| `AnatomicBuilder.Category.getCategoryFromContextUID(String)` | `AnatomicBuilder.Category.fromContextUID(String)` (returns `Optional<Category>`) |
| `BodyPart.getBodyPartFromCode(String)` | `BodyPart.fromCode(String)` |
| `SurfacePart.getSurfacePartFromCode(String)` | `SurfacePart.fromCode(String)` |
| `AnatomicModifier.getAnatomicModifierFromCode(String)` | `AnatomicModifier.fromCode(String)` |
| `CodingScheme.getSchemeFromDesignator(String)` | `CodingScheme.fromDesignator(String)` (returns `Optional<CodingScheme>`) |
| `CodingScheme.getSchemeFromUid(String)` | `CodingScheme.fromUid(String)` (returns `Optional<CodingScheme>`) |

The non-deprecated APIs are exercised directly by `DicomImageAdapterTest`,
`LookupTableUtilsTest`, `RescaleUtilsTest`, `PixelDataUtilsTest`,
`PaletteColorUtilsTest`, `DicomAttributeUtilsTest`, `DicomImageReaderTest`,
`DicomListenerTest`, `DicomizerTest`, `GetSCUTest`, `FilesetInfoTest`,
`ViewerMessageTest`, `AnatomicBuilderTest`, and `AnatomicRegionTest`.

## 4. Items not covered at unit level

The following are intentionally out of scope for unit tests in this
repository. Consumers should decide whether they need to address them in
their own integration / system test layer.

- **R-10 against a real PACS.** `org.weasis.dicom.real.*IT` exists for
  this purpose; it is only run under `-Pdicom-integration` against either
  the public test PACS at `www.dicomserver.co.uk` (default,
  `dicom-configs/public-server.properties`), a local dcm4che container
  (`local-dcm4che.properties`), or a TLS-enabled target
  (`secure-server.properties`). CI does not run integration tests, so the
  end-to-end network path is the consumer's responsibility to verify.
- **R-02 against a hostile server.** `TlsOptions` is only exercised at
  the configuration level. Mutual-TLS negotiation, certificate-pinning,
  and rejection of weak cipher suites in a live handshake belong to the
  integration / system layer.
- **R-04 for codec-specific quirks.** JPEG / JPEG-LS / JPEG-2000 / RLE /
  MPEG-2 / MP4 decoding is delegated to the OpenCV-wrapped codecs.
  Assessing those external decoders against vendor-specific DICOM corpora
  is the consumer's responsibility.
- **Cross-platform pixel reproducibility.** CI runs on one OS/arch per
  invocation (Ubuntu / x86-64 on GitHub Actions). Cross-platform bit-exact
  reproducibility belongs in a consumer compatibility matrix.

## 5. Test-suite consistency observations

- All unit-test classes share the same conventions:
  `@DisplayNameGeneration(ReplaceUnderscores.class)`, `@Nested` for
  behavioural grouping, `assertAll` for multi-assertion atomicity,
  `@ParameterizedTest` with `@NullAndEmptySource` on string-accepting
  public APIs.
- The framework is **JUnit 6 + Mockito** only (`junit-jupiter`,
  `junit-jupiter-params`, `mockito-core`, `mockito-junit-jupiter`). The
  project does **not** depend on AssertJ or junit-pioneer; locale /
  timezone pinning is provided by the in-repo `LocaleTimeZoneExtension`
  (`org.weasis.dicom.junit`).
- Surefire is configured with `<parallel>all</parallel>`. Tests that pin
  default Locale or TimeZone MUST use `@DefaultLocale` / `@DefaultTimeZone`
  so the previous value is restored in `afterEach`.
- Integration tests use the `IT` suffix and run via `maven-failsafe-plugin`
  only under `-Pdicom-integration`; they are excluded from the default
  Surefire invocation.

## 6. Optional traceability convention

If you maintain a software-requirements or hazard register in your own
project that consumes this library, you can tag tests with
`@Tag("SR-xxx")` (or your own ID scheme). SonarCloud and Surefire XML both
surface JUnit tags, which lets a reviewer cross-reference requirement IDs
to test methods without restructuring the suite.

## 7. Running the verification locally

```
mvn -f weasis-dicom-tools -B test                                  # full unit suite
mvn -f weasis-dicom-tools -B -Pcoverage verify                     # with JaCoCo
mvn -f weasis-dicom-tools -Pdicom-integration verify               # add IT suite
mvn -f weasis-dicom-tools test -Dtest=TranscoderTest               # single class
mvn -f weasis-dicom-tools -Pdicom-integration verify -Ddicom.test.config=local-dcm4che
```

The native OpenCV library is required and is unpacked automatically by the
build to `weasis-dicom-tools/target/lib/<os-name>-<cpu-name>/`. Surefire's
`argLine` points `-Djava.library.path` at that directory.