/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 - 2025 by Andrew D. King
 */

package programmingtheiot.integration.app;

import static org.junit.Assert.*;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import programmingtheiot.gda.app.DeviceDataManager;

/**
 * Integration test for DeviceDataManager.
 * 
 * Tests complete flow of:
 * - Starting/stopping manager
 * - MQTT connectivity
 * - Subscription to CDA topics
 * - Message handling
 * 
 * PIOT-GDA-10-001 & PIOT-GDA-10-002 Integration Test
 */
public class DeviceDataManagerIntegrationTest {
    // static

    private static final Logger _Logger = Logger.getLogger(DeviceDataManagerIntegrationTest.class.getName());

    // member var's

    private DeviceDataManager devDataMgr = null;

    // test setup methods

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        _Logger.info("\n\n***** Testing DeviceDataManager Integration *****\n");
        this.devDataMgr = new DeviceDataManager();
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }

    // test methods

    /**
     * Test DeviceDataManager startup and shutdown
     */
    @Test
    public void testStartStopManager() {
        _Logger.info("\n----- Test: Start and Stop DeviceDataManager -----");

        // Start manager
        this.devDataMgr.startManager();

        try {
            Thread.sleep(5000); // Run for 5 seconds
        } catch (InterruptedException e) {
            // ignore
        }

        // Stop manager
        this.devDataMgr.stopManager();

        _Logger.info("\n----- Test Complete -----\n");
    }

    /**
     * Test DeviceDataManager for 60 seconds
     */
    // @Test
    public void testTimedIntegration() {
        _Logger.info("\n----- Test: 60-Second Integration Test -----");

        // Start manager
        this.devDataMgr.startManager();

        _Logger.info("Running for 60 seconds...");

        try {
            Thread.sleep(60000); // Run for 60 seconds
        } catch (InterruptedException e) {
            // ignore
        }

        // Stop manager
        this.devDataMgr.stopManager();

        _Logger.info("\n----- Test Complete -----\n");
    }
}