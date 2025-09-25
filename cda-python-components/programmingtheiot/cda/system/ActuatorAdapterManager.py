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

import programmingtheiot.common.ConfigConst as ConfigConst
from programmingtheiot.common.ConfigUtil import ConfigUtil
from programmingtheiot.common.IDataMessageListener import IDataMessageListener

from programmingtheiot.data.ActuatorData import ActuatorData
from programmingtheiot.cda.sim.HvacActuatorSimTask import HvacActuatorSimTask
from programmingtheiot.cda.sim.HumidifierActuatorSimTask import HumidifierActuatorSimTask

class ActuatorAdapterManager(object):
	"""
	Manager class for handling actuator adapters, supporting both simulation and emulation modes.
	
	This class coordinates between simulated and emulated actuators based on configuration settings,
	and manages the routing of actuation commands to the appropriate actuator tasks.
	"""
	
	def __init__(self, dataMsgListener: IDataMessageListener = None):
		"""
		Constructor for ActuatorAdapterManager.
		
		@param dataMsgListener Optional data message listener for processing actuator responses
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
		
		# Initialize actuator task references
		self.humidifierActuator = None
		self.hvacActuator = None
		self.ledDisplayActuator = None
		
		# Initialize actuator tasks based on configuration
		if self.useEmulator:
			logging.info("ActuatorAdapterManager will use emulators.")
			self._initActuatorEmulationTasks()
		else:
			logging.info("ActuatorAdapterManager will use simulators.")
			self._initEnvironmentalActuationTasks()
	
	def _initEnvironmentalActuationTasks(self):
		"""
		Initialize environmental actuator simulation tasks.
		Creates simulated actuators that log actuation commands without real hardware interaction.
		"""
		# Create simulator tasks for environmental control
		self.humidifierActuator = HumidifierActuatorSimTask()
		self.hvacActuator = HvacActuatorSimTask()
		# Note: LED display actuator is typically only available in emulation mode
		
		logging.info("Environmental actuator simulation tasks initialized.")
	
	def _initActuatorEmulationTasks(self):
		"""
		Initialize actuator emulation tasks using dynamic loading.
		
		This method dynamically loads the emulator task modules at runtime to avoid
		import dependencies when emulation is not enabled. The emulator tasks interface
		with the SenseHAT emulator to provide visual feedback on actuation commands.
		"""
		logging.info("Loading actuator emulation tasks...")
		
		try:
			# Dynamically load HumidifierEmulatorTask
			hueModule = import_module(
				'programmingtheiot.cda.emulated.HumidifierEmulatorTask',
				'HumidifierEmulatorTask')
			hueClazz = getattr(hueModule, 'HumidifierEmulatorTask')
			self.humidifierActuator = hueClazz()
			logging.info("Successfully loaded HumidifierEmulatorTask")
			
			# Dynamically load HvacEmulatorTask
			hveModule = import_module(
				'programmingtheiot.cda.emulated.HvacEmulatorTask', 
				'HvacEmulatorTask')
			hveClazz = getattr(hveModule, 'HvacEmulatorTask')
			self.hvacActuator = hveClazz()
			logging.info("Successfully loaded HvacEmulatorTask")
			
			# Dynamically load LedDisplayEmulatorTask
			leDisplayModule = import_module(
				'programmingtheiot.cda.emulated.LedDisplayEmulatorTask',
				'LedDisplayEmulatorTask')
			leClazz = getattr(leDisplayModule, 'LedDisplayEmulatorTask')
			self.ledDisplayActuator = leClazz()
			logging.info("Successfully loaded LedDisplayEmulatorTask")
			
		except ImportError as e:
			logging.error("Failed to load actuator emulator tasks: %s", str(e))
			logging.warning("Falling back to actuator simulation tasks due to emulator load failure")
			# Fall back to simulation tasks if emulator loading fails
			self._initEnvironmentalActuationTasks()
		except Exception as e:
			logging.error("Unexpected error loading actuator emulator tasks: %s", str(e))
			logging.warning("Falling back to actuator simulation tasks due to unexpected error")
			# Fall back to simulation tasks for any other errors
			self._initEnvironmentalActuationTasks()
	
	def sendActuatorCommand(self, data: ActuatorData) -> ActuatorData:
		"""
		Send an actuator command to the appropriate actuator task.
		
		This method validates the incoming actuation command and routes it to the
		appropriate actuator based on the type ID. It performs several validation
		checks before processing the command.
		
		@param data The ActuatorData command to process
		@return ActuatorData The response from the actuator, or None if invalid/failed
		"""
		# Validate input data and check if it's not a response
		if data and not data.isResponseFlagEnabled():
			# First check if the actuation event is destined for this device
			if data.getLocationID() == self.locationID:
				logging.info("Actuator command received for location ID %s. Processing...", 
					str(data.getLocationID()))
				
				aType = data.getTypeID()
				responseData = None
				
				# Route command to appropriate actuator based on type ID
				if aType == ConfigConst.HUMIDIFIER_ACTUATOR_TYPE and self.humidifierActuator:
					logging.debug("Processing humidifier actuator command")
					responseData = self.humidifierActuator.updateActuator(data)
				elif aType == ConfigConst.HVAC_ACTUATOR_TYPE and self.hvacActuator:
					logging.debug("Processing HVAC actuator command")
					responseData = self.hvacActuator.updateActuator(data)
				elif aType == ConfigConst.LED_DISPLAY_ACTUATOR_TYPE and self.ledDisplayActuator:
					logging.debug("Processing LED display actuator command")
					responseData = self.ledDisplayActuator.updateActuator(data)
				else:
					logging.warning("No valid actuator type or actuator not available. Ignoring actuation for type: %s", 
						data.getTypeID())
				
				# Log response status
				if responseData:
					logging.debug("Actuator command processed successfully. Response: %s", 
						responseData.getStatusCode())
				else:
					logging.warning("Actuator command processing failed or returned no response")
				
				# In a later lab module, the responseData instance will be
				# passed to a callback function implemented in DeviceDataManager
				# via IDataMessageListener
				return responseData
			else:
				logging.warning("Location ID doesn't match. Ignoring actuation: (me) %s != (you) %s", 
					str(self.locationID), str(data.getLocationID()))
		else:
			if not data:
				logging.warning("Actuator request received. Message is empty. Ignoring.")
			else:
				logging.warning("Actuator request received. Message is response flag enabled. Ignoring.")
		
		return None
	
	def setDataMessageListener(self, listener: IDataMessageListener) -> bool:
		"""
		Set the data message listener for processing actuator responses.
		
		@param listener The IDataMessageListener implementation
		@return bool True if listener was set successfully, False otherwise
		"""
		if listener:
			self.dataMsgListener = listener
			logging.info("Data message listener set successfully")
			return True
		else:
			logging.warning("Invalid data message listener provided")
		
		return False