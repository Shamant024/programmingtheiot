#####
# 
# This class is part of the Programming the Internet of Things
# project, and is available via the MIT License, which can be
# found in the LICENSE file at the top level of this repository.
# 
# You may find it more helpful to your design to adjust the
# functionality, constants and interfaces (if there are any)
# provided within in order to meet the needs of your specific
# Programming the Internet of Things project.
# 

import logging

import programmingtheiot.common.ConfigConst as ConfigConst
from programmingtheiot.common.ConfigUtil import ConfigUtil

from programmingtheiot.cda.connection.CoapClientConnector import CoapClientConnector
from programmingtheiot.cda.connection.MqttClientConnector import MqttClientConnector

from programmingtheiot.cda.system.ActuatorAdapterManager import ActuatorAdapterManager
from programmingtheiot.cda.system.SensorAdapterManager import SensorAdapterManager
from programmingtheiot.cda.system.SystemPerformanceManager import SystemPerformanceManager

from programmingtheiot.common.IDataMessageListener import IDataMessageListener
from programmingtheiot.common.ISystemPerformanceDataListener import ISystemPerformanceDataListener
from programmingtheiot.common.ITelemetryDataListener import ITelemetryDataListener
from programmingtheiot.common.ResourceNameEnum import ResourceNameEnum

from programmingtheiot.data.ActuatorData import ActuatorData
from programmingtheiot.data.SensorData import SensorData
from programmingtheiot.data.SystemPerformanceData import SystemPerformanceData
from programmingtheiot.data.DataUtil import DataUtil

class DeviceDataManager(IDataMessageListener):
	"""
	Central manager for the Constrained Device Application (CDA).
	
	Smart Office Project: Coordinates environmental monitoring and actuation
	for optimal office conditions.
	
	Monitors:
	- Temperature, Humidity, Pressure, CO2 levels
	
	Controls:
	- Ventilation System (CO2 > 1000ppm OR Temp > 26°C)
	- Air Purifier (Humidity > 60%)
	"""
	
	def __init__(self):
		"""
		Constructor for DeviceDataManager.
		
		Initializes all managers and configuration settings.
		"""
		# Initialize configuration
		self.configUtil = ConfigUtil()
		
		# Load configuration properties
		self.enableSystemPerf = self.configUtil.getBoolean(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.ENABLE_SYSTEM_PERF_KEY)
		
		self.enableSensing = self.configUtil.getBoolean(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.ENABLE_SENSING_KEY)
		
		self.enableMqtt = self.configUtil.getBoolean(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.ENABLE_MQTT_CLIENT_KEY)
		
		self.enableCoap = self.configUtil.getBoolean(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.ENABLE_COAP_CLIENT_KEY)
		
		# Smart Office Project - CO2 and Ventilation Configuration
		self.handleCO2ChangeOnDevice = self.configUtil.getBoolean(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.HANDLE_CO2_CHANGE_ON_DEVICE_KEY)
		
		self.triggerVentilationCO2Threshold = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.TRIGGER_VENTILATION_CO2_THRESHOLD_KEY,
			defaultVal=1000.0)
		
		self.triggerVentilationTempThreshold = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.TRIGGER_VENTILATION_TEMP_THRESHOLD_KEY,
			defaultVal=26.0)
		
		self.triggerAirPurifierHumidityThreshold = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.TRIGGER_AIR_PURIFIER_HUMIDITY_THRESHOLD_KEY,
			defaultVal=60.0)
		
		# Legacy temperature handling (keeping for compatibility)
		self.handleTempChangeOnDevice = self.configUtil.getBoolean(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.HANDLE_TEMP_CHANGE_ON_DEVICE_KEY)
		
		self.triggerHvacTempFloor = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.TRIGGER_HVAC_TEMP_FLOOR_KEY,
			defaultVal=18.0)
		
		self.triggerHvacTempCeiling = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.TRIGGER_HVAC_TEMP_CEILING_KEY,
			defaultVal=20.0)
		
		# Initialize managers based on configuration
		if self.enableSystemPerf:
			self.sysPerfMgr = SystemPerformanceManager()
			self.sysPerfMgr.setDataMessageListener(self)
			logging.info("Local system performance tracking enabled")
		else:
			self.sysPerfMgr = None
			
		if self.enableSensing:
			self.sensorAdapterMgr = SensorAdapterManager()
			self.sensorAdapterMgr.setDataMessageListener(self)
			logging.info("Local sensor tracking enabled (Temp, Humidity, Pressure, CO2)")
		else:
			self.sensorAdapterMgr = None
		
		# Initialize actuator adapter manager
		self.actuatorAdapterMgr = ActuatorAdapterManager()
		self.actuatorAdapterMgr.setDataMessageListener(self)
		logging.info("Local actuation capabilities enabled (Ventilation, Air Purifier)")
		
		# Initialize MQTT client connector if enabled
		if self.enableMqtt:
			self.mqttClient = MqttClientConnector()
			self.mqttClient.setDataMessageListener(self)
			logging.info("MQTT client enabled")
		else:
			self.mqttClient = None
			logging.info("MQTT client disabled by configuration")
		
		# Initialize CoAP client connector if enabled
		if self.enableCoap:
			self.coapClient = CoapClientConnector()
			self.coapClient.setDataMessageListener(self)
			logging.info("CoAP client enabled")
		else:
			self.coapClient = None
			logging.info("CoAP client disabled by configuration")
		
		# Initialize data caches
		self.latestSensorDataCache = {}
		self.latestActuatorDataCache = {}
		self.latestSystemPerfDataCache = {}
		
		# Initialize listeners
		self.sysPerfDataListener = None
		self.telemetryDataListener = None
		
		# Initialize DataUtil for JSON conversions
		self.dataUtil = DataUtil()
		
		logging.info("DeviceDataManager initialization complete - Smart Office Project ready.")
		
	def getLatestActuatorDataResponseFromCache(self, name: str = None) -> ActuatorData:
		"""
		Retrieves the named actuator data (response) item from the internal data cache.
		
		@param name The actuator name to retrieve
		@return ActuatorData The cached actuator response data, or None if not found
		"""
		if name and name in self.latestActuatorDataCache:
			return self.latestActuatorDataCache[name]
		
		return None
		
	def getLatestSensorDataFromCache(self, name: str = None) -> SensorData:
		"""
		Retrieves the named sensor data item from the internal data cache.
		
		@param name The sensor name to retrieve
		@return SensorData The cached sensor data, or None if not found
		"""
		if name and name in self.latestSensorDataCache:
			return self.latestSensorDataCache[name]
		
		return None
	
	def getLatestSystemPerformanceDataFromCache(self, name: str = None) -> SystemPerformanceData:
		"""
		Retrieves the named system performance data from the internal data cache.
		
		@param name The system performance data name to retrieve  
		@return SystemPerformanceData The cached system performance data, or None if not found
		"""
		if name and name in self.latestSystemPerfDataCache:
			return self.latestSystemPerfDataCache[name]
		
		return None
	
	def handleActuatorCommandMessage(self, data: ActuatorData) -> ActuatorData:
		"""
		This callback method will be invoked by the connection that's handling
		an incoming ActuatorData command message.
		
		@param data The incoming ActuatorData command message
		@return ActuatorData The response from processing the command
		"""
		logging.info("Actuator command received: " + str(data))
		
		if data:
			logging.info("Processing actuator command message: %s", data.getName())
			return self.actuatorAdapterMgr.sendActuatorCommand(data)
		else:
			logging.warning("Incoming actuator command is invalid (null). Ignoring.")
			return None
	
	def handleActuatorCommandResponse(self, data: ActuatorData) -> bool:
		"""
		This callback method will be invoked by the actuator manager that just
		processed an ActuatorData command, which creates a new ActuatorData
		instance and sets it as a response before calling this method.
		
		@param data The incoming ActuatorData response message
		@return bool True if processed successfully, False otherwise
		"""
		if data:
			logging.info("Incoming actuator response received (from actuator manager): " + str(data))
			
			# Cache the response data
			if data.getName():
				self.latestActuatorDataCache[data.getName()] = data
			
			# Transmit actuator response upstream via MQTT and/or CoAP
			self._handleUpstreamTransmission(
				ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE,
				self.dataUtil.actuatorDataToJson(data)
			)
			
			return True
		else:
			logging.warning("Incoming actuator response is invalid (null). Ignoring.")
			return False
	
	def handleIncomingMessage(self, resourceEnum: ResourceNameEnum, msg: str) -> bool:
		"""
		This callback method is generic and designed to handle any incoming string-based
		message, which will likely be JSON-formatted and need to be converted to the appropriate
		data type.
		
		@param resourceEnum The resource enumeration for the message
		@param msg The incoming JSON message
		@return bool True if processed successfully, False otherwise
		"""
		logging.info("Incoming message received for resource: %s", str(resourceEnum))
		logging.debug("Message content: %s", msg)
		
		if msg:
			self._handleIncomingDataAnalysis(msg)
			return True
		
		return False
	
	def handleSensorMessage(self, data: SensorData) -> bool:
		"""
		This callback method will be invoked by the sensor manager that just processed
		a new sensor reading.
		
		Smart Office: Analyzes CO2, Temperature, and Humidity for actuation triggers.
		
		@param data The incoming SensorData message
		@return bool True if processed successfully, False otherwise
		"""
		if data:
			logging.info("Incoming sensor data received: %s = %.2f", data.getName(), data.getValue())
			
			# Cache the sensor data
			if data.getName():
				self.latestSensorDataCache[data.getName()] = data
			
			# Smart Office: Analyze sensor data for threshold crossings
			self._handleSensorDataAnalysis(data)
			
			# Transmit sensor data upstream via MQTT and/or CoAP
			self._handleUpstreamTransmission(
				ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE,
				self.dataUtil.sensorDataToJson(data)
			)
			
			return True
		else:
			logging.warning("Incoming sensor data is invalid (null). Ignoring.")
			return False
	
	def handleSystemPerformanceMessage(self, data: SystemPerformanceData) -> bool:
		"""
		This callback method will be invoked by the system performance manager.
		
		@param data The incoming SystemPerformanceData message
		@return bool True if processed successfully, False otherwise
		"""
		if data:
			logging.info("Incoming system performance message received: CPU=%.2f%%, Mem=%.2f%%", 
				data.getCpuUtilization(), data.getMemoryUtilization())
			
			# Cache the system performance data
			if data.getName():
				self.latestSystemPerfDataCache[data.getName()] = data
			
			# Transmit system performance data upstream via MQTT and/or CoAP
			self._handleUpstreamTransmission(
				ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE,
				self.dataUtil.systemPerformanceDataToJson(data)
			)
			
			return True
		else:
			logging.warning("Incoming system performance data is invalid (null). Ignoring.")
			return False
	
	def setSystemPerformanceDataListener(self, listener: ISystemPerformanceDataListener = None):
		"""
		Set the system performance data listener.
		
		@param listener The system performance data listener
		"""
		if listener:
			self.sysPerfDataListener = listener
			
	def setTelemetryDataListener(self, name: str = None, listener: ITelemetryDataListener = None):
		"""
		Set the telemetry data listener.
		
		@param name The listener name
		@param listener The telemetry data listener
		"""
		if listener:
			self.telemetryDataListener = listener
			
	def startManager(self):
		"""
		Start the DeviceDataManager and all associated managers.
		"""
		logging.info("Starting DeviceDataManager for Smart Office Project...")
		
		# Start MQTT client if enabled
		if self.mqttClient:
			self.mqttClient.connectClient()
			
			# Subscribe to actuator command topic to receive commands from GDA
			self.mqttClient.subscribeToTopic(
				ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, 
				qos=ConfigConst.DEFAULT_QOS
			)
			
			# Subscribe to management status command topic
			self.mqttClient.subscribeToTopic(
				ResourceNameEnum.CDA_MGMT_STATUS_CMD_RESOURCE,
				qos=ConfigConst.DEFAULT_QOS
			)
			
			logging.info("MQTT client connected and subscribed to topics.")
		
		# Start CoAP client if enabled
		if self.coapClient:
			logging.info("CoAP client connection enabled.")
			
			# Optionally perform discovery to find available resources on GDA
			logging.info("Performing CoAP resource discovery...")
			self.coapClient.sendDiscoveryRequest(timeout=10)
		
		# Start system performance manager
		if self.sysPerfMgr:
			self.sysPerfMgr.startManager()
		
		# Start sensor adapter manager (will collect Temp, Humidity, Pressure, CO2)
		if self.sensorAdapterMgr:
			self.sensorAdapterMgr.startManager()
			logging.info("Sensor adapter manager started - monitoring office environment.")
		
		logging.info("Started DeviceDataManager - Smart Office system active.")
		
	def stopManager(self):
		"""
		Stop the DeviceDataManager and all associated managers.
		"""
		logging.info("Stopping DeviceDataManager...")
		
		# Stop system performance manager
		if self.sysPerfMgr:
			self.sysPerfMgr.stopManager()
		
		# Stop sensor adapter manager  
		if self.sensorAdapterMgr:
			self.sensorAdapterMgr.stopManager()
		
		# Disconnect MQTT client if enabled
		if self.mqttClient:
			# Unsubscribe from topics
			self.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE)
			self.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_MGMT_STATUS_CMD_RESOURCE)
			
			# Disconnect from broker
			self.mqttClient.disconnectClient()
			logging.info("MQTT client disconnected.")
		
		# Disconnect CoAP client if enabled
		if self.coapClient:
			logging.info("CoAP client disconnected.")
		
		logging.info("Stopped DeviceDataManager.")
		
	def _handleIncomingDataAnalysis(self, msg: str):
		"""
		Analyzes incoming messages and converts them to appropriate data types.
		
		@param msg The incoming message to analyze
		"""
		logging.info("Analyzing incoming data analysis message...")
		
		try:
			# Try to convert JSON to ActuatorData
			actuatorData = self.dataUtil.jsonToActuatorData(msg)
			
			if actuatorData:
				logging.info("Converted incoming JSON to ActuatorData: " + str(actuatorData))
				# Process the actuator command
				self.handleActuatorCommandMessage(actuatorData)
			else:
				logging.warning("Failed to convert JSON to ActuatorData. Message: " + msg)
		except Exception as e:
			logging.error("Error analyzing incoming data: " + str(e))
		
	def _handleSensorDataAnalysis(self, data: SensorData):
		"""
		Smart Office: Analyzes sensor data and triggers appropriate actuation.
		
		Ventilation triggers:
		- CO2 > 1000 ppm
		- Temperature > 26°C
		
		Air Purifier triggers:
		- Humidity > 60%
		
		@param data The sensor data to analyze
		"""
		# Smart Office: Handle CO2 levels for ventilation control
		if self.handleCO2ChangeOnDevice and data.getTypeID() == ConfigConst.CO2_SENSOR_TYPE:
			logging.info("Analyzing CO2 data: %.2f ppm (threshold: %.2f ppm)", 
				data.getValue(), self.triggerVentilationCO2Threshold)
			
			if data.getValue() > self.triggerVentilationCO2Threshold:
				logging.warning("CO2 level HIGH! Activating ventilation system.")
				
				# Create ventilation actuator command
				ad = ActuatorData(typeID=ConfigConst.VENTILATION_ACTUATOR_TYPE)
				ad.setLocationID(data.getLocationID())
				ad.setName(ConfigConst.VENTILATION_ACTUATOR_NAME)
				ad.setCommand(ConfigConst.COMMAND_ON)
				ad.setValue(data.getValue())
				ad.setStateData("CO2 level high - ventilation ON")
				
				# Send actuator command
				self.handleActuatorCommandMessage(ad)
			else:
				logging.info("CO2 level NORMAL (%.2f ppm). Ventilation not needed.", data.getValue())
		
		# Smart Office: Handle Temperature for ventilation control
		if self.handleCO2ChangeOnDevice and data.getTypeID() == ConfigConst.TEMP_SENSOR_TYPE:
			logging.info("Analyzing temperature data: %.2f°C (threshold: %.2f°C)", 
				data.getValue(), self.triggerVentilationTempThreshold)
			
			if data.getValue() > self.triggerVentilationTempThreshold:
				logging.warning("Temperature HIGH! Activating ventilation system.")
				
				# Create ventilation actuator command
				ad = ActuatorData(typeID=ConfigConst.VENTILATION_ACTUATOR_TYPE)
				ad.setLocationID(data.getLocationID())
				ad.setName(ConfigConst.VENTILATION_ACTUATOR_NAME)
				ad.setCommand(ConfigConst.COMMAND_ON)
				ad.setValue(data.getValue())
				ad.setStateData("Temperature high - ventilation ON")
				
				# Send actuator command
				self.handleActuatorCommandMessage(ad)
			else:
				logging.info("Temperature NORMAL (%.2f°C). Ventilation not needed.", data.getValue())
		
		# Smart Office: Handle Humidity for air purifier control
		if self.handleCO2ChangeOnDevice and data.getTypeID() == ConfigConst.HUMIDITY_SENSOR_TYPE:
			logging.info("Analyzing humidity data: %.2f%% (threshold: %.2f%%)", 
				data.getValue(), self.triggerAirPurifierHumidityThreshold)
			
			if data.getValue() > self.triggerAirPurifierHumidityThreshold:
				logging.warning("Humidity HIGH! Activating air purifier.")
				
				# Create air purifier actuator command
				ad = ActuatorData(typeID=ConfigConst.AIR_PURIFIER_ACTUATOR_TYPE)
				ad.setLocationID(data.getLocationID())
				ad.setName(ConfigConst.AIR_PURIFIER_ACTUATOR_NAME)
				ad.setCommand(ConfigConst.COMMAND_ON)
				ad.setValue(data.getValue())
				ad.setStateData("Humidity high - air purifier ON")
				
				# Send actuator command
				self.handleActuatorCommandMessage(ad)
			else:
				logging.info("Humidity NORMAL (%.2f%%). Air purifier not needed.", data.getValue())
		
	def _handleUpstreamTransmission(self, resourceName: ResourceNameEnum, msg: str):
		"""
		Transmits data upstream to GDA via MQTT or CoAP.
		
		@param resourceName The resource name for the message
		@param msg The message to transmit upstream
		"""
		logging.debug("Upstream transmission invoked for resource: " + str(resourceName))
		
		mqttSuccess = False
		coapSuccess = False
		
		# Transmit via MQTT if enabled and connected
		if self.mqttClient:
			mqttSuccess = self.mqttClient.publishMessage(
				resource=resourceName,
				msg=msg,
				qos=ConfigConst.DEFAULT_QOS
			)
			
			if mqttSuccess:
				logging.debug("MQTT transmission successful.")
			else:
				logging.warning("MQTT transmission failed.")
		
		# Transmit via CoAP if enabled
		if self.coapClient:
			# Map CDA resources to GDA resources for CoAP transmission
			gdaResourceName = self._mapCdaResourceToGda(resourceName)
			
			if gdaResourceName:
				# Use POST to send data to GDA
				coapSuccess = self.coapClient.sendPostRequest(
					resource=gdaResourceName,
					payload=msg,
					timeout=10
				)
				
				if coapSuccess:
					logging.debug("CoAP transmission successful.")
				else:
					logging.warning("CoAP transmission failed.")
			else:
				logging.warning("Could not map CDA resource to GDA resource for CoAP transmission.")
		
		# If no upstream communication is configured
		if not self.mqttClient and not self.coapClient:
			logging.debug("No upstream communication configured. Message not transmitted.")
	
	def _mapCdaResourceToGda(self, cdaResource: ResourceNameEnum) -> ResourceNameEnum:
		"""
		Maps CDA resource names to corresponding GDA resource names for CoAP communication.
		
		@param cdaResource The CDA resource enumeration
		@return ResourceNameEnum The corresponding GDA resource, or None if no mapping exists
		"""
		if cdaResource == ResourceNameEnum.CDA_SENSOR_DATA_MSG_RESOURCE:
			return ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE
		elif cdaResource == ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE:
			return ResourceNameEnum.GDA_SYSTEM_PERF_MSG_RESOURCE
		elif cdaResource == ResourceNameEnum.CDA_ACTUATOR_RESPONSE_MSG_RESOURCE:
			return ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE
		else:
			logging.warning("No GDA resource mapping for CDA resource: %s", str(cdaResource))
			return None