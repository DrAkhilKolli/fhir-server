/*
 * (C) Copyright IBM Corp. 2019, 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.linuxforhealth.fhir.model.plugin.generator.impl;

import java.io.File;
import java.util.Map;

import jakarta.json.JsonObject;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import org.linuxforhealth.fhir.model.plugin.generator.ModelGenerator;
import org.linuxforhealth.fhir.tools.CodeGenerator;

/**
 * fhir-model generator for FHIR R5 (5.0.0)
 */
public class R5ModelGeneratorImpl implements ModelGenerator {

    private String baseDirectory;
    private boolean limit = true;

    @Override
    public boolean useTargetProjectBaseDirectory() {
        return false;
    }

    @Override
    public void setTargetProjectBaseDirectory(String baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public void setLimit(boolean limit) {
        this.limit = limit;
    }

    @Override
    public void process(MavenProject mavenProject, Log log) {
        String targetDir = baseDirectory + "/src/main/java";
        String targetBaseDirectory = baseDirectory;
        baseDirectory = baseDirectory.replace("fhir-model", "fhir-tools");

        String definitionsDir = baseDirectory + "/definitions";
        File r5DefinitionsDir = new File(definitionsDir + "/R5");

        if (mavenProject.getArtifactId().contains("fhir-model") || !limit) {
            if (r5DefinitionsDir.exists()) {

                log.info("Setting the base dir for definitions -> " + baseDirectory);
                log.info("Setting the Target Directory -> " + targetDir);
                System.setProperty("BaseDir", baseDirectory);
                System.setProperty("TargetBaseDir", targetBaseDirectory);

                Map<String, JsonObject> structureDefinitionMap =
                        CodeGenerator.buildResourceMap(definitionsDir + "/R5/profiles-resources.json", "StructureDefinition");
                structureDefinitionMap.putAll(CodeGenerator.buildResourceMap(definitionsDir + "/R5/profiles-types.json", "StructureDefinition"));

                Map<String, JsonObject> codeSystemMap = CodeGenerator.buildResourceMap(definitionsDir + "/R5/valuesets.json", "CodeSystem");

                // Supplement with external terminology code systems and value sets not included in the R5 definitions bundle
                String supplementalPath = definitionsDir + "/R5/supplemental-codesystems.json";
                if (new File(supplementalPath).exists()) {
                    codeSystemMap.putAll(CodeGenerator.buildResourceMap(supplementalPath, "CodeSystem"));
                }

                Map<String, JsonObject> valueSetMap = CodeGenerator.buildResourceMap(definitionsDir + "/R5/valuesets.json", "ValueSet");
                if (new File(supplementalPath).exists()) {
                    valueSetMap.putAll(CodeGenerator.buildResourceMap(supplementalPath, "ValueSet"));
                }
                String expansionsPath = definitionsDir + "/R5/expansions.json";
                if (new File(expansionsPath).exists()) {
                    valueSetMap.putAll(CodeGenerator.buildResourceMap(expansionsPath, "ValueSet"));
                }

                log.info("[Started] generating the code for fhir-model");
                CodeGenerator generator = new CodeGenerator(structureDefinitionMap, codeSystemMap, valueSetMap);
                generator.generate(targetDir);
                log.info("[Finished] generating the code for fhir-model");

            } else {
                log.info("Skipping as the Definitions don't exist in this project " + baseDirectory);
            }
        } else {
            log.info("Skipping project as the artifact is not a model project -> " + mavenProject.getArtifactId());
        }
    }
}
