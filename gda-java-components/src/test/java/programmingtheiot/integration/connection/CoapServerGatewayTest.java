/**
 * 
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 - 2025 by Andrew D. King
 */

package programmingtheiot.integration.connection;

import java.util.Set;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.DefaultDataMessageListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.gda.connection.*;

/**
 * This test case class contains very basic integration tests for
 * CoapServerGateway. It should not be considered complete,
 * but serve as a starting point for the student implementing
 * additional functionality within their Programming the IoT
 * environment.
 * 
 * Tests cover:
 * - Server startup and shutdown
 * - Resource discovery
 * - GET requests to registered resources
 * - POST requests with sensor data
 * - PUT requests with actuator commands
 * - Bidirectional communication patterns
 */
public class CoapServerGatewayTest {
	// static

	public static final long DEFAULT_TIMEOUT = 5000L;
	public static final long DISCOVERY_TIMEOUT = 10000L;
	public static final boolean USE_DEFAULT_RESOURCES = true;

	private static final Logger _Logger = Logger.getLogger(CoapServerGatewayTest.class.getName());

	private static CoapServerGateway csg = null;
	private static String baseUrl = null;

	// member var's

	private IDataMessageListener dml = null;

	// test setup methods

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		_Logger.info("Setting up CoAP Server Gateway for testing...");

		// Build the base URL
		baseUrl = ConfigConst.DEFAULT_COAP_PROTOCOL + "://" +
				ConfigConst.DEFAULT_HOST + ":" +
				ConfigConst.DEFAULT_COAP_PORT;

		_Logger.info("Base CoAP URL: " + baseUrl);

		// Create and start the CoAP server
		csg = new CoapServerGateway(new DefaultDataMessageListener());
		csg.startServer();

		// Give the server time to fully start
		Thread.sleep(2000);

		_Logger.info("CoAP Server Gateway started successfully.");
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		_Logger.info("Tearing down CoAP Server Gateway...");

		if (csg != null) {
			csg.stopServer();
			Thread.sleep(1000);
		}

		_Logger.info("CoAP Server Gateway stopped successfully.");
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		// Create a new listener for each test
		this.dml = new DefaultDataMessageListener();
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
		// Cleanup after each test
		this.dml = null;
	}

	// test methods

	/**
	 * Test basic server startup, resource discovery, and simple GET requests.
	 */
	@Test
	public void testRunSimpleCoapServerGatewayIntegration() {
		_Logger.info("\n\n----- [TEST] Simple CoAP Server Gateway Integration -----");

		try {
			_Logger.info("Creating CoAP client connection to: " + baseUrl);
			CoapClient clientConn = new CoapClient(baseUrl);
			clientConn.setTimeout(DISCOVERY_TIMEOUT);

			// Discover available resources
			_Logger.info("Discovering available resources...");
			Set<WebLink> wlSet = clientConn.discover();

			if (wlSet != null && !wlSet.isEmpty()) {
				_Logger.info("Discovered " + wlSet.size() + " resources:");
				for (WebLink wl : wlSet) {
					_Logger.info(" --> WebLink: " + wl.getURI() + ". Attributes: " + wl.getAttributes());
				}
			} else {
				_Logger.warning("No resources discovered.");
			}

			// Test GET requests to various resource levels
			_Logger.info("\nTesting GET requests to various resource paths...");

			// Test 1: GET root product resource (PIOT)
			_Logger.info("Test 1: GET /" + ConfigConst.PRODUCT_NAME);
			clientConn.setURI(baseUrl + "/" + ConfigConst.PRODUCT_NAME);
			CoapResponse response1 = clientConn.get();
			if (response1 != null) {
				_Logger.info("Response Code: " + response1.getCode());
				_Logger.info("Response Text: " + response1.getResponseText());
			}

			// Test 2: GET device level resource (PIOT/GatewayDevice)
			_Logger.info("\nTest 2: GET /" + ConfigConst.PRODUCT_NAME + "/" + ConfigConst.GATEWAY_DEVICE);
			clientConn.setURI(baseUrl + "/" + ConfigConst.PRODUCT_NAME + "/" + ConfigConst.GATEWAY_DEVICE);
			CoapResponse response2 = clientConn.get();
			if (response2 != null) {
				_Logger.info("Response Code: " + response2.getCode());
				_Logger.info("Response Text: " + response2.getResponseText());
			}

			// Test 3: GET specific resource (PIOT/GatewayDevice/MgmtStatusMsg)
			_Logger.info("\nTest 3: GET /" + ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE.getResourceName());
			clientConn.setURI(baseUrl + "/" + ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE.getResourceName());
			CoapResponse response3 = clientConn.get();
			if (response3 != null) {
				_Logger.info("Response Code: " + response3.getCode());
				_Logger.info("Response Text: " + response3.getResponseText());
				assertEquals(ResponseCode.CONTENT, response3.getCode());
			}

			// Test 4: GET SystemPerfMsg resource
			_Logger.info("\nTest 4: GET /" + ResourceNameEnum.GDA_SYSTEM_PERF_MSG_RESOURCE.getResourceName());
			clientConn.setURI(baseUrl + "/" + ResourceNameEnum.GDA_SYSTEM_PERF_MSG_RESOURCE.getResourceName());
			CoapResponse response4 = clientConn.get();
			if (response4 != null) {
				_Logger.info("Response Code: " + response4.getCode());
				_Logger.info("Response Text: " + response4.getResponseText());
				assertEquals(ResponseCode.CONTENT, response4.getCode());
			}

			clientConn.shutdown();

			_Logger.info("\n----- [TEST COMPLETE] Simple CoAP Server Gateway Integration -----\n");

		} catch (Exception e) {
			_Logger.severe("Test failed with exception: " + e.getMessage());
			e.printStackTrace();
			fail("Test failed: " + e.getMessage());
		}
	}

	/**
	 * Test POST request with SystemPerformanceData to GDA resource.
	 */
	@Test
	public void testPostSystemPerformanceDataToGda() {
		_Logger.info("\n\n----- [TEST] POST SystemPerformanceData to GDA -----");

		try {
			// Create sample SystemPerformanceData
			SystemPerformanceData sysPerfData = new SystemPerformanceData();
			sysPerfData.setName("GDA System Performance");
			sysPerfData.setCpuUtilization(45.5f);
			sysPerfData.setMemoryUtilization(62.3f);
			sysPerfData.setDiskUtilization(78.1f);

			// Convert to JSON
			String jsonData = DataUtil.getInstance().systemPerformanceDataToJson(sysPerfData);
			_Logger.info("Sending SystemPerformanceData JSON:\n" + jsonData);

			// Create CoAP client
			String resourceUrl = baseUrl + "/" + ResourceNameEnum.GDA_SYSTEM_PERF_MSG_RESOURCE.getResourceName();
			_Logger.info("Posting to: " + resourceUrl);

			CoapClient clientConn = new CoapClient(resourceUrl);
			clientConn.setTimeout(DEFAULT_TIMEOUT);

			// Send POST request
			CoapResponse response = clientConn.post(jsonData, MediaTypeRegistry.APPLICATION_JSON);

			// Validate response
			assertNotNull("Response should not be null", response);
			_Logger.info("Response Code: " + response.getCode());

			assertEquals("Response should be CHANGED (2.04)", ResponseCode.CHANGED, response.getCode());

			clientConn.shutdown();

			_Logger.info("----- [TEST COMPLETE] POST SystemPerformanceData to GDA -----\n");

		} catch (Exception e) {
			_Logger.severe("Test failed with exception: " + e.getMessage());
			e.printStackTrace();
			fail("Test failed: " + e.getMessage());
		}
	}

	/**
	 * Test PUT request with SensorData to GDA resource.
	 */
	@Test
	public void testPutSensorDataToGda() {
		_Logger.info("\n\n----- [TEST] PUT SensorData to GDA -----");

		try {
			// Create sample SensorData
			SensorData sensorData = new SensorData();
			sensorData.setName("Temperature Sensor");
			sensorData.setTypeID(ConfigConst.TEMP_SENSOR_TYPE);
			sensorData.setValue(22.5f);

			// Convert to JSON
			String jsonData = DataUtil.getInstance().sensorDataToJson(sensorData);
			_Logger.info("Sending SensorData JSON:\n" + jsonData);

			// Create CoAP client
			String resourceUrl = baseUrl + "/" + ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE.getResourceName();
			_Logger.info("Putting to: " + resourceUrl);

			CoapClient clientConn = new CoapClient(resourceUrl);
			clientConn.setTimeout(DEFAULT_TIMEOUT);

			// Send PUT request
			CoapResponse response = clientConn.put(jsonData, MediaTypeRegistry.APPLICATION_JSON);

			// Validate response
			assertNotNull("Response should not be null", response);
			_Logger.info("Response Code: " + response.getCode());

			assertEquals("Response should be CHANGED (2.04)", ResponseCode.CHANGED, response.getCode());

			clientConn.shutdown();

			_Logger.info("----- [TEST COMPLETE] PUT SensorData to GDA -----\n");

		} catch (Exception e) {
			_Logger.severe("Test failed with exception: " + e.getMessage());
			e.printStackTrace();
			fail("Test failed: " + e.getMessage());
		}
	}

	/**
	 * Test DELETE request to GDA resource.
	 */
	@Test
	public void testDeleteRequest() {
		_Logger.info("\n\n----- [TEST] DELETE Request to GDA -----");

		try {
			// Create CoAP client
			String resourceUrl = baseUrl + "/" + ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE.getResourceName();
			_Logger.info("Sending DELETE to: " + resourceUrl);

			CoapClient clientConn = new CoapClient(resourceUrl);
			clientConn.setTimeout(DEFAULT_TIMEOUT);

			// Send DELETE request
			CoapResponse response = clientConn.delete();

			// Validate response
			assertNotNull("Response should not be null", response);
			_Logger.info("Response Code: " + response.getCode());

			assertEquals("Response should be DELETED (2.02)", ResponseCode.DELETED, response.getCode());

			clientConn.shutdown();

			_Logger.info("----- [TEST COMPLETE] DELETE Request to GDA -----\n");

		} catch (Exception e) {
			_Logger.severe("Test failed with exception: " + e.getMessage());
			e.printStackTrace();
			fail("Test failed: " + e.getMessage());
		}
	}

	/**
	 * Test the server's ability to handle multiple concurrent requests.
	 */
	@Test
	public void testMultipleConcurrentRequests() {
		_Logger.info("\n\n----- [TEST] Multiple Concurrent Requests -----");

		try {
			int numRequests = 5;
			_Logger.info("Sending " + numRequests + " concurrent GET requests...");

			Thread[] threads = new Thread[numRequests];

			for (int i = 0; i < numRequests; i++) {
				final int requestNum = i + 1;
				threads[i] = new Thread(() -> {
					try {
						String resourceUrl = baseUrl + "/"
								+ ResourceNameEnum.GDA_SYSTEM_PERF_MSG_RESOURCE.getResourceName();
						CoapClient client = new CoapClient(resourceUrl);
						client.setTimeout(DEFAULT_TIMEOUT);

						CoapResponse response = client.get();

						if (response != null) {
							_Logger.info("Request " + requestNum + " - Response Code: " + response.getCode());
							assertEquals(ResponseCode.CONTENT, response.getCode());
						}

						client.shutdown();
					} catch (Exception e) {
						_Logger.severe("Request " + requestNum + " failed: " + e.getMessage());
					}
				});

				threads[i].start();
			}

			// Wait for all threads to complete
			for (Thread thread : threads) {
				thread.join();
			}

			_Logger.info("All concurrent requests completed successfully.");
			_Logger.info("----- [TEST COMPLETE] Multiple Concurrent Requests -----\n");

		} catch (Exception e) {
			_Logger.severe("Test failed with exception: " + e.getMessage());
			e.printStackTrace();
			fail("Test failed: " + e.getMessage());
		}
	}

	/**
	 * Test long-running server to allow external clients to connect.
	 * This test runs for 2 minutes to allow manual testing with external CoAP
	 * clients.
	 */
	@Test
	public void testLongRunningServer() {
		_Logger.info("\n\n----- [TEST] Long Running Server (2 minutes) -----");
		_Logger.info("Server is running. You can now connect with external CoAP clients.");
		_Logger.info("Base URL: " + baseUrl);
		_Logger.info("Available resources:");
		_Logger.info("  - " + baseUrl + "/" + ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE.getResourceName());
		_Logger.info("  - " + baseUrl + "/" + ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE.getResourceName());
		_Logger.info("  - " + baseUrl + "/" + ResourceNameEnum.GDA_SYSTEM_PERF_MSG_RESOURCE.getResourceName());

		try {
			// Wait for 2 minutes (so other app tests can run)
			Thread.sleep(120000L);

			_Logger.info("----- [TEST COMPLETE] Long Running Server -----\n");

		} catch (Exception e) {
			_Logger.severe("Test failed with exception: " + e.getMessage());
			e.printStackTrace();
			fail("Test failed: " + e.getMessage());
		}
	}
}