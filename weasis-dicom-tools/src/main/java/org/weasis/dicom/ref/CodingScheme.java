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

import java.util.Arrays;
import java.util.Objects;
import org.weasis.core.util.StringUtil;

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
  SRT("SRT", "2.16.840.1.113883.6.96", "SNOMED CT"),
  UBERON("UBERON", "1.2.840.10008.2.16.6", "UBERON"),
  UCUM("UCUM", "2.16.840.1.113883.6.8", "UCUM"),
  UMLS("UMLS", "2.16.840.1.113883.6.86", "UMLS"),
  UNS("UNS", "1.2.840.10008.2.16.17", "UNS"),
  UPC("UPC", "2.16.840.1.113883.6.55", "UPC");

  private final String designator;
  private final String uid;
  private final String codeName;

  CodingScheme(String designator, String uid, String codeName) {
    this.designator = designator;
    this.uid = uid;
    this.codeName = codeName;
  }

  public String getDesignator() {
    return designator;
  }

  public String getUid() {
    return uid;
  }

  public String getCodeName() {
    return codeName;
  }

  @Override
  public String toString() {
    return designator;
  }

  public static CodingScheme getSchemeFromDesignator(String designator) {
    if (!StringUtil.hasText(designator)) {
      return null;
    }
    return Arrays.stream(CodingScheme.values())
        .filter(c -> Objects.equals(c.designator, designator))
        .findFirst()
        .orElse(null);
  }

  public static CodingScheme getSchemeFromUid(String uid) {
    if (!StringUtil.hasText(uid)) {
      return null;
    }
    return Arrays.stream(CodingScheme.values())
        .filter(c -> Objects.equals(c.uid, uid))
        .findFirst()
        .orElse(null);
  }
}
