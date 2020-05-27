package com.ibm.fhir.usdf;

import com.ibm.fhir.model.resource.StructureDefinition;
import com.ibm.fhir.model.type.BackboneElement;
import com.ibm.fhir.model.type.ElementDefinition;
import com.ibm.fhir.openapi.generator.FHIROpenApiGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class USDFUtils {
    public static void loadExtensions(Map<Class<?>, StructureDefinition> objectMap,  String type) {
        String extensionsDir = USDFConstants.EXTENSIONS_PATH;
//        log.info("*************** Load Extension: "+extensionsDir);
        System.out.println("*************** Load Extension: " + extensionsDir);
        try (Stream<Path> walk = Files.walk(Paths.get(extensionsDir))) {
            List<String> result = walk.map(x -> x.toString()).filter(f -> f.startsWith(
                    extensionsDir + "/" + type)).collect(Collectors.toList());

            result.forEach(f -> {
//                objectMap.putAll(CodeGenerator.buildResourceMap(f, type));
                try {
                    //element in the index 4 has the class name (u)
                    FHIROpenApiGenerator.populateStructureDefinitionMap(objectMap,f.split("/")[9]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            System.out.println("Loading Completed");

        } catch (IOException e) {
//            log.error("Failed to load extensions from "+ extensionsDir, e);
            System.out.println("Failed to load extensions from " + extensionsDir + e);
        }
    }

    public static ElementDefinition getElementDefinitionUSDF (StructureDefinition structureDefinition, Class<?> modelClass,
                                                          String elementName){
        String structureDefinitionName = structureDefinition.getName().getValue();
        String path = structureDefinitionName;

        String pathEnding = elementName;
        if (BackboneElement.class.isAssignableFrom(modelClass) && !BackboneElement.class.equals(modelClass) && modelClass.isMemberClass()) {
            String modelClassName = modelClass.getSimpleName();
            modelClassName = modelClassName.substring(0, 1).toLowerCase() + modelClassName.substring(1);

            if (Character.isDigit(modelClassName.charAt(modelClassName.length() - 1))) {
                modelClassName = modelClassName.substring(0, modelClassName.length() - 1);
            }

            path += "." + modelClassName;
            pathEnding = modelClassName + "." + elementName;
        }

        if (structureDefinitionName.equals(USDFConstants.USDF_CLASSNAMES.CoveragePlan.toString())||
                structureDefinitionName.equals(USDFConstants.USDF_CLASSNAMES.FormularyDrug.toString())){
            path = structureDefinition.getType().getValue()+"."+elementName;
            for (ElementDefinition elementDefinition : structureDefinition.getSnapshot().getElement()) {
                String elementDefinitionPath = elementDefinition.getPath().getValue();
                if (elementDefinitionPath.equals(path) || (elementDefinitionPath.startsWith(structureDefinitionName)
                        && elementDefinitionPath.endsWith(pathEnding))) {
                    return elementDefinition;
                }
            }
        }
        if (modelClass.getName().equals(USDFConstants.NESTED_ELEMENT_CLASSNAME_COVERAGEPLAN_ENTRY)||
                modelClass.getName().startsWith(USDFConstants.NESTED_ELEMENT_CLASSNAME_FORMULARYDRUG_COMMON) ||
                modelClass.getName().startsWith(USDFConstants.NESTED_ELEMENT_CLASSNAME_COVERAGEPLAN_COMMON)
        ){
            path = structureDefinition.getType().getValue()+"."+elementName;
            for (ElementDefinition elementDefinition : structureDefinition.getSnapshot().getElement()) {
                String elementDefinitionPath = elementDefinition.getPath().getValue();
                if (elementDefinitionPath.equals(path) || (elementDefinitionPath.startsWith(
                        structureDefinition.getType().getValue())
                        && elementDefinitionPath.endsWith(pathEnding))) {
                    return elementDefinition;
                } else if (elementDefinition.getPath().getValue().equals(
                        USDFConstants.ELEMENT_SPECIAL_MEDKNOWLEDGE_VALUE)){
                    return elementDefinition;
                }
            }
        }
        throw new RuntimeException("Unable to retrieve element definition for " + elementName + " in " + modelClass.getName());
    }

    public static boolean isUSDFModelClass(Class<?> modelClass){
        if (modelClass.getSimpleName().equals(USDFConstants.USDF_CLASSNAMES.CoveragePlan.toString())||
                modelClass.getSimpleName().equals(USDFConstants.USDF_CLASSNAMES.FormularyDrug.toString())){
            //examples are skipped
            return true;
        }
        return false;
    }





}
