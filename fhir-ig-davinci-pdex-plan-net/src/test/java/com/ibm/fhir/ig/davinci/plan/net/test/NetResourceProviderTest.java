/*
 * (C) Copyright IBM Corp. 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.ig.davinci.plan.net.test;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ibm.fhir.ig.davinci.pdex.plan.net.PlanNetResourceProvider;
import com.ibm.fhir.registry.spi.FHIRRegistryResourceProvider;

public class NetResourceProviderTest {
    @Test
    public void testBBResourceProvider() {
        FHIRRegistryResourceProvider provider = new PlanNetResourceProvider();
        Assert.assertEquals(provider.getRegistryResources().size(), 145);
    }
}
