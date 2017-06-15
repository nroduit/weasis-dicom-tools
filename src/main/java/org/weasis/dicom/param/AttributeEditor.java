package org.weasis.dicom.param;

import org.dcm4che3.data.Attributes;

@FunctionalInterface
public interface AttributeEditor {

    void apply(Attributes attributes);

}
