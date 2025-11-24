/**
 * 
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 - 2025 by Andrew D. King
 */

package programmingtheiot.integration.connection;

import static org.junit.Assert.*;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.gda.connection.MqttClientConnector;

/**
 * Performance test for MQTT Client Connector.
 * Tests publishing performance with different QoS levels.
 * 
 * PIOT-INT-10-001: Test the performance of MQTT using all three QoS levels
 * 
 * IMPORTANT NOTE: This test expects MqttClientConnector to be
 * configured using the synchronous MqttClient.
 */
public class MqttClientPerformanceTest {
	// static

	private static final Logger _Logger = Logger.getLogger(MqttClientPerformanceTest.class.getName());

	public static final int MAX_TEST_RUNS = 10000;

	// member var's

	private MqttClientConnector mqttClient = null;

	// test setup methods

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		_Logger.info("\n\n***** GDA MQTT Client Performance Tests - PIOT-INT-10-001 *****\n");
		ConfigUtil.getInstance();
		this.mqttClient = new MqttClientConnector();
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	// test methods

	/**
	 * Test method for
	 * {@link programmingtheiot.gda.connection.MqttClientConnector#connectClient()}.
	 * 
	 * Test 1: Measure connection and disconnection time
	 */
	@Test
	public void testConnectAndDisconnect() {
		_Logger.info("\n----- Test: Connect and Disconnect -----");

		long startMillis = System.currentTimeMillis();

		assertTrue(this.mqttClient.connectClient());

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// ignore
		}

		assertTrue(this.mqttClient.disconnectClient());

		long endMillis = System.currentTimeMillis();
		long elapsedMillis = endMillis - startMillis;

		_Logger.info("Connect and Disconnect: " + elapsedMillis + " ms\n");
	}

	/**
	 * Test method for
	 * {@link programmingtheiot.gda.connection.MqttClientConnector#publishMessage(programmingtheiot.common.ResourceNameEnum, java.lang.String, int)}.
	 * 
	 * Test 2: Measure publishing performance with QoS 0 (At most once)
	 */
	@Test
	public void testPublishQoS0() {
		_Logger.info("\n----- Test: Publish with QoS 0 -----");
		execTestPublish(MAX_TEST_RUNS, 0);
	}

	/**
	 * Test method for
	 * {@link programmingtheiot.gda.connection.MqttClientConnector#publishMessage(programmingtheiot.common.ResourceNameEnum, java.lang.String, int)}.
	 * 
	 * Test 3: Measure publishing performance with QoS 1 (At least once)
	 */
	@Test
	public void testPublishQoS1() {
		_Logger.info("\n----- Test: Publish with QoS 1 -----");
		execTestPublish(MAX_TEST_RUNS, 1);
	}

	/**
	 * Test method for
	 * {@link programmingtheiot.gda.connection.MqttClientConnector#publishMessage(programmingtheiot.common.ResourceNameEnum, java.lang.String, int)}.
	 * 
	 * Test 4: Measure publishing performance with QoS 2 (Exactly once)
	 */
	@Test
	public void testPublishQoS2() {
		_Logger.info("\n----- Test: Publish with QoS 2 -----");
		execTestPublish(MAX_TEST_RUNS, 2);
	}

	// private methods

	/**
	 * Execute the publish performance test for a given QoS level.
	 * 
	 * @param maxTestRuns Number of messages to publish
	 * @param qos         Quality of Service level (0, 1, or 2)
	 */
	private void execTestPublish(int maxTestRuns, int qos) {
		assertTrue(this.mqttClient.connectClient());

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// ignore
		}

		SensorData sensorData = new SensorData();
		String payload = DataUtil.getInstance().sensorDataToJson(sensorData);
		int payloadLen = payload.length();

		_Logger.info("Starting publish test: QoS=" + qos + ", Messages=" + maxTestRuns + ", Payload Size=" + payloadLen
				+ " bytes");

		long startMillis = System.currentTimeMillis();

		for (int sequenceNo = 1; sequenceNo <= maxTestRuns; sequenceNo++) {
			this.mqttClient.publishMessage(ResourceNameEnum.CDA_MGMT_STATUS_CMD_RESOURCE, payload, qos);
		}

		long endMillis = System.currentTimeMillis();
		long elapsedMillis = endMillis - startMillis;

		assertTrue(this.mqttClient.disconnectClient());

		_Logger.info("PERFORMANCE TEST RESULTS - QoS " + qos);
		_Logger.info("Total Messages:    " + maxTestRuns);
		_Logger.info("Payload Size:      " + payloadLen + " bytes");
		_Logger.info("Total Time:        " + elapsedMillis + " ms");
		_Logger.info("Average per msg:   " + String.format("%.4f", (float) elapsedMillis / maxTestRuns) + " ms");
		_Logger.info("Messages per sec:  " + String.format("%.2f", (maxTestRuns / (float) elapsedMillis) * 1000));
	}
}