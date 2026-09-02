# Anatomic regions

Concepts of the `org.weasis.dicom.ref` package: how an image's anatomy is represented, resolved
from DICOM attributes and matched.

## Canonical region

`AnatomicRegion` is the anatomy of an image: a coded region (`AnatomicItem`), its context group
(`CategoryBuilder`) and its modifiers. The region item is one of:

| Item | Content |
|---|---|
| `BodyPart` | regions of CID 4 / 4031 / 4040, with their Body Part Examined term (PS3.16 Table L-1) when one exists |
| `SurfacePart` | surface regions of CID 4029 |
| `OtherPart` | any other coded region, kept with its code and meaning |
| `BodyPartTerm` | a Body Part Examined value that names no region: shown and written back, but without code |

The coded region is the value consumers work on; Body Part Examined (0018,0015) is only an input
alias and a compatibility output.

## Reading and writing

`AnatomicRegion.read` takes the Anatomic Region Sequence first, then Body Part Examined through
PS3.16 Table L-1:

- several Body Part Examined terms may name one region (`THORAX` and `CHEST` are the chest);
- retired SNOMED-RT identifiers (`SRT`, e.g. `T-28000`) of files coded before SNOMED CT concept
  ids are mapped to their region;
- a lower-case Body Part Examined value is accepted, a deviation from the CS value representation.

`AnatomicRegion.write` writes the Anatomic Region Sequence and, when the region has a term, Body
Part Examined; a `BodyPartTerm` is written as Body Part Examined only.

## Region groups

Neither the codes nor the terms say that the liver is in the abdomen. `RegionGroup` names the
coarse area imaged, used as a grouper of related images. The concept and its values are those of
*Region imaged* in the LOINC/RSNA Radiology Playbook (DICOM CID 102):

| Group | Playbook region | SNOMED CT (PS3.16 Table L-1) |
|---|---|---|
| `HEAD` | head | 69536005 Head |
| `NECK` | neck | 45048000 Neck |
| `CHEST` | chest | 816094009 Chest |
| `BREAST` | breast | 76752008 Breast |
| `ABDOMEN` | abdomen | 818981001 Abdomen |
| `PELVIS` | pelvis | 816092008 Pelvis |
| `EXTREMITY` | extremity | 66019005 Extremity |
| `UPPER_EXTREMITY` | upper extremity | 53120007 Upper limb |
| `LOWER_EXTREMITY` | lower extremity | 61685007 Lower limb |
| `WHOLE_BODY` | whole body | 38266002 Entire body |
| `SPINE` | — (Weasis extension) | 421060004 Spine |

- The SNOMED CT concept identifies the group wherever a code is needed (Anatomic Region Sequence,
  FHIR `ImagingStudy.series.bodySite`, which is bound to SNOMED CT body structures through the same
  Annex L).
- A region belongs to every area it covers, as the Playbook assigns several regions to an exam
  spanning them: chest, abdomen and pelvis → three groups.
- The Playbook has no spine region: spine segments lie in the trunk regions they cross (lumbar
  spine → abdomen and pelvis) and also in `SPINE`.
- `WHOLE_BODY`: an image of the whole body lies in every group. `EXTREMITY`: a limb, whichever it
  is; it includes both limb groups.
- Surface regions (CID 4029: skin, mucosa, nails) belong to the area they cover.
- Generic structures (artery, vein, joint, phantom, skin, hair…) and terms belong to no group, the
  Playbook's *unspecified*.

Group names are localized by `RegionGroup.getLabel` (`group.properties`, en and fr).

`RegionGroups` holds the membership of region codes in the groups, following the SNOMED CT "part
of" relations. It is a reviewed Weasis table, not a SNOMED CT extract: `regionGroups.json`, whose
entries are `BodyPart` / `SurfacePart` names or `SCHEME:code`; a site document can be merged on top
of the built-in one and installed with `RegionGroups.setDefault`. `AnatomicRegion.getGroups` and
`isIn` answer from the table in use.

## Anatomy notation

`AnatomySelector.parse` reads the anatomy a person or a script names, and resolves it once:

| Token | Selects |
|---|---|
| a group name (`CHEST`) | every region of the group |
| a coded value (`SCT:816094009`, `SRT:T-28000`) | that region |
| a Body Part Examined term (`THORAX`) | the region of Table L-1 |

A bare word is a group first, then a term; the group contains the region the same term names.

## Hanging protocols

`HangingProtocol.appliesTo(image)` tells whether a protocol is intended for a study image: one item
of its Hanging Protocol Definition Sequence must apply (PS3.3 C.23.1). In an item, each criterion
present must hold:

- Modality equal to the image modality;
- one Anatomic Region Sequence code selecting the image anatomy: the same code (retired `SRT`
  identifiers resolved), or the code of a region group area covering it — a protocol for the chest
  applies to a lung image, a leniency beyond exact Sequence Matching;
- Laterality equal to the image laterality (Image Laterality, else Laterality); `U` applies to an
  image without laterality, an empty value to any image.

The image anatomy comes from `AnatomicRegion.read`, so Body Part Examined is used when the image
has no Anatomic Region Sequence. Procedure and reason codes are not evaluated.
