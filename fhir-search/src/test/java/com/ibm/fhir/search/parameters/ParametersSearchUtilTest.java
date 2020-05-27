/*
 * (C) Copyright IBM Corp. 2019
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.search.parameters;

import static org.testng.Assert.assertFalse;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ibm.fhir.config.FHIRConfiguration;
import com.ibm.fhir.config.FHIRRequestContext;
import com.ibm.fhir.model.resource.Observation;
import com.ibm.fhir.model.resource.SearchParameter;
import com.ibm.fhir.search.test.BaseSearchTest;
import com.ibm.fhir.search.util.SearchUtil;

/**
 * Tests the ParametersUtil through the SearchUtil.
 * 
 * @author padams
 * @author pbastide
 *
 */
public class ParametersSearchUtilTest extends BaseSearchTest {

    @BeforeClass
    public void setup() {
        FHIRConfiguration.setConfigHome("src/test/resources");
    }

    @Test
    public void testGetSearchParameters1Default() throws Exception {
        // Simple test looking only for built-in search parameters for Observation.class.
        // Use default tenant id ("default") which has no Observation tenant-specific
        // search parameters.
        List<SearchParameter> result = SearchUtil.getSearchParameters(Observation.class);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        printSearchParameters("testGetSearchParameters1", result);

        if (DEBUG) {
            ParametersUtil.print(System.out);
        }

        assertEquals(44, result.size());
    }

    @Test
    public void testGetSearchParameters2Default() throws Exception {
        // Looking for built-in and tenant-specific search parameters for "Patient"
        // and "Observation". Use the default tenant since it has some Patient search
        // parameters defined.
        FHIRRequestContext.set(new FHIRRequestContext("default"));

        List<SearchParameter> result = SearchUtil.getSearchParameters("Patient");
        assertNotNull(result);
        printSearchParameters("testGetSearchParameters2/Patient", result);
        assertEquals(35, result.size());

        result = SearchUtil.getSearchParameters("Observation");
        assertNotNull(result);
        printSearchParameters("testGetSearchParameters2/Observation", result);
        assertEquals(44, result.size());
    }

    @Test
    public void testGetSearchParameters3Tenant() throws Exception {
        // Looking for built-in and tenant-specific search parameters for "Observation".

        // Use tenant1 since it has some Patient search parameters defined.
        FHIRRequestContext.set(new FHIRRequestContext("tenant1"));

        // tenant1's filtering includes only 1 search parameter for Observation.
        List<SearchParameter> result = SearchUtil.getSearchParameters("Observation");
        assertNotNull(result);
        printSearchParameters("testGetSearchParameters3/Observation", result);

        // Simple conversion and output.
        if (DEBUG) {
            System.out.println("As Follows: ");
            System.out.println(result.stream().map(in -> in.getCode().getValue()).collect(Collectors.toList()));
        }
        assertEquals(2, result.size());
        SearchParameter sp = result.get(0);
        assertNotNull(sp);
        assertEquals("code", sp.getCode().getValue());

        sp = result.get(1);
        assertNotNull(sp);
        assertEquals("value-range", sp.getCode().getValue());

        result = SearchUtil.getSearchParameters("Immunization");
        assertNotNull(result);
        printSearchParameters("testGetSearchParameters3/Immunization", result);
        assertEquals(22, result.size());
    }

    @Test
    public void testGetSearchParameters4Tenant() throws Exception {
        // Test filtering of search parameters for Device (tenant1).
        FHIRRequestContext.set(new FHIRRequestContext("tenant1"));

        List<SearchParameter> result = SearchUtil.getSearchParameters("Device");
        assertNotNull(result);
        printSearchParameters("testGetSearchParameters4/Device", result);
        assertEquals(2, result.size());
        List<String> codes = getSearchParameterCodes(result);
        assertTrue(codes.contains("patient"));
        assertTrue(codes.contains("organization"));
    }

    @Test
    public void testGetSearchParameters5Tenant() throws Exception {
        // Test filtering of search parameters for Patient (tenant1).
        FHIRRequestContext.set(new FHIRRequestContext("tenant1"));

        List<SearchParameter> result = SearchUtil.getSearchParameters("Patient");
        assertNotNull(result);
        printSearchParameters("testGetSearchParameters5/Patient", result);
        assertEquals(4, result.size());
        List<String> codes = getSearchParameterCodes(result);
        assertTrue(codes.contains("active"));
        assertTrue(codes.contains("address"));
        assertTrue(codes.contains("birthdate"));
        assertTrue(codes.contains("name"));

        // Make sure we get all of the MedicationAdministration search parameters.
        // (No filtering configured for these)
        result = SearchUtil.getSearchParameters("MedicationAdministration");
        assertNotNull(result);
        printSearchParameters("testGetSearchParameters5/MedicationAdministration", result);
        assertEquals(19, result.size());
    }

    @Test
    public void testGetSearchParameters6Default() throws Exception {
        // Test filtering of search parameters for Patient (default tenant).
        FHIRRequestContext.set(new FHIRRequestContext("default"));

        List<SearchParameter> result = SearchUtil.getSearchParameters("Patient");
        assertNotNull(result);
        printSearchParameters("testGetSearchParameters6/Patient", result);
        assertEquals(35, result.size());

        result = SearchUtil.getSearchParameters("Device");
        assertNotNull(result);
        printSearchParameters("testGetSearchParameters6/Device", result);
        assertEquals(18, result.size());
    }
}
