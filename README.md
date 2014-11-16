weasis-dicom-tools
=====================

This project provides a DICOM API for [C-Echo](https://github.com/nroduit/weasis-dicom-tools/blob/master/src/main/java/org/weasis/dicom/op/Echo.java), [C-Move](https://github.com/nroduit/weasis-dicom-tools/blob/master/src/main/java/org/weasis/dicom/op/CMove.java), C-Get, [C-Find](https://github.com/nroduit/weasis-dicom-tools/blob/master/src/main/java/org/weasis/dicom/op/CFind.java) and [C-Store](https://github.com/nroduit/weasis-dicom-tools/blob/master/src/main/java/org/weasis/dicom/op/CStore.java) based on dcm4che3. The implementation allows to follow the progression of an DICOM operation and gives its status.

This project replaces [weasis-dicom-operations](https://github.com/nroduit/weasis-dicom-operations) and now this library is used by recent versions of [weasis-pacs-connector](https://github.com/nroduit/weasis-pacs-connector) and in the weasis-dicom-codec module of [Weasis](https://github.com/nroduit/Weasis).