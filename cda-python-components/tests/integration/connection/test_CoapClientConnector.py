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

from time import sleep

import programmingtheiot.common.ConfigConst as ConfigConst

from programmingtheiot.common.ConfigUtil import ConfigUtil
from programmingtheiot.common.DefaultDataMessageListener import DefaultDataMessageListener
from programmingtheiot.common.ResourceNameEnum import ResourceNameEnum

from programmingtheiot.cda.connection.CoapClientConnector import CoapClientConnector

from programmingtheiot.data.DataUtil import DataUtil
from programmingtheiot.data.SensorData import SensorData
from programmingtheiot.data.SystemPerformanceData import SystemPerformanceData

class CoapClientConnectorTest(unittest.TestCase):
	"""
	This test case class contains very basic integration tests for
	CoapClientConnector using a separately running CoAP server.
	
	It should not be considered complete,
	but serve as a starting point for the student implementing
	additional functionality within their Programming the IoT
	environment.
	
	NOTE: This is different from CoapServerAdapterTest in that it depends
	upon an external CoAP server (e.g., the GDA's CoAP server).
	
	IMPORTANT: Make sure the GDA CoAP server is running before executing these tests!
	"""
	
	@classmethod
	def setUpClass(self):
		logging.basicConfig(format = '%(asctime)s:%(module)s:%(levelname)s:%(message)s', level = logging.INFO)
		logging.info("Testing CoapClientConnector class...")
		logging.info("NOTE: Ensure GDA CoAP server is running on localhost:5683")
		
		self.dataMsgListener = DefaultDataMessageListener()
		
		self.pollRate = ConfigUtil().getInteger(ConfigConst.CONSTRAINED_DEVICE, ConfigConst.POLL_CYCLES_KEY, ConfigConst.DEFAULT_POLL_CYCLES)
		
		self.coapClient = CoapClientConnector()
		self.coapClient.setDataMessageListener(self.dataMsgListener)
		
	@classmethod
	def tearDownClass(self):
		logging.info("CoapClientConnector tests complete.")
		
	def setUp(self):
		pass

	def tearDown(self):
		pass

	#@unittest.skip("Ignore for now.")
	def testConnectAndDiscover(self):
		"""
		Test DISCOVERY functionality - finds all resources on GDA server.
		Remove the skip annotation to run this test.
		"""
		logging.info("\n\n===== TEST: Connect and Discover =====")
		
		success = self.coapClient.sendDiscoveryRequest(timeout=10)
		
		self.assertTrue(success, "Discovery request should succeed")
		
		sleep(2)
		
		logging.info("===== TEST COMPLETE: Connect and Discover =====\n")

	#@unittest.skip("Ignore for now.")
	def testGetRequest(self):
		"""
		Test GET functionality - retrieves resource data from GDA.
		Remove the skip annotation to run this test.
		"""
		logging.info("\n\n===== TEST: GET Request =====")
		
		# Test GET to GDA system performance resource
		success = self.coapClient.sendGetRequest(
			resource=ResourceNameEnum.GDA_SYSTEM_PERF_MSG_RESOURCE,
			enableCON=True,
			timeout=10
		)
		
		self.assertTrue(success, "GET request should succeed")
		
		sleep(1)
		
		logging.info("===== TEST COMPLETE: GET Request =====\n")

	#@unittest.skip("Ignore for now.")
	def testPostSensorMessage(self):
		"""
		Test POST functionality - sends sensor data to GDA.
		Remove the skip annotation to run this test.
		"""
		logging.info("\n\n===== TEST: POST Sensor Message =====")
		
		# Create sample sensor data
		data = SensorData()
		data.setName("TempSensor")
		data.setTypeID(ConfigConst.TEMP_SENSOR_TYPE)
		data.setValue(22.5)
		
		jsonData = DataUtil().sensorDataToJson(data=data)
		
		logging.info("Sending SensorData: %s", jsonData)
		
		# POST to GDA management status message resource
		success = self.coapClient.sendPostRequest(
			resource=ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE,
			enableCON=True,
			payload=jsonData,
			timeout=10
		)
		
		self.assertTrue(success, "POST request should succeed")
		
		sleep(1)
		
		logging.info("===== TEST COMPLETE: POST Sensor Message =====\n")

	#@unittest.skip("Ignore for now.")
	def testPostSystemPerformanceMessage(self):
		"""
		Test POST functionality with SystemPerformanceData.
		Remove the skip annotation to run this test.
		"""
		logging.info("\n\n===== TEST: POST System Performance Message =====")
		
		# Create sample system performance data
		data = SystemPerformanceData()
		data.setName("CDA System Performance")
		data.setCpuUtilization(55.2)
		data.setMemoryUtilization(68.7)
		data.setDiskUtilization(42.1)
		
		jsonData = DataUtil().systemPerformanceDataToJson(data=data)
		
		logging.info("Sending SystemPerformanceData: %s", jsonData)
		
		# POST to GDA system performance resource
		success = self.coapClient.sendPostRequest(
			resource=ResourceNameEnum.GDA_SYSTEM_PERF_MSG_RESOURCE,
			enableCON=True,
			payload=jsonData,
			timeout=10
		)
		
		self.assertTrue(success, "POST request should succeed")
		
		sleep(1)
		
		logging.info("===== TEST COMPLETE: POST System Performance Message =====\n")
	
	#@unittest.skip("Ignore for now.")
	def testPutSensorMessage(self):
		"""
		Test PUT functionality - updates sensor data on GDA.
		Remove the skip annotation to run this test.
		"""
		logging.info("\n\n===== TEST: PUT Sensor Message =====")
		
		# Create sample sensor data
		data = SensorData()
		data.setName("HumiditySensor")
		data.setTypeID(ConfigConst.HUMIDITY_SENSOR_TYPE)
		data.setValue(45.8)
		
		jsonData = DataUtil().sensorDataToJson(data=data)
		
		logging.info("Sending SensorData via PUT: %s", jsonData)
		
		# PUT to GDA management status message resource
		success = self.coapClient.sendPutRequest(
			resource=ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE,
			enableCON=True,
			payload=jsonData,
			timeout=10
		)
		
		self.assertTrue(success, "PUT request should succeed")
		
		sleep(1)
		
		logging.info("===== TEST COMPLETE: PUT Sensor Message =====\n")

	#@unittest.skip("Ignore for now.")
	def testDeleteRequest(self):
		"""
		Test DELETE functionality - removes resource from GDA.
		Remove the skip annotation to run this test.
		"""
		logging.info("\n\n===== TEST: DELETE Request =====")
		
		# Send DELETE to GDA management status resource
		success = self.coapClient.sendDeleteRequest(
			resource=ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE,
			enableCON=True,
			timeout=10
		)
		
		self.assertTrue(success, "DELETE request should succeed")
		
		sleep(1)
		
		logging.info("===== TEST COMPLETE: DELETE Request =====\n")

	@unittest.skip("Ignore for now - OBSERVE not yet implemented.")
	def testActuatorCommandObserve(self):
		"""
		Test OBSERVE functionality - watches for changes to resources.
		This will be implemented in future lab modules.
		Remove the skip annotation to run this test when OBSERVE is implemented.
		"""
		logging.info("\n\n===== TEST: OBSERVE Actuator Command =====")
		
		self._startObserver()
		
		logging.info("Observing resource for 30 seconds...")
		sleep(30)
		
		self._stopObserver()
		
		logging.info("===== TEST COMPLETE: OBSERVE Actuator Command =====\n")
		
	def _startObserver(self):
		"""Helper method to start observing a resource."""
		self.coapClient.startObserver(resource=ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE)

	def _stopObserver(self):
		"""Helper method to stop observing a resource."""
		self.coapClient.stopObserver(resource=ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE)

if __name__ == "__main__":
	unittest.main()