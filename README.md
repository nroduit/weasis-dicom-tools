weasis-dicom-tools
=====================

This project provides a DICOM API for [C-Echo](src/main/java/org/weasis/dicom/op/Echo.java), [C-Move](src/main/java/org/weasis/dicom/op/CMove.java), [C-Get](src/main/java/org/weasis/dicom/op/CGet.java), [C-Find](src/main/java/org/weasis/dicom/op/CFind.java) and [C-Store](src/main/java/org/weasis/dicom/op/CStore.java) based on dcm4che3. The implementation allows to follow the progression of an DICOM operation like C-Move and gives its status.

This project replaces [weasis-dicom-operations](https://github.com/nroduit/weasis-dicom-operations) and now this library is used by recent versions of [weasis-pacs-connector](https://github.com/nroduit/weasis-pacs-connector) and in the weasis-dicom-codec module of [Weasis](https://github.com/nroduit/Weasis).