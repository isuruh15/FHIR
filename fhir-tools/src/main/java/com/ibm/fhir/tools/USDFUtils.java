package com.ibm.fhir.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.json.JsonObject;

import static com.ibm.fhir.tools.USDFConstants.METHODTOSKIPXHTML;

/**
 *
 */
public class USDFUtils {

    /**
     *
     * @param classname
     * @return
     */
    public static boolean isUSDFResource(String classname) {
        for (USDFConstants.UsdfClass superClass : USDFConstants.UsdfClass.values()) {
            if (classname.equals(superClass.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param classname
     * @return
     */
    public static boolean isUSDFSuperClass(String classname) {
        for (USDFConstants.UsdfSuperClass superClass : USDFConstants.UsdfSuperClass.values()) {
            if (classname.equals(superClass.toString())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUSDFSuperType(String classname) {
        for (USDFConstants.UsdfSuperType superClass : USDFConstants.UsdfSuperType.values()) {
            if (classname.equals(superClass.toString())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSkipBuilderMethodsForUSDF(String classname) {
        return classname.equals("Extension.Builder") || classname.equals("com.ibm.fhir.model.resource.List.Builder") ||
                classname.equals("MedicationKnowledge.Builder");
    }

    public static void loadExtensions(Map<String, JsonObject> objectMap, String extensionsDir, String type) {
//        log.info("*************** Load Extension: "+extensionsDir);
        System.out.println("*************** Load Extension: " + extensionsDir);
        try (Stream<Path> walk = Files.walk(Paths.get(extensionsDir))) {
//            log.info("*************** innnnn");
            System.out.println("*************** innnnn");
            List<String> result = walk.map(x -> x.toString()).filter(f ->
                    f.contains("/extensions/" + type)).collect(Collectors.toList());

            result.forEach(f -> {
                objectMap.putAll(CodeGenerator.buildResourceMap(f, type));
            });
            System.out.println("Done");

        } catch (IOException e) {
//            log.error("Failed to load extensions from "+ extensionsDir, e);
            System.out.println("Failed to load extensions from " + extensionsDir + e);
        }
    }

    public static boolean isSkipNarrative(String fieldName) {
        return fieldName.equals(METHODTOSKIPXHTML) ? true : false;
    }








}
