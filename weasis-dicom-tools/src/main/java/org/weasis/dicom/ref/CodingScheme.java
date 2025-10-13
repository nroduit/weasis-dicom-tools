/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.ref;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.weasis.core.util.StringUtil;

/**
 * Comprehensive enumeration of standard medical and healthcare coding schemes used in DICOM. Each
 * coding scheme is identified by a unique designator, UID, and descriptive name, following
 * international healthcare informatics standards.
 *
 * <p>This enum provides lookup capabilities for both designators (short identifiers) and UIDs
 * (universal identifiers) used in various healthcare contexts including:
 *
 * <ul>
 *   <li>Medical terminology (SNOMED CT, ICD, LOINC)
 *   <li>Anatomical classification systems (FMA, RadLex)
 *   <li>DICOM-specific terminologies (DCM)
 *   <li>Regional and specialized vocabularies
 * </ul>
 *
 * @see <a
 *     href="https://dicom.nema.org/medical/dicom/current/output/chtml/part16/chapter_8.html">DICOM
 *     PS3.16 - Coding Schemes</a>
 */
public enum CodingScheme {
  ACR("ACR", "2.16.840.1.113883.6.76", "ACR Index"),
  ASTM_SIGPURPOSE("ASTM-sigpurpose", "1.2.840.10065.1.12", "ASTM E 2084"),
  C4("C4", "2.16.840.1.113883.6.12", "CPT-4"),
  C5("C5", "2.16.840.1.113883.6.82", "CPT-5"),
  CADSR("caDSR", "2.16.840.1.113883.3.26.2", "Cancer Data Standard Repository"),
  CD2("CD2", "2.16.840.1.113883.6.13", "CDT-2"),
  CTV3("CTV3", "2.16.840.1.113883.6.6", "Clinical Terms Version 3"),
  DC("DC", "1.2.840.10008.2.16.10", "Dublin Core"),
  DCM("DCM", "1.2.840.10008.2.16.4", "DICOM Controlled Terminology"),
  DCMUID("DCMUID", "1.2.840.10008.2.6.1", "DICOM UID Registry"),
  FMA("FMA", "2.16.840.1.113883.6.119", "The Foundational Model of Anatomy Ontology"),
  HPC("HPC", "2.16.840.1.113883.6.14", "HPC"),
  I10("I10", "2.16.840.1.113883.6.3", "ICD-10"),
  I10C("I10C", "2.16.840.1.113883.6.90", "ICD-10-CM"),
  I10P("I10P", "2.16.840.1.113883.6.4", "ICD-10-PCS"),
  I11("I11", "1.2.840.10008.2.16.16", "ICD-11"),
  I9("I9", "2.16.840.1.113883.6.42", "ICD-9"),
  I9C("I9C", "2.16.840.1.113883.6.2", "ICD-9-CM"),
  ICDO3("ICDO3", "2.16.840.1.113883.6.43.1", "ICD-O-3"),
  IBSI("IBSI", "1.2.840.10008.2.16.13", "Image Biomarker Standardisation Initiative"),
  ISO639_1("ISO639_1", "2.16.840.1.113883.6.99", "ISO 639-1"),
  ISO639_2("ISO639_2", "2.16.840.1.113883.6.100", "ISO 639-2"),
  ISO3166_1("ISO3166_1", "2.16.1", "ISO 3166-1"),
  ITIS_TSN("ITIS_TSN", "1.2.840.10008.2.16.7", "ITIS TSN"),
  LN("LN", "2.16.840.1.113883.6.1", "LOINC"),
  MA("MA", "1.2.840.10008.2.16.5", "Adult Mouse Anatomy Ontology"),
  MAYOASRG(
      "MAYOASRG",
      "1.2.840.10008.2.16.12",
      "Mayo Clinic Non-radiological Images Specific Body Structure Anatomical Surface Region Guide"),
  MGI("MGI", "1.2.840.10008.2.16.8", "MGI"),
  MSH("MSH", "2.16.840.1.113883.6.177", "MeSH"),
  NCIT("NCIt", "2.16.840.1.113883.3.26.1.1", "NCI Thesaurus"),
  NDC("NDC", "2.16.840.1.113883.6.69", "National Drug Code Directory"),
  NEU("NEU", "2.16.840.1.113883.6.210", "NeuroNames"),
  NICIP("NICIP", "2.16.840.1.113883.2.1.3.2.4.21", "NICIP"),
  NYUMCCG(
      "NYUMCCG",
      "1.2.840.10008.2.16.11",
      "New York University Melanoma Clinical Cooperative Group"),
  PATHLEX("PATHLEX", "1.3.6.1.4.1.19376.1.8.2.1", "PathLex"),
  POS("POS", "2.16.840.1.113883.6.50", "POS"),
  PUBCHEM_CID("PUBCHEM_CID", "1.2.840.10008.2.16.9", "PubChem"),
  RADLEX("RADLEX", "2.16.840.1.113883.6.256", "RadLex"),
  RADELEMENT("RADELEMENT", "1.2.840.10008.2.16.15", "RadElement"),
  RFC3066("RFC3066", "2.16.840.1.113883.6.121", "RFC 3066"),
  RFC5646("RFC5646", "2.16.840.1.113883.6.316", "RFC 5646"),
  RO("RO", "1.2.840.10008.2.16.14", "Radiomics Ontology"),
  RRID("RRID", "1.2.840.10008.2.16.18", "RRID"),
  RXNORM("RXNORM", "2.16.840.1.113883.6.88", "RXNORM"),
  SDM("99SDM", "2.16.840.1.113883.6.53", "SDM"),
  SNM3("SNM3", "2.16.840.1.113883.6.51", "SNOMED V3"),
  SCT("SCT", "2.16.840.1.113883.6.96", "SNOMED CT"),
  // SRT("SRT", "2.16.840.1.113883.6.96", "SNOMED CT"), // Legacy designation
  UBERON("UBERON", "1.2.840.10008.2.16.6", "UBERON"),
  UCUM("UCUM", "2.16.840.1.113883.6.8", "UCUM"),
  UMLS("UMLS", "2.16.840.1.113883.6.86", "UMLS"),
  UNS("UNS", "1.2.840.10008.2.16.17", "UNS"),
  UNKNOWN("UNKNOWN", "1.2.840.10008.2.999.999", "Unknown Coding Scheme"),
  UPC("UPC", "2.16.840.1.113883.6.55", "UPC");

  private static final Map<String, CodingScheme> DESIGNATOR_LOOKUP =
      Stream.of(values())
          .collect(Collectors.toUnmodifiableMap(CodingScheme::getDesignator, Function.identity()));

  private static final Map<String, CodingScheme> UID_LOOKUP =
      Stream.of(values())
          .collect(Collectors.toUnmodifiableMap(CodingScheme::getUid, Function.identity()));

  private final String designator;
  private final String uid;
  private final String codeName;

  CodingScheme(String designator, String uid, String codeName) {
    this.designator = designator;
    this.uid = uid;
    this.codeName = codeName;
  }

  /**
   * Returns the coding scheme designator (short identifier).
   *
   * @return the designator string
   */
  public String getDesignator() {
    return designator;
  }

  /**
   * Returns the unique identifier (OID) for this coding scheme.
   *
   * @return the UID string
   */
  public String getUid() {
    return uid;
  }

  /**
   * Returns the human-readable name of this coding scheme.
   *
   * @return the descriptive name
   */
  public String getCodeName() {
    return codeName;
  }

  @Override
  public String toString() {
    return designator;
  }

  /**
   * Finds a coding scheme by its designator.
   *
   * @param designator the coding scheme designator to look up
   * @return an Optional containing the matching coding scheme, or empty if not found
   */
  public static Optional<CodingScheme> fromDesignator(String designator) {
    if (StringUtil.hasText(designator)) {
      return Optional.ofNullable(DESIGNATOR_LOOKUP.get(designator));
    }
    return Optional.empty();
  }

  /**
   * Finds a coding scheme by its unique identifier.
   *
   * @param uid the UID to look up
   * @return an Optional containing the matching coding scheme, or empty if not found
   */
  public static Optional<CodingScheme> fromUid(String uid) {
    if (StringUtil.hasText(uid)) {
      return Optional.ofNullable(UID_LOOKUP.get(uid));
    }
    return Optional.empty();
  }

  /**
   * @deprecated Use {@link #fromDesignator(String)} instead for better null safety.
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static CodingScheme getSchemeFromDesignator(String designator) {
    return fromDesignator(designator).orElse(null);
  }

  /**
   * @deprecated Use {@link #fromUid(String)} instead for better null safety.
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static CodingScheme getSchemeFromUid(String uid) {
    return fromUid(uid).orElse(null);
  }
}
