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
from programmingtheiot.cda.sim.BaseActuatorSimTask import BaseActuatorSimTask
from pisense import SenseHAT

class LedDisplayEmulatorTask(BaseActuatorSimTask):
	"""
	Emulated LED display actuator task that interfaces with SenseHAT emulator.
	Controls the 8x8 LED matrix for displaying text and clearing the display.
	"""
	
	def __init__(self):
		"""
		Constructor for LedDisplayEmulatorTask.
		Initializes the LED display emulator with proper configuration.
		"""
		# Call superclass constructor with LED display actuator constants
		super(LedDisplayEmulatorTask, self).__init__(
			name = ConfigConst.LED_ACTUATOR_NAME,
			typeID = ConfigConst.LED_DISPLAY_ACTUATOR_TYPE,
			simpleName = "LED_Display")
		
		# Check configuration to determine if emulation should be enabled
		enableEmulation = ConfigUtil().getBoolean(
			ConfigConst.CONSTRAINED_DEVICE,
			ConfigConst.ENABLE_EMULATOR_KEY)
		
		# Initialize SenseHAT with emulation flag
		# If True: uses emulator, if False: attempts hardware connection
		self.sh = SenseHAT(emulate = enableEmulation)
	
	def _activateActuator(self, val: float = ConfigConst.DEFAULT_VAL, stateData: str = None) -> int:
		"""
		Activates the LED display by scrolling the provided state data text.
		If the command is 'ON', scroll the state data across the screen.
		
		@param val: Numeric value (not used for LED display)
		@param stateData: Text message to display on LED matrix
		@return int: 0 on success, -1 on error
		"""
		if self.sh.screen:
			# Use stateData if provided, otherwise use default message
			if stateData:
				displayText = stateData
			else:
				displayText = "LED ON"
			
			# Scroll the text across the LED matrix with font size 8
			self.sh.screen.scroll_text(displayText, size = 8)
			return 0
		else:
			logging.warning("No SenseHAT LED screen instance to write.")
			return -1
	
	def _deactivateActuator(self, val: float = ConfigConst.DEFAULT_VAL, stateData: str = None) -> int:
		"""
		Deactivates the LED display by clearing the screen.
		If the command is 'OFF', clear the LED display.
		
		@param val: Numeric value (not used)
		@param stateData: State data (not used for deactivation)
		@return int: 0 on success, -1 on error
		"""
		if self.sh.screen:
			# Clear the LED display immediately
			self.sh.screen.clear()
			return 0
		else:
			logging.warning("No SenseHAT LED screen instance to clear / close.")
			return -1