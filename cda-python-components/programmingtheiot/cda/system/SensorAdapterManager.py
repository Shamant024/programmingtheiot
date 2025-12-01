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
from importlib import import_module

from apscheduler.schedulers.background import BackgroundScheduler

import programmingtheiot.common.ConfigConst as ConfigConst
from programmingtheiot.common.ConfigUtil import ConfigUtil
from programmingtheiot.common.IDataMessageListener import IDataMessageListener

from programmingtheiot.cda.sim.SensorDataGenerator import SensorDataGenerator
from programmingtheiot.cda.sim.HumiditySensorSimTask import HumiditySensorSimTask
from programmingtheiot.cda.sim.TemperatureSensorSimTask import TemperatureSensorSimTask
from programmingtheiot.cda.sim.PressureSensorSimTask import PressureSensorSimTask
from programmingtheiot.cda.sim.CO2SensorSimTask import CO2SensorSimTask

class SensorAdapterManager(object):
	"""
	Manager class for handling sensor adapters, supporting both simulation and emulation modes.
	
	Smart Office Project: Manages 4 sensors (Temperature, Humidity, Pressure, CO2)
	"""
	def __init__(self, dataMsgListener: IDataMessageListener = None):
		"""
		Constructor for SensorAdapterManager.
		
		@param dataMsgListener Optional data message listener for processing sensor data
		"""
		# Initialize data message listener
		self.dataMsgListener = dataMsgListener
		
		# Initialize configuration utility
		self.configUtil = ConfigUtil()
		
		# Get configuration properties
		self.useSimulator = self.configUtil.getBoolean(
			section=ConfigConst.CONSTRAINED_DEVICE, 
			key=ConfigConst.ENABLE_SIMULATOR_KEY)
		
		self.useEmulator = self.configUtil.getBoolean(
			section=ConfigConst.CONSTRAINED_DEVICE, 
			key=ConfigConst.ENABLE_EMULATOR_KEY)
		
		self.locationID = self.configUtil.getProperty(
			section=ConfigConst.CONSTRAINED_DEVICE, 
			key=ConfigConst.DEVICE_LOCATION_ID_KEY, 
			defaultVal=ConfigConst.NOT_SET)
		
		# Get polling rate for scheduler
		self.pollRate = self.configUtil.getInteger(
			section=ConfigConst.CONSTRAINED_DEVICE, 
			key=ConfigConst.POLL_CYCLES_KEY, 
			defaultVal=ConfigConst.DEFAULT_POLL_CYCLES)
		
		# Initialize scheduler
		self.scheduler = BackgroundScheduler()
		self.scheduler.add_job(
			func=self.handleTelemetry,
			trigger='interval',
			seconds=self.pollRate,
			max_instances=2,
			coalesce=True,
			misfire_grace_time=15)
		
		# Initialize sensor adapters based on configuration
		if self.useEmulator:
			logging.info("SensorAdapterManager will use emulators.")
			self._initSensorEmulationTasks()
		else:
			logging.info("SensorAdapterManager will use simulators.")
			self._initSensorSimulationTasks()
	
	def _initSensorSimulationTasks(self):
		"""
		Initialize sensor simulation tasks with data generators.
		Creates simulated sensors for Smart Office project (Temp, Humidity, Pressure, CO2).
		"""
		# Get floor and ceiling values from configuration
		humidityFloor = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE, 
			key=ConfigConst.HUMIDITY_SIM_FLOOR_KEY, 
			defaultVal=SensorDataGenerator.LOW_NORMAL_ENV_HUMIDITY)
		
		humidityCeiling = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE, 
			key=ConfigConst.HUMIDITY_SIM_CEILING_KEY, 
			defaultVal=SensorDataGenerator.HI_NORMAL_ENV_HUMIDITY)
		
		pressureFloor = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE, 
			key=ConfigConst.PRESSURE_SIM_FLOOR_KEY, 
			defaultVal=SensorDataGenerator.LOW_NORMAL_ENV_PRESSURE)
		
		pressureCeiling = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE, 
			key=ConfigConst.PRESSURE_SIM_CEILING_KEY, 
			defaultVal=SensorDataGenerator.HI_NORMAL_ENV_PRESSURE)
		
		tempFloor = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE, 
			key=ConfigConst.TEMP_SIM_FLOOR_KEY, 
			defaultVal=SensorDataGenerator.LOW_NORMAL_INDOOR_TEMP)
		
		tempCeiling = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE, 
			key=ConfigConst.TEMP_SIM_CEILING_KEY, 
			defaultVal=SensorDataGenerator.HI_NORMAL_INDOOR_TEMP)
		
		# NEW - Smart Office: Get CO2 floor and ceiling values
		co2Floor = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.CO2_SIM_FLOOR_KEY,
			defaultVal=400.0)  # Outdoor baseline CO2
		
		co2Ceiling = self.configUtil.getFloat(
			section=ConfigConst.CONSTRAINED_DEVICE,
			key=ConfigConst.CO2_SIM_CEILING_KEY,
			defaultVal=1200.0)  # High CO2 level
		
		# Generate data sets
		self.dataGenerator = SensorDataGenerator()
		
		humidityData = self.dataGenerator.generateDailyEnvironmentHumidityDataSet(
			minValue=humidityFloor, 
			maxValue=humidityCeiling, 
			useSeconds=False)
		
		pressureData = self.dataGenerator.generateDailyEnvironmentPressureDataSet(
			minValue=pressureFloor, 
			maxValue=pressureCeiling, 
			useSeconds=False)
		
		tempData = self.dataGenerator.generateDailyIndoorTemperatureDataSet(
			minValue=tempFloor, 
			maxValue=tempCeiling, 
			useSeconds=False)
		
		# NEW - Smart Office: Generate CO2 data with bell curve (occupancy pattern)
		co2Data = self.dataGenerator.generateDailySensorDataSet(
			curveType=SensorDataGenerator.BELL_CURVE,
			noiseLevel=15,
			minValue=co2Floor,
			maxValue=co2Ceiling,
			startHour=0,
			endHour=24,
			useSeconds=False)
		
		# Create sensor simulation tasks with generated data sets
		self.humidityAdapter = HumiditySensorSimTask(dataSet=humidityData)
		self.pressureAdapter = PressureSensorSimTask(dataSet=pressureData)
		self.tempAdapter = TemperatureSensorSimTask(dataSet=tempData)
		self.co2Adapter = CO2SensorSimTask(dataSet=co2Data)  # NEW - Smart Office
		
		logging.info("Sensor simulation tasks initialized (Temp, Humidity, Pressure, CO2).")
	
	def _initSensorEmulationTasks(self):
		"""
		Initialize sensor emulation tasks using dynamic loading.
		
		This method dynamically loads the emulator task modules at runtime to avoid
		import dependencies when emulation is not enabled. The emulator tasks interface
		with the SenseHAT emulator to read actual sensor values.
		
		NOTE: CO2 sensor will use simulation even in emulator mode (SenseHAT doesn't have CO2).
		"""
		logging.info("Loading sensor emulation tasks...")
		
		try:
			# Dynamically load HumiditySensorEmulatorTask
			heModule = import_module(
				'programmingtheiot.cda.emulated.HumiditySensorEmulatorTask',
				'HumiditySensorEmulatorTask')
			heClazz = getattr(heModule, 'HumiditySensorEmulatorTask')
			self.humidityAdapter = heClazz()
			logging.info("Successfully loaded HumiditySensorEmulatorTask")
			
			# Dynamically load PressureSensorEmulatorTask  
			peModule = import_module(
				'programmingtheiot.cda.emulated.PressureSensorEmulatorTask',
				'PressureSensorEmulatorTask')
			peClazz = getattr(peModule, 'PressureSensorEmulatorTask')
			self.pressureAdapter = peClazz()
			logging.info("Successfully loaded PressureSensorEmulatorTask")
			
			# Dynamically load TemperatureSensorEmulatorTask
			teModule = import_module(
				'programmingtheiot.cda.emulated.TemperatureSensorEmulatorTask',
				'TemperatureSensorEmulatorTask')
			teClazz = getattr(teModule, 'TemperatureSensorEmulatorTask')
			self.tempAdapter = teClazz()
			logging.info("Successfully loaded TemperatureSensorEmulatorTask")
			
			# NEW - Smart Office: CO2 sensor always uses simulation (SenseHAT doesn't have CO2)
			logging.info("CO2 sensor using simulation (SenseHAT emulator doesn't support CO2).")
			co2Floor = self.configUtil.getFloat(
				section=ConfigConst.CONSTRAINED_DEVICE,
				key=ConfigConst.CO2_SIM_FLOOR_KEY,
				defaultVal=400.0)
			
			co2Ceiling = self.configUtil.getFloat(
				section=ConfigConst.CONSTRAINED_DEVICE,
				key=ConfigConst.CO2_SIM_CEILING_KEY,
				defaultVal=1200.0)
			
			self.dataGenerator = SensorDataGenerator()
			co2Data = self.dataGenerator.generateDailySensorDataSet(
				curveType=SensorDataGenerator.BELL_CURVE,
				noiseLevel=15,
				minValue=co2Floor,
				maxValue=co2Ceiling,
				startHour=0,
				endHour=24,
				useSeconds=False)
			
			self.co2Adapter = CO2SensorSimTask(dataSet=co2Data)
			logging.info("CO2 sensor simulation task initialized.")
			
		except ImportError as e:
			logging.error("Failed to load sensor emulator tasks: %s", str(e))
			logging.warning("Falling back to sensor simulation tasks due to emulator load failure")
			# Fall back to simulation tasks if emulator loading fails
			self._initSensorSimulationTasks()
		except Exception as e:
			logging.error("Unexpected error loading sensor emulator tasks: %s", str(e))
			logging.warning("Falling back to sensor simulation tasks due to unexpected error")
			# Fall back to simulation tasks for any other errors
			self._initSensorSimulationTasks()
	
	def handleTelemetry(self):
		"""
		Handle telemetry collection from all sensor adapters.
		
		Smart Office: Collects data from Temperature, Humidity, Pressure, and CO2 sensors.
		"""
		try:
			# Generate telemetry from each sensor adapter
			humidityData = self.humidityAdapter.generateTelemetry()
			pressureData = self.pressureAdapter.generateTelemetry()
			tempData = self.tempAdapter.generateTelemetry()
			co2Data = self.co2Adapter.generateTelemetry()  # NEW - Smart Office
			
			# Set location ID for each sensor data instance
			humidityData.setLocationID(self.locationID)
			pressureData.setLocationID(self.locationID)
			tempData.setLocationID(self.locationID)
			co2Data.setLocationID(self.locationID)  # NEW - Smart Office
			
			# Log generated data for debugging
			logging.debug('Generated humidity data: %s', str(humidityData.getValue()))
			logging.debug('Generated pressure data: %s', str(pressureData.getValue()))
			logging.debug('Generated temp data: %s', str(tempData.getValue()))
			logging.debug('Generated CO2 data: %s ppm', str(co2Data.getValue()))  # NEW - Smart Office
			
			# Send data to message listener if available
			if self.dataMsgListener:
				self.dataMsgListener.handleSensorMessage(humidityData)
				self.dataMsgListener.handleSensorMessage(pressureData)
				self.dataMsgListener.handleSensorMessage(tempData)
				self.dataMsgListener.handleSensorMessage(co2Data)  # NEW - Smart Office
				
		except Exception as e:
			logging.error("Error handling telemetry collection: %s", str(e))
		
	def setDataMessageListener(self, listener: IDataMessageListener) -> bool:
		"""
		Set the data message listener for processing sensor data.
		
		@param listener The IDataMessageListener implementation
		@return bool True if listener was set successfully, False otherwise
		"""
		if listener:
			self.dataMsgListener = listener
			return True
		
		return False
	
	def startManager(self) -> bool:
		"""
		Start the sensor adapter manager and its scheduler.
		
		@return bool True if started successfully, False if already running
		"""
		logging.info("Starting SensorAdapterManager.")
		
		if not self.scheduler.running:
			self.scheduler.start()
			logging.info("Started SensorAdapterManager with 4 sensors (Temp, Humidity, Pressure, CO2).")
			return True
		else:
			logging.info("SensorAdapterManager scheduler already started. Ignoring.")
			return False
		
	def stopManager(self) -> bool:
		"""
		Stop the sensor adapter manager and its scheduler.
		
		@return bool True if stopped successfully, False if already stopped
		"""
		try:
			self.scheduler.shutdown()
			logging.info("Stopped SensorAdapterManager.")
			return True
		except:
			logging.info("SensorAdapterManager scheduler already stopped. Ignoring.")
			return False