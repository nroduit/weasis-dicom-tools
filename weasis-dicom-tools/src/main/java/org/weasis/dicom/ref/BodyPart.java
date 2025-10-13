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

import static org.weasis.dicom.ref.CodingScheme.SCT;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Comprehensive enumeration of anatomical body parts used in DICOM imaging. Each body part is
 * defined with SNOMED CT coding, legacy DICOM codes, and classification attributes for medical
 * imaging workflows.
 *
 * <p>Body parts are categorized by:
 *
 * <ul>
 *   <li><strong>Paired</strong>: Anatomical structures that naturally occur in pairs (e.g., eyes,
 *       lungs)
 *   <li><strong>Common</strong>: Frequently used in general medical imaging
 *   <li><strong>Endoscopic</strong>: Suitable for endoscopic imaging procedures
 * </ul>
 *
 * <p>This enum provides both modern SNOMED CT codes and legacy DICOM codes for backward
 * compatibility with older imaging systems.
 *
 * @see AnatomicItem
 * @see AnatomicRegion
 * @see CodingScheme
 */
public enum BodyPart implements AnatomicItem {
  ABDOMEN(SCT, 818981001, "ABDOMEN", false, true, false),
  ABDOMEN_AND_PELVIS(SCT, 818982008, "ABDOMENPELVIS", false, true, false),
  ABDOMINAL_AORTA(SCT, 7832008, "ABDOMINALAORTA", false, false, false),
  ACROMIOCLAVICULAR_JOINT(SCT, 85856004, "ACJOINT", true, true, false),
  ADRENAL_GLAND(SCT, 23451007, "ADRENAL", true, false, false),
  AMNIOTIC_FLUID(SCT, 77012006, "AMNIOTICFLUID", false, false, false),
  ANKLE_JOINT(SCT, 70258002, "ANKLE", true, true, false),
  ANOMALOUS_PULMONARY_VEIN(SCT, 128585006, "", false, false, false),
  ANTECUBITAL_VEIN(SCT, 128553008, "ANTECUBITALV", true, false, false),
  ANTERIOR_CARDIAC_VEIN(SCT, 194996006, "ANTCARDIACV", false, false, false),
  ANTERIOR_CEREBRAL_ARTERY(SCT, 60176003, "ACA", true, false, false),
  ANTERIOR_COMMUNICATING_ARTERY(SCT, 8012006, "ANTCOMMA", false, false, false),
  ANTERIOR_SPINAL_ARTERY(SCT, 17388009, "ANTSPINALA", false, false, false),
  ANTERIOR_TIBIAL_ARTERY(SCT, 68053000, "ANTTIBIALA", true, false, false),
  ANUS_RECTUM_AND_SIGMOID_COLON(SCT, 110612005, "ANUSRECTUMSIGMD", false, false, true),
  AORTA(SCT, 15825003, "AORTA", false, false, false),
  AORTIC_ARCH(SCT, 57034009, "AORTICARCH", false, false, false),
  AORTIC_FISTULA(SCT, 128551005, "", false, false, false),
  APEX_OF_LEFT_VENTRICLE(SCT, 128564006, "", false, false, false),
  APEX_OF_LUNG(SCT, 86598002, "", true, true, false),
  APEX_OF_RIGHT_VENTRICLE(SCT, 128565007, "", false, false, false),
  APPENDIX(SCT, 66754008, "APPENDIX", false, false, false),
  ARTERY(SCT, 51114001, "ARTERY", true, false, false),
  ASCENDING_AORTA(SCT, 54247002, "ASCAORTA", false, false, false),
  ASCENDING_COLON(SCT, 9040008, "ASCENDINGCOLON", false, false, false),
  ATRIUM(SCT, 59652004, "", true, false, false),
  AXILLA(SCT, 91470000, "AXILLA", true, false, false),
  AXILLARY_ARTERY(SCT, 67937003, "AXILLARYA", true, false, false),
  AXILLARY_VEIN(SCT, 68705008, "AXILLARYV", true, false, false),
  AZYGOS_VEIN(SCT, 72107004, "AZYGOSVEIN", false, false, false),
  BACK(SCT, 77568009, "BACK", false, false, false),
  BAFFLE(SCT, 128981007, "", false, false, false),
  BASILAR_ARTERY(SCT, 59011009, "BASILARA", false, false, false),
  BILE_DUCT(SCT, 28273000, "BILEDUCT", false, true, true),
  BILIARY_TRACT(SCT, 34707002, "BILIARYTRACT", false, true, true),
  BLADDER(SCT, 89837001, "BLADDER", false, true, true),
  BLADDER_AND_URETHRA(SCT, 110837003, "BLADDERURETHRA", false, false, true),
  BODY_CONDUIT(SCT, 91830000, "", false, false, false),
  BOYD_S_PERFORATING_VEIN(SCT, 128548003, "", true, false, false),
  BRACHIAL_ARTERY(SCT, 17137000, "BRACHIALA", true, false, false),
  BRACHIAL_VEIN(SCT, 20115005, "BRACHIALV", true, false, false),
  BRAIN(SCT, 12738006, "BRAIN", false, false, false),
  BREAST(SCT, 76752008, "BREAST", true, true, false),
  BROAD_LIGAMENT(SCT, 34411009, "", false, false, false),
  BRONCHUS(SCT, 955009, "BRONCHUS", true, true, true),
  BUTTOCK(SCT, 46862004, "BUTTOCK", true, false, false),
  CALCANEUS(SCT, 80144004, "CALCANEUS", true, true, false),
  CALF_OF_LEG(SCT, 53840002, "CALF", true, false, false),
  CALYX(SCT, 2334006, "", false, false, false),
  CAROTID_ARTERY(SCT, 69105007, "CAROTID", true, false, false),
  CAROTID_BULB(SCT, 21479005, "BULB", true, false, false),
  CELIAC_ARTERY(SCT, 57850000, "CELIACA", false, false, false),
  CEPHALIC_VEIN(SCT, 20699002, "CEPHALICV", true, false, false),
  CEREBELLUM(SCT, 113305005, "CEREBELLUM", true, false, false),
  CEREBRAL_ARTERY(SCT, 88556005, "CEREBRALA", true, false, false),
  CEREBRAL_HEMISPHERE(SCT, 372073000, "CEREBHEMISPHERE", true, false, false),
  CERVICAL_SPINE(SCT, 122494005, "CSPINE", false, true, false),
  CERVICO_THORACIC_SPINE(SCT, 1217257000, "CTSPINE", false, true, false),
  CERVIX(SCT, 71252005, "CERVIX", false, false, true),
  CHEEK(SCT, 60819002, "CHEEK", false, false, false),
  CHEST_ABDOMEN_AND_PELVIS(SCT, 416775004, "CHESTABDPELVIS", false, true, false),
  CHEST_AND_ABDOMEN(SCT, 416550000, "CHESTABDOMEN", false, true, false),
  CHOROID_PLEXUS(SCT, 80621003, "CHOROIDPLEXUS", true, false, false),
  CIRCLE_OF_WILLIS(SCT, 11279006, "CIRCLEOFWILLIS", false, false, false),
  CLAVICLE(SCT, 51299004, "CLAVICLE", true, true, false),
  COCCYX(SCT, 64688005, "COCCYX", false, true, false),
  COLON(SCT, 71854001, "COLON", false, true, false),
  COMMON_ATRIUM(SCT, 253276007, "", false, false, false),
  COMMON_BILE_DUCT(SCT, 79741001, "COMMONBILEDUCT", false, true, true),
  COMMON_CAROTID_ARTERY(SCT, 32062004, "CCA", true, false, false),
  COMMON_FEMORAL_ARTERY(SCT, 181347005, "CFA", true, false, false),
  COMMON_FEMORAL_VEIN(SCT, 397363009, "CFV", true, false, false),
  COMMON_ILIAC_ARTERY(SCT, 73634005, "COMILIACA", true, false, false),
  COMMON_ILIAC_VEIN(SCT, 46027005, "COMILIACV", true, false, false),
  COMMON_VENTRICLE(SCT, 45503006, "", false, false, false),
  CONGENITAL_CORONARY_ARTERY_FISTULA_TO_LEFT_ATRIUM(SCT, 128555001, "", false, false, false),
  CONGENITAL_CORONARY_ARTERY_FISTULA_TO_LEFT_VENTRICLE(SCT, 128556000, "", false, false, false),
  CONGENITAL_CORONARY_ARTERY_FISTULA_TO_RIGHT_ATRIUM(SCT, 128557009, "", false, false, false),
  CONGENITAL_CORONARY_ARTERY_FISTULA_TO_RIGHT_VENTRICLE(SCT, 128558004, "", false, false, false),
  PULMONARY_ARTERIOVENOUS_FISTULA(SCT, 111289009, "", false, false, false),
  CORNEA(SCT, 28726007, "CORNEA", true, false, false),
  CORONARY_ARTERY(SCT, 41801008, "CORONARYARTERY", false, false, false),
  CORONARY_SINUS(SCT, 90219004, "CORONARYSINUS", false, false, false),
  DESCENDING_AORTA(SCT, 32672002, "DESCAORTA", false, false, false),
  DESCENDING_COLON(SCT, 32622004, "DESCENDINGCOLON", false, false, false),
  DODD_S_PERFORATING_VEIN(SCT, 128554002, "", true, false, false),
  DUODENUM(SCT, 38848004, "DUODENUM", false, true, false),
  EAR(SCT, 117590005, "EAR", true, false, false),
  ELBOW_JOINT(SCT, 16953009, "ELBOW", true, true, false),
  ENDOMETRIUM(SCT, 2739003, "ENDOMETRIUM", false, false, false),
  ENDO_NASAL(SCT, 53342003, "ENDONASAL", false, false, false),
  ENDO_NASOPHARYNGEAL(SCT, 18962004, "ENDONASOPHARYNYX", false, false, false),
  ENDO_VASCULAR(SCT, 59820001, "ENDOVASCULAR", false, false, false),
  ENDO_VESICAL(SCT, 48367006, "ENDOVESICAL", false, false, false),
  ENTIRE_BODY(SCT, 38266002, "WHOLEBODY", false, true, false),
  EPIDIDYMIS(SCT, 87644002, "EPIDIDYMIS", true, false, false),
  EPIGASTRIC_REGION(SCT, 27947004, "EPIGASTRIC", false, false, false),
  ESOPHAGUS(SCT, 32849002, "ESOPHAGUS", false, true, false),
  ESOPHAGUS_STOMACH_AND_DUODENUM(SCT, 110861005, "", false, true, true),
  EXTERNAL_AUDITORY_CANAL(SCT, 84301002, "EAC", true, false, true),
  EXTERNAL_CAROTID_ARTERY(SCT, 22286001, "ECA", true, false, false),
  EXTERNAL_ILIAC_ARTERY(SCT, 113269004, "EXTILIACA", true, false, false),
  EXTERNAL_ILIAC_VEIN(SCT, 63507001, "EXTILIACV", true, false, false),
  EXTERNAL_JUGULAR_VEIN(SCT, 71585003, "EXTJUGV", true, false, false),
  EXTREMITY(SCT, 66019005, "EXTREMITY", true, true, false),
  EYE(SCT, 81745001, "EYE", true, true, false),
  EYELID(SCT, 80243003, "EYELID", true, false, false),
  FACE(SCT, 89545001, "FACE", false, false, false),
  FACIAL_ARTERY(SCT, 23074001, "FACIALA", true, false, false),
  FACIAL_BONES(SCT, 91397008, "", false, true, false),
  FEMORAL_ARTERY(SCT, 7657000, "FEMORALA", true, false, false),
  FEMORAL_VEIN(SCT, 83419000, "FEMORALV", true, false, false),
  FEMUR(SCT, 71341001, "FEMUR", true, true, false),
  FIBULA(SCT, 87342007, "FIBULA", true, true, false),
  FINGER(SCT, 7569003, "FINGER", true, true, false),
  FLANK(SCT, 58602004, "FLANK", false, false, false),
  FONTANEL_OF_SKULL(SCT, 79361005, "FONTANEL", false, false, false),
  FOOT(SCT, 56459004, "FOOT", true, true, false),
  FOREARM(SCT, 14975008, "FOREARM", true, true, false),
  FOURTH_VENTRICLE(SCT, 35918002, "4THVENTRICLE", false, false, false),
  GALLBLADDER(SCT, 28231008, "GALLBLADDER", false, true, true),
  GASTRIC_VEIN(SCT, 110568007, "GASTRICV", true, false, false),
  GENICULAR_ARTERY(SCT, 128559007, "GENICULARA", true, false, false),
  GESTATIONAL_SAC(SCT, 300571009, "GESTSAC", false, false, false),
  GREAT_CARDIAC_VEIN(SCT, 5928000, "", false, false, false),
  GREAT_SAPHENOUS_VEIN(SCT, 60734001, "GSV", true, false, false),
  HAND(SCT, 85562004, "HAND", true, true, false),
  HEAD(SCT, 69536005, "HEAD", false, true, false),
  HEAD_AND_NECK(SCT, 774007, "HEADNECK", false, true, false),
  HEART(SCT, 80891009, "HEART", false, true, false),
  HEPATIC_ARTERY(SCT, 76015000, "HEPATICA", true, false, false),
  HEPATIC_VEIN(SCT, 8993003, "HEPATICV", true, false, false),
  HIP_JOINT(SCT, 24136001, "HIP", true, true, false),
  HUMERUS(SCT, 85050009, "HUMERUS", true, true, false),
  HUNTERIAN_PERFORATING_VEIN(SCT, 128560002, "", true, false, false),
  HYPOGASTRIC_REGION(SCT, 11708003, "HYPOGASTRIC", false, false, false),
  HYPOPHARYNX(SCT, 81502006, "HYPOPHARYNX", false, false, false),
  ILEUM(SCT, 34516001, "ILEUM", false, true, false),
  ILIAC_AND_OR_FEMORAL_ARTERY(SCT, 299716001, "", true, false, false),
  ILIAC_ARTERY(SCT, 10293006, "ILIACA", true, false, false),
  ILIAC_VEIN(SCT, 244411005, "ILIACV", true, false, false),
  ILIUM(SCT, 22356005, "ILIUM", true, true, false),
  INFERIOR_CARDIAC_VEIN(SCT, 195416006, "", false, false, false),
  INFERIOR_LEFT_PULMONARY_VEIN(SCT, 51249003, "", false, false, false),
  INFERIOR_MESENTERIC_ARTERY(SCT, 33795007, "INFMESA", false, false, false),
  INFERIOR_RIGHT_PULMONARY_VEIN(SCT, 113273001, "", false, false, false),
  INFERIOR_VENA_CAVA(SCT, 64131007, "INFVENACAVA", false, false, false),
  INGUINAL_REGION(SCT, 26893007, "INGUINAL", true, false, true),
  INNOMINATE_ARTERY(SCT, 12691009, "INNOMINATEA", false, false, false),
  INNOMINATE_VEIN(SCT, 8887007, "INNOMINATEV", true, false, false),
  INTERNAL_AUDITORY_CANAL(SCT, 361078006, "IAC", true, true, false),
  INTERNAL_CAROTID_ARTERY(SCT, 86117002, "ICA", true, false, false),
  INTERNAL_ILIAC_ARTERY(SCT, 90024005, "INTILIACA", true, false, false),
  INTERNAL_JUGULAR_VEIN(SCT, 12123001, "INTJUGULARV", true, false, false),
  INTERNAL_MAMMARY_ARTERY(SCT, 69327007, "INTMAMMARYA", true, false, false),
  INTRA_ABDOMINOPELVIC(SCT, 818987002, "", false, false, true),
  INTRA_ARTICULAR(SCT, 131183008, "", false, false, false),
  INTRACRANIAL(SCT, 1101003, "INTRACRANIAL", false, false, false),
  INTRA_PELVIC(SCT, 816989007, "", false, false, true),
  JAW_REGION(SCT, 661005, "JAW", false, true, false),
  JEJUNUM(SCT, 21306003, "JEJUNUM", false, true, false),
  JOINT(SCT, 39352004, "JOINT", true, false, true),
  JUXTAPOSED_ATRIAL_APPENDAGE(SCT, 128563000, "", false, false, false),
  KIDNEY(SCT, 64033007, "KIDNEY", false, false, true),
  KNEE(SCT, 72696002, "KNEE", true, true, true),
  LACRIMAL_ARTERY(SCT, 59749000, "LACRIMALA", true, false, false),
  LACRIMAL_ARTERY_OF_RIGHT_EYE(SCT, 128979005, "", false, false, false),
  LARGE_INTESTINE(SCT, 14742008, "LARGEINTESTINE", false, true, true),
  LARYNX(SCT, 4596009, "LARYNX", false, true, true),
  LATERAL_VENTRICLE(SCT, 66720007, "LATVENTRICLE", true, false, false),
  LEFT_ATRIUM(SCT, 82471001, "LATRIUM", false, false, false),
  LEFT_AURICULAR_APPENDAGE(SCT, 33626005, "", false, false, false),
  LEFT_FEMORAL_ARTERY(SCT, 113270003, "LFEMORALA", false, false, false),
  LEFT_HEPATIC_VEIN(SCT, 273202007, "LHEPATICV", false, false, false),
  LEFT_HYPOCHONDRIAC_REGION(SCT, 133945003, "LHYPOCHONDRIAC", false, false, false),
  LEFT_INGUINAL_REGION(SCT, 85119005, "LINGUINAL", false, false, false),
  LEFT_LOWER_QUADRANT_OF_ABDOMEN(SCT, 68505006, "LLQ", false, false, false),
  LEFT_LUMBAR_REGION(SCT, 1017210004, "LLUMBAR", false, false, false),
  LEFT_PORTAL_VEIN(SCT, 70253006, "LPORTALV", false, false, false),
  LEFT_PULMONARY_ARTERY(SCT, 50408007, "LPULMONARYA", false, false, false),
  LEFT_UPPER_QUADRANT_OF_ABDOMEN(SCT, 86367003, "LUQ", false, false, false),
  LEFT_VENTRICLE(SCT, 87878005, "LVENTRICLE", false, false, false),
  LEFT_VENTRICLE_INFLOW(SCT, 70238003, "", false, false, false),
  LEFT_VENTRICLE_OUTFLOW_TRACT(SCT, 13418002, "", false, false, false),
  LINGUAL_ARTERY(SCT, 113264009, "LINGUALA", true, false, false),
  LIVER(SCT, 10200004, "LIVER", false, false, false),
  LOWER_INNER_QUADRANT_OF_BREAST(SCT, 19100000, "", true, false, false),
  LOWER_LEG(SCT, 30021000, "LOWERLEG", true, true, false),
  LOWER_LIMB(SCT, 61685007, "LOWERLIMB", true, true, false),
  LOWER_OUTER_QUADRANT_OF_BREAST(SCT, 33564002, "", true, false, false),
  LUMBAR_ARTERY(SCT, 34635009, "LUMBARA", true, false, false),
  LUMBAR_REGION(SCT, 52612000, "LUMBAR", true, false, false),
  LUMBAR_SPINE(SCT, 122496007, "LSPINE", false, true, false),
  LUMBO_SACRAL_SPINE(SCT, 1217253001, "LSSPINE", false, true, false),
  LUMEN_OF_BLOOD_VESSEL(SCT, 91747007, "LUMEN", false, false, true),
  LUNG(SCT, 39607008, "LUNG", true, false, false),
  MANDIBLE(SCT, 91609006, "MANDIBLE", false, true, false),
  MASTOID_BONE(SCT, 59066005, "MASTOID", true, true, false),
  MAXILLA(SCT, 70925003, "MAXILLA", true, true, false),
  MEDIASTINUM(SCT, 72410000, "MEDIASTINUM", false, true, true),
  MESENTERIC_ARTERY(SCT, 86570000, "MESENTRICA", false, false, false),
  MESENTERIC_VEIN(SCT, 128583004, "MESENTRICV", false, false, false),
  MIDDLE_CEREBRAL_ARTERY(SCT, 17232002, "MCA", true, false, false),
  MIDDLE_HEPATIC_VEIN(SCT, 273099000, "MIDHEPATICV", false, false, false),
  MORISONS_POUCH(SCT, 243977002, "MORISONSPOUCH", false, false, false),
  MOUTH(SCT, 123851003, "MOUTH", false, false, false),
  NASAL_BONE(SCT, 74386004, "", true, true, false),
  NASOPHARYNX(SCT, 360955006, "NASOPHARYNX", false, false, true),
  NECK(SCT, 45048000, "NECK", false, true, false),
  NECK_CHEST_ABDOMEN_AND_PELVIS(SCT, 416319003, "NECKCHESTABDPELV", false, true, false),
  NECK_CHEST_AND_ABDOMEN(SCT, 416152001, "NECKCHESTABDOMEN", false, true, false),
  NECK_AND_CHEST(SCT, 417437006, "NECKCHEST", false, true, false),
  NOSE(SCT, 45206002, "NOSE", false, false, false),
  OCCIPITAL_ARTERY(SCT, 31145008, "OCCPITALA", true, false, false),
  OCCIPITAL_VEIN(SCT, 32114007, "OCCIPTALV", true, false, false),
  OMENTAL_BURSA(SCT, 113346000, "", false, false, false),
  OMENTUM(SCT, 27398004, "", false, false, false),
  OPHTHALMIC_ARTERY(SCT, 53549008, "OPHTHALMICA", true, false, false),
  OPTIC_CANAL(SCT, 55024004, "OPTICCANAL", true, true, false),
  ORBITAL_STRUCTURE(SCT, 363654007, "ORBIT", true, true, false),
  OVARY(SCT, 15497006, "OVARY", true, false, false),
  PANCREAS(SCT, 15776009, "PANCREAS", false, true, false),
  PANCREATIC_DUCT(SCT, 69930009, "PANCREATICDUCT", false, true, true),
  PANCREATIC_DUCT_AND_BILE_DUCT_SYSTEMS(SCT, 110621006, "PANCBILEDUCT", false, true, true),
  PARANASAL_SINUS(SCT, 2095001, "", true, true, true),
  PARASTERNAL(SCT, 91691001, "PARASTERNAL", false, false, false),
  PARATHYROID(SCT, 111002, "PARATHYROID", true, false, false),
  PAROTID_GLAND(SCT, 45289007, "PAROTID", true, true, false),
  PATELLA(SCT, 64234005, "PATELLA", true, true, false),
  PATENT_DUCTUS_ARTERIOSUS(SCT, 83330001, "", false, false, false),
  PELVIS(SCT, 816092008, "PELVIS", false, true, false),
  PELVIS_AND_LOWER_EXTREMITIES(SCT, 1231522001, "PELVISLOWEXTREMT", false, true, false),
  PENILE_ARTERY(SCT, 282044005, "PENILEA", true, false, false),
  PENIS(SCT, 18911002, "PENIS", false, false, false),
  PERINEUM(SCT, 38864007, "PERINEUM", false, false, false),
  PERONEAL_ARTERY(SCT, 8821006, "PERONEALA", true, false, false),
  PHANTOM(SCT, 706342009, "PHANTOM", false, true, false),
  PHARYNX(SCT, 54066008, "PHARYNX", false, false, true),
  PHARYNX_AND_LARYNX(SCT, 312535008, "PHARYNXLARYNX", false, false, true),
  PLACENTA(SCT, 78067005, "PLACENTA", false, false, false),
  POPLITEAL_ARTERY(SCT, 43899006, "POPLITEALA", true, false, false),
  POPLITEAL_FOSSA(SCT, 32361000, "POPLITEALFOSSA", true, false, false),
  POPLITEAL_VEIN(SCT, 56849005, "POPLITEALV", true, false, false),
  PORTAL_VEIN(SCT, 32764006, "PORTALV", false, false, false),
  POSTERIOR_CEREBRAL_ARTERY(SCT, 70382005, "PCA", true, false, false),
  POSTERIOR_COMMUNICATING_ARTERY(SCT, 43119007, "POSCOMMA", true, false, false),
  POSTERIOR_MEDIAL_TRIBUTARY(SCT, 128569001, "", false, false, false),
  POSTERIOR_TIBIAL_ARTERY(SCT, 13363002, "POSTIBIALA", true, false, false),
  PRIMITIVE_AORTA(SCT, 14944004, "", false, false, false),
  PRIMITIVE_PULMONARY_ARTERY(SCT, 91707000, "", false, false, false),
  PROFUNDA_FEMORIS_ARTERY(SCT, 31677005, "PROFFEMA", true, false, false),
  PROFUNDA_FEMORIS_VEIN(SCT, 23438002, "PROFFEMV", true, false, false),
  PROSTATE(SCT, 41216001, "PROSTATE", false, true, false),
  PULMONARY_ARTERY(SCT, 81040000, "PULMONARYA", true, false, false),
  PULMONARY_ARTERY_CONDUIT(SCT, 128584005, "", false, false, false),
  PULMONARY_CHAMBER_OF_COR_TRIATRIATUM(SCT, 128586007, "", false, false, false),
  PULMONARY_VEIN(SCT, 122972007, "PULMONARYV", true, false, false),
  PULMONARY_VEIN_CONFLUENCE(SCT, 128566008, "", false, false, false),
  PULMONARY_VENOUS_ATRIUM(SCT, 128567004, "", false, false, false),
  RADIAL_ARTERY(SCT, 45631007, "RADIALA", true, false, false),
  RADIUS(SCT, 62413002, "RADIUS", true, false, false),
  RADIUS_AND_ULNA(SCT, 110535000, "RADIUSULNA", true, false, false),
  RECTOUTERINE_POUCH(SCT, 53843000, "CULDESAC", false, false, false),
  RECTUM(SCT, 34402009, "RECTUM", false, true, true),
  RENAL_ARTERY(SCT, 2841007, "RENALA", true, false, false),
  RENAL_PELVIS(SCT, 25990002, "", true, false, false),
  RENAL_VEIN(SCT, 56400007, "RENALV", true, false, false),
  RETROPERITONEUM(SCT, 82849001, "RETROPERITONEUM", false, false, false),
  RIB(SCT, 113197003, "RIB", true, true, false),
  RIGHT_ATRIUM(SCT, 73829009, "RATRIUM", false, false, false),
  RIGHT_AURICULAR_APPENDAGE(SCT, 68300000, "", false, false, false),
  RIGHT_FEMORAL_ARTERY(SCT, 69833005, "RFEMORALA", false, false, false),
  RIGHT_HEPATIC_VEIN(SCT, 272998002, "RHEPATICV", false, false, false),
  RIGHT_HYPOCHONDRIAC_REGION(SCT, 133946002, "RHYPOCHONDRIAC", false, false, false),
  RIGHT_INGUINAL_REGION(SCT, 37117007, "RINGUINAL", false, false, false),
  RIGHT_LOWER_QUADRANT_OF_ABDOMEN(SCT, 48544008, "RLQ", false, false, false),
  RIGHT_LUMBAR_REGION(SCT, 1017211000, "RLUMBAR", false, false, false),
  RIGHT_PORTAL_VEIN(SCT, 73931004, "RPORTALV", false, false, false),
  RIGHT_PULMONARY_ARTERY(SCT, 78480002, "RPULMONARYA", false, false, false),
  RIGHT_UPPER_QUADRANT_OF_ABDOMEN(SCT, 50519007, "RUQ", false, false, false),
  RIGHT_VENTRICLE(SCT, 53085002, "RVENTRICLE", false, false, false),
  RIGHT_VENTRICLE_INFLOW(SCT, 8017000, "", false, false, false),
  RIGHT_VENTRICLE_OUTFLOW_TRACT(SCT, 44627009, "", false, false, false),
  SACROILIAC_JOINT(SCT, 39723000, "SIJOINT", true, true, false),
  SACRUM(SCT, 54735007, "SSPINE", false, true, false),
  SAPHENOFEMORAL_JUNCTION(SCT, 128587003, "SFJ", true, false, false),
  SAPHENOUS_VEIN(SCT, 362072009, "SAPHENOUSV", true, false, false),
  SCALP(SCT, 41695006, "SCALP", false, false, false),
  SCAPULA(SCT, 79601000, "SCAPULA", true, true, false),
  SCLERA(SCT, 18619003, "SCLERA", true, false, false),
  SCROTUM(SCT, 20233005, "SCROTUM", true, false, false),
  SELLA_TURCICA(SCT, 42575006, "SELLA", false, true, false),
  SEMINAL_VESICLE(SCT, 64739004, "SEMVESICLE", false, false, false),
  SESAMOID_BONES_OF_FOOT(SCT, 58742003, "SESAMOID", true, true, false),
  SHOULDER(SCT, 16982005, "SHOULDER", true, true, true),
  SIGMOID_COLON(SCT, 60184004, "SIGMOID", false, false, true),
  SKULL(SCT, 89546000, "SKULL", false, true, false),
  SMALL_INTESTINE(SCT, 30315005, "SMALLINTESTINE", false, true, false),
  SPINAL_CORD(SCT, 2748008, "SPINALCORD", false, false, false),
  SPINE(SCT, 421060004, "SPINE", false, true, true),
  SPLEEN(SCT, 78961009, "SPLEEN", false, false, false),
  SPLENIC_ARTERY(SCT, 22083002, "SPLENICA", false, false, false),
  SPLENIC_VEIN(SCT, 35819009, "SPLENICV", false, false, false),
  STERNOCLAVICULAR_JOINT(SCT, 7844006, "SCJOINT", true, true, false),
  STERNUM(SCT, 56873002, "STERNUM", false, true, false),
  STOMACH(SCT, 69695003, "STOMACH", false, true, false),
  SUBCLAVIAN_ARTERY(SCT, 36765005, "SUBCLAVIANA", true, false, false),
  SUBCLAVIAN_VEIN(SCT, 9454009, "SUBCLAVIANV", true, false, false),
  SUBCOSTAL(SCT, 19695001, "SUBCOSTAL", true, false, false),
  SUBMANDIBULAR_AREA(SCT, 5713008, "", true, false, false),
  SUBMANDIBULAR_GLAND(SCT, 54019009, "SUBMANDIBULAR", true, true, false),
  SUBMENTAL(SCT, 170887008, "", false, false, false),
  SUBXIPHOID(SCT, 5076001, "", false, false, false),
  SUPERFICIAL_FEMORAL_ARTERY(SCT, 181349008, "SFA", true, false, false),
  SUPERFICIAL_FEMORAL_VEIN(SCT, 397364003, "SFV", true, false, false),
  SUPERFICIAL_TEMPORAL_ARTERY(SCT, 15672000, "", true, false, false),
  SUPERIOR_LEFT_PULMONARY_VEIN(SCT, 43863001, "LSUPPULMONARYV", false, false, false),
  SUPERIOR_MESENTERIC_ARTERY(SCT, 42258001, "SMA", false, false, false),
  SUPERIOR_RIGHT_PULMONARY_VEIN(SCT, 8629005, "RSUPPULMONARYV", false, false, false),
  SUPERIOR_THYROID_ARTERY(SCT, 72021004, "SUPTHYROIDA", true, false, false),
  SUPERIOR_VENA_CAVA(SCT, 48345005, "SVC", false, false, false),
  SUPRACLAVICULAR_REGION_OF_NECK(SCT, 77621008, "SUPRACLAVICULAR", true, false, false),
  SUPRASTERNAL_NOTCH(SCT, 26493002, "", false, false, false),
  SYSTEMIC_COLLATERAL_ARTERY_TO_LUNG(SCT, 128589000, "", false, false, false),
  SYSTEMIC_VENOUS_ATRIUM(SCT, 128568009, "", false, false, false),
  TARSAL_JOINT(SCT, 27949001, "", true, true, false),
  TEMPOROMANDIBULAR_JOINT(SCT, 53620006, "TMJ", true, true, false),
  TESTIS(SCT, 40689003, "TESTIS", true, false, false),
  THALAMUS(SCT, 42695009, "THALAMUS", true, false, false),
  THIGH(SCT, 68367000, "THIGH", true, true, false),
  THIRD_VENTRICLE(SCT, 49841001, "3RDVENTRICLE", false, false, false),
  THORACIC_AORTA(SCT, 113262008, "THORACICAORTA", false, false, false),
  THORACIC_SPINE(SCT, 122495006, "TSPINE", false, true, false),
  THORACO_LUMBAR_SPINE(SCT, 1217256009, "TLSPINE", false, true, false),
  THORAX(SCT, 43799004, "THORAX", false, false, true),
  THUMB(SCT, 76505004, "THUMB", true, true, false),
  THYMUS(SCT, 9875009, "THYMUS", false, false, false),
  THYROID(SCT, 69748006, "THYROID", false, false, false),
  TIBIA(SCT, 12611008, "TIBIA", true, false, false),
  TIBIA_AND_FIBULA(SCT, 110536004, "TIBIAFIBULA", true, false, false),
  TOE(SCT, 29707007, "TOE", true, true, false),
  TONGUE(SCT, 21974007, "TONGUE", false, false, false),
  TRACHEA(SCT, 44567001, "TRACHEA", false, true, false),
  TRACHEA_AND_BRONCHUS(SCT, 110726009, "TRACHEABRONCHUS", false, false, true),
  TRANSVERSE_COLON(SCT, 485005, "TRANSVERSECOLON", false, false, false),
  TRUNCUS_ARTERIOSUS_COMMUNIS(SCT, 61959006, "", false, false, false),
  ULNA(SCT, 23416004, "ULNA", true, false, false),
  ULNAR_ARTERY(SCT, 44984001, "ULNARA", true, false, false),
  UMBILICAL_ARTERY(SCT, 50536004, "UMBILICALA", false, false, false),
  UMBILICAL_REGION(SCT, 90290004, "UMBILICAL", false, false, false),
  UMBILICAL_VEIN(SCT, 284639000, "UMBILICALV", false, false, false),
  UPPER_ARM(SCT, 40983000, "UPPERARM", true, true, false),
  UPPER_INNER_QUADRANT_OF_BREAST(SCT, 77831004, "", true, false, false),
  UPPER_OUTER_QUADRANT_OF_BREAST(SCT, 76365002, "", true, false, false),
  UPPER_URINARY_TRACT(SCT, 431491007, "UPRURINARYTRACT", false, true, true),
  URETER(SCT, 87953007, "URETER", false, true, true),
  URETHRA(SCT, 13648007, "URETHRA", false, true, false),
  UTERUS(SCT, 35039007, "UTERUS", false, false, false),
  UTERUS_AND_FALLOPIAN_TUBES(SCT, 110639002, "", false, true, true),
  VAGINA(SCT, 76784001, "VAGINA", false, false, false),
  VASCULAR_GRAFT(SCT, 118375008, "", false, false, false),
  VEIN(SCT, 29092000, "VEIN", false, false, false),
  VENOUS_NETWORK(SCT, 34340008, "", false, false, false),
  VENTRICLE(SCT, 21814001, "", true, false, false),
  VERTEBRAL_ARTERY(SCT, 85234005, "VERTEBRALA", true, false, false),
  VERTEBRAL_COLUMN_AND_CRANIUM(SCT, 110517009, "", false, true, false),
  VULVA(SCT, 45292006, "VULVA", false, false, false),
  WRIST_JOINT(SCT, 74670003, "WRIST", true, true, false),
  ZYGOMA(SCT, 13881006, "ZYGOMA", true, true, false);

  private static final Map<String, BodyPart> CODE_LOOKUP =
      Stream.of(values())
          .collect(Collectors.toUnmodifiableMap(BodyPart::getCodeValue, Function.identity()));
  private final CodingScheme scheme;
  private final String codeValue;
  private final String legacyCode;
  private final boolean paired;
  private final boolean common;
  private final boolean endoscopic;

  BodyPart(
      CodingScheme scheme,
      int codeValue,
      String legacyCode,
      boolean paired,
      boolean common,
      boolean endoscopic) {
    this.scheme = scheme;
    this.codeValue = String.valueOf(codeValue);
    this.legacyCode = legacyCode;
    this.paired = paired;
    this.common = common;
    this.endoscopic = endoscopic;
  }

  @Override
  public String getCodeValue() {
    return codeValue;
  }

  @Override
  public String getCodeMeaning() {
    return MesBody.getString(codeValue);
  }

  /**
   * Returns the human-readable meaning of this body part in the specified locale.
   *
   * @param locale the desired locale
   * @return the localized code meaning
   */
  public String getCodeMeaning(Locale locale) {
    return MesBody.getString(codeValue, locale);
  }

  @Override
  public CodingScheme getCodingScheme() {
    return scheme;
  }

  @Override
  public String getLegacyCode() {
    return legacyCode;
  }

  @Override
  public boolean isPaired() {
    return paired;
  }

  /**
   * Indicates whether this body part is commonly used in general medical imaging workflows.
   *
   * @return {@code true} if this is a commonly imaged body part
   */
  public boolean isCommon() {
    return common;
  }

  /**
   * Indicates whether this body part is suitable for endoscopic imaging procedures.
   *
   * @return {@code true} if this body part can be examined endoscopically
   */
  public boolean isEndoscopic() {
    return endoscopic;
  }

  @Override
  public String toString() {
    return getCodeMeaning();
  }

  /**
   * Finds a body part by its SNOMED CT code value.
   *
   * @param code the SNOMED CT code value to look up
   * @return the corresponding {@code BodyPart}, or {@code null} if not found
   */
  public static BodyPart fromCode(String code) {
    return CODE_LOOKUP.get(code);
  }

  /**
   * @deprecated Use {@link #fromCode(String)} instead for better performance and clarity.
   */
  @Deprecated(since = "5.34.0.3", forRemoval = true)
  public static BodyPart getBodyPartFromCode(String code) {
    return fromCode(code);
  }
}
