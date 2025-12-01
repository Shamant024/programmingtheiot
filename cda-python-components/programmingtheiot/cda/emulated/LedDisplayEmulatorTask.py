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

try:
	from pisense import SenseHAT
	SENSE_HAT_AVAILABLE = True
except:
	SENSE_HAT_AVAILABLE = False

class LedDisplayEmulatorTask(BaseActuatorSimTask):
	"""
	Emulated LED display actuator task that interfaces with SenseHAT emulator.
	Controls the 8x8 LED matrix for displaying text and clearing the display.
	
	Smart Office: Displays emergency alerts from cloud dashboard.
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
		
		# Initialize SenseHAT if available
		if SENSE_HAT_AVAILABLE:
			try:
				self.sh = SenseHAT(emulate = enableEmulation)
				logging.info("SenseHAT emulator initialized successfully for LED display.")
			except:
				self.sh = None
				logging.warning("SenseHAT emulator initialization failed. Using console output only.")
		else:
			self.sh = None
			logging.info("SenseHAT library not available. Using console output only.")
	
	def _activateActuator(self, val: float = ConfigConst.DEFAULT_VAL, stateData: str = None) -> int:
		"""
		Activates the LED display by scrolling the provided state data text.
		If the command is 'ON', scroll the state data across the screen.
		
		@param val: Numeric value (not used for LED display)
		@param stateData: Text message to display on LED matrix
		@return int: 0 on success, -1 on error
		"""
		# PROMINENT CONSOLE OUTPUT
		print("\n" + "=" * 60)
		print("" * 15)
		print("=" * 60)
		print("***************  LED DISPLAY ON  ***************")
		print("=" * 60)
		if stateData:
			print(f"MESSAGE: {stateData}")
		else:
			print("MESSAGE: EMERGENCY ALERT ACTIVATED!")
		print("=" * 60)
		print("" * 15)
		print("=" * 60 + "\n")
		
		logging.warning(" LED DISPLAY ACTIVATED - Cloud Command Received!")
		
		# Try to display on SenseHAT if available
		if self.sh and self.sh.screen:
			try:
				# Use stateData if provided, otherwise use default message
				if stateData:
					displayText = stateData
				else:
					displayText = "ALERT!"
				
				# Scroll the text across the LED matrix
				self.sh.screen.scroll_text(displayText, size = 8)
				logging.info("Text displayed on SenseHAT emulator screen.")
			except Exception as e:
				logging.warning(f"Failed to display on SenseHAT screen: {e}")
		
		return 0
	
	def _deactivateActuator(self, val: float = ConfigConst.DEFAULT_VAL, stateData: str = None) -> int:
		"""
		Deactivates the LED display by clearing the screen.
		If the command is 'OFF', clear the LED display.
		
		@param val: Numeric value (not used)
		@param stateData: State data (not used for deactivation)
		@return int: 0 on success, -1 on error
		"""
		# PROMINENT CONSOLE OUTPUT
		print("\n" + "-" * 60)
		print("" * 15)
		print("-" * 60)
		print("***************  LED DISPLAY OFF  ***************")
		print("-" * 60)
		print("MESSAGE: Emergency alert cleared - system normal")
		print("-" * 60)
		print("" * 15)
		print("-" * 60 + "\n")
		
		logging.info("✓ LED DISPLAY DEACTIVATED - Cloud Command Received!")
		
		# Try to clear SenseHAT if available
		if self.sh and self.sh.screen:
			try:
				# Clear the LED display
				self.sh.screen.clear()
				logging.info("SenseHAT emulator screen cleared.")
			except Exception as e:
				logging.warning(f"Failed to clear SenseHAT screen: {e}")
		
		return 0