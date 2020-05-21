package com.ibm.fhir.usdf;

public class USDFConstants {
    public static final String EXTENSIONS_PATH = "src/main/resources/extensions";
    //add usdf classNames below
    public static final String COVERAGEPLAN = "CoveragePlan";
    public static final String FORMULARYDRUG = "FormularyDrug";

    //additional constants
    public static final String NESTED_ELEMENT_CLASSNAME_COVERAGEPLAN_ENTRY = "com.ibm.fhir.model.resource.CoveragePlan$Entry";
    public static final String NESTED_ELEMENT_CLASSNAME_FORMULARYDRUG_COMMON = "com.ibm.fhir.model.resource.FormularyDrug$";
    public static final String ELEMENT_SPECIAL_MEDKNOWLEDGE_VALUE = "MedicationKnowledge.drugCharacteristic.value[x]";

    //    public static final String COVERAGEPLAN = "CoveragePlan";
//    public static final String COVERAGEPLAN = "CoveragePlan";

}
