#####
# 
# This class is part of the Programming the Internet of Things
# project, and is available via the MIT License, which can be
# found in the LICENSE file at the top level of this repository.
# 
# Copyright (c) 2020 - 2025 by Andrew D. King
# 

import logging
import unittest
import time

from programmingtheiot.cda.connection.MqttClientConnector import MqttClientConnector
from programmingtheiot.common.ResourceNameEnum import ResourceNameEnum
from programmingtheiot.data.DataUtil import DataUtil
from programmingtheiot.data.SensorData import SensorData

class MqttClientPerformanceTest(unittest.TestCase):
	"""
	Performance test for MQTT Client Connector.
	Tests publishing performance with different QoS levels.
	
	PIOT-INT-10-001: Test the performance of MQTT using all three QoS levels
	"""
	
	NS_IN_MILLIS = 1000000
	MAX_TEST_RUNS = 10000
	
	@classmethod
	def setUpClass(cls):
		logging.basicConfig(
			format='%(asctime)s:%(module)s:%(levelname)s:%(message)s', 
			level=logging.INFO  # Changed to INFO to reduce noise during performance tests
		)
		logging.info("\n\n***** MQTT Client Performance Tests - PIOT-INT-10-001 *****\n")
		
	def setUp(self):
		"""
		Set up each test - create a new MQTT client instance
		"""
		self.mqttClient = MqttClientConnector(clientID='CDAMqttClientPerformanceTest001')
		
	def tearDown(self):
		"""
		Clean up after each test
		"""
		pass

	#@unittest.skip("Ignore for now.")
	def testConnectAndDisconnect(self):
		"""
		Test 1: Measure connection and disconnection time
		"""
		logging.info("\n----- Test: Connect and Disconnect -----")
		
		startTime = time.time_ns()
		
		self.assertTrue(self.mqttClient.connectClient())
		time.sleep(1)  # Wait for connection to stabilize
		self.assertTrue(self.mqttClient.disconnectClient())
		
		endTime = time.time_ns()
		elapsedMillis = (endTime - startTime) / self.NS_IN_MILLIS
		
		logging.info("Connect and Disconnect: %.2f ms\n", elapsedMillis)
		
	#@unittest.skip("Ignore for now.")
	def testPublishQoS0(self):
		"""
		Test 2: Measure publishing performance with QoS 0 (At most once)
		"""
		logging.info("\n----- Test: Publish with QoS 0 -----")
		self._execTestPublish(self.MAX_TEST_RUNS, 0)

	#@unittest.skip("Ignore for now.")
	def testPublishQoS1(self):
		"""
		Test 3: Measure publishing performance with QoS 1 (At least once)
		"""
		logging.info("\n----- Test: Publish with QoS 1 -----")
		self._execTestPublish(self.MAX_TEST_RUNS, 1)

	#@unittest.skip("Ignore for now.")
	def testPublishQoS2(self):
		"""
		Test 4: Measure publishing performance with QoS 2 (Exactly once)
		"""
		logging.info("\n----- Test: Publish with QoS 2 -----")
		self._execTestPublish(self.MAX_TEST_RUNS, 2)

	def _execTestPublish(self, maxTestRuns: int, qos: int):
		"""
		Execute the publish performance test for a given QoS level.
		
		@param maxTestRuns Number of messages to publish
		@param qos Quality of Service level (0, 1, or 2)
		"""
		# Connect to broker
		self.assertTrue(self.mqttClient.connectClient())
		time.sleep(1)  # Wait for connection to stabilize
		
		# Create test sensor data
		sensorData = SensorData()
		payload = DataUtil().sensorDataToJson(sensorData)
		payloadLen = len(payload)
		
		logging.info("Starting publish test: QoS=%d, Messages=%d, Payload Size=%d bytes", 
					 qos, maxTestRuns, payloadLen)
		
		# Start timing
		startTime = time.time_ns()
		
		# Publish messages
		for seqNo in range(0, maxTestRuns):
			self.mqttClient.publishMessage(
				resource=ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, 
				msg=payload, 
				qos=qos
			)
		
		# End timing
		endTime = time.time_ns()
		elapsedMillis = (endTime - startTime) / self.NS_IN_MILLIS
		
		# Disconnect from broker
		self.assertTrue(self.mqttClient.disconnectClient())
		
		# Log results
		logging.info("=" * 70)
		logging.info("PERFORMANCE TEST RESULTS - QoS %d", qos)
		logging.info("=" * 70)
		logging.info("Total Messages:    %d", maxTestRuns)
		logging.info("Payload Size:      %d bytes", payloadLen)
		logging.info("Total Time:        %.2f ms", elapsedMillis)
		logging.info("Average per msg:   %.4f ms", elapsedMillis / maxTestRuns)
		logging.info("Messages per sec:  %.2f", (maxTestRuns / elapsedMillis) * 1000)
		logging.info("=" * 70 + "\n")
	
if __name__ == "__main__":
	unittest.main()