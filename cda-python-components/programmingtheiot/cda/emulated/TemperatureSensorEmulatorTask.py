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

from programmingtheiot.data.SensorData import SensorData
import programmingtheiot.common.ConfigConst as ConfigConst
from programmingtheiot.common.ConfigUtil import ConfigUtil
from programmingtheiot.cda.sim.BaseSensorSimTask import BaseSensorSimTask
from pisense import SenseHAT

class TemperatureSensorEmulatorTask(BaseSensorSimTask):
	"""
	Emulated temperature sensor task that interfaces with SenseHAT emulator.
	Reads temperature data from the emulated environment.
	"""
	
	def __init__(self, dataSet = None):
		"""
		Constructor for TemperatureSensorEmulatorTask.
		
		@param dataSet: Optional dataset (not used in emulator mode)
		"""
		# Call superclass constructor with temperature sensor constants
		super(TemperatureSensorEmulatorTask, self).__init__(
			name = ConfigConst.TEMP_SENSOR_NAME,
			typeID = ConfigConst.TEMP_SENSOR_TYPE)
		
		# Check configuration to determine if emulation should be enabled
		enableEmulation = ConfigUtil().getBoolean(
			ConfigConst.CONSTRAINED_DEVICE,
			ConfigConst.ENABLE_EMULATOR_KEY)
		
		# Initialize SenseHAT with emulation flag
		# If True: uses emulator, if False: attempts hardware connection
		self.sh = SenseHAT(emulate = enableEmulation)
	
	def generateTelemetry(self) -> SensorData:
		"""
		Generates telemetry by reading temperature from SenseHAT emulator.
		
		@return SensorData: Sensor data containing temperature reading
		"""
		# Create new SensorData instance with proper name and type
		sensorData = SensorData(name = self.getName(), typeID = self.getTypeID())
		
		# Read temperature value from SenseHAT emulator environment
		sensorVal = self.sh.environ.temperature
		
		# Set the sensor value
		sensorData.setValue(sensorVal)
		
		# Update latest sensor data (required by base class)
		self.latestSensorData = sensorData
		
		return sensorData