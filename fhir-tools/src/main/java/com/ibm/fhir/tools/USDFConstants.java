package com.ibm.fhir.tools;

class USDFConstants {
    public enum UsdfSuperClass {
        List,
        MedicationKnowledge
    }

    public enum UsdfClass {
        CoveragePlan,
        FormularyDrug
    }

    public enum UsdfSuperType {
        Extension
    }

    public static final String FULLCLASSNAMELIST = "com.ibm.fhir.model.resource.List";
    public static final boolean ENABLEXML = false; //enable after adding support for usdf


}
