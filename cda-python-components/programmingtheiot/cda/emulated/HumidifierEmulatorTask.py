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
from time import sleep
import programmingtheiot.common.ConfigConst as ConfigConst
from programmingtheiot.common.ConfigUtil import ConfigUtil
from programmingtheiot.cda.sim.BaseActuatorSimTask import BaseActuatorSimTask
from pisense import SenseHAT

class HumidifierEmulatorTask(BaseActuatorSimTask):
	"""
	Emulated humidifier actuator task that interfaces with SenseHAT emulator.
	Controls LED display to show humidifier status and values.
	"""
	
	def __init__(self):
		"""
		Constructor for HumidifierEmulatorTask.
		"""
		# Call superclass constructor with humidifier actuator constants
		super(HumidifierEmulatorTask, self).__init__(
			name = ConfigConst.HUMIDIFIER_ACTUATOR_NAME,
			typeID = ConfigConst.HUMIDIFIER_ACTUATOR_TYPE,
			simpleName = "HUMIDIFIER")
		
		# Check configuration to determine if emulation should be enabled
		enableEmulation = ConfigUtil().getBoolean(
			ConfigConst.CONSTRAINED_DEVICE,
			ConfigConst.ENABLE_EMULATOR_KEY)
		
		# Initialize SenseHAT with emulation flag
		# If True: uses emulator, if False: attempts hardware connection
		self.sh = SenseHAT(emulate = enableEmulation)
	
	def _activateActuator(self, val: float = ConfigConst.DEFAULT_VAL, stateData: str = None) -> int:
		"""
		Activates the humidifier by displaying activation message on SenseHAT LED matrix.
		
		@param val: The target humidity level
		@param stateData: Optional state data
		@return int: 0 on success, -1 on error
		"""
		if self.sh.screen:
			# Create activation message with value
			msg = self.getSimpleName() + ' ON: ' + str(val) + '%'
			
			# Scroll the message across the LED matrix
			self.sh.screen.scroll_text(msg)
			
			return 0
		else:
			logging.warning("No SenseHAT LED screen instance to write.")
			return -1
	
	def _deactivateActuator(self, val: float = ConfigConst.DEFAULT_VAL, stateData: str = None) -> int:
		"""
		Deactivates the humidifier by displaying deactivation message and clearing screen.
		
		@param val: The humidity level (not used in deactivation)
		@param stateData: Optional state data
		@return int: 0 on success, -1 on error
		"""
		if self.sh.screen:
			# Create deactivation message
			msg = self.getSimpleName() + ' OFF'
			
			# Scroll the message across the LED matrix
			self.sh.screen.scroll_text(msg)
			
			# Optional sleep to allow message to scroll before clearing
			sleep(5)
			
			# Clear the LED display
			self.sh.screen.clear()
			
			return 0
		else:
			logging.warning("No SenseHAT LED screen instance to clear / close.")
			return -1