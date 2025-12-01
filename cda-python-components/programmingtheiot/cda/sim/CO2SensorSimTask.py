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
import math
import programmingtheiot.common.ConfigConst as ConfigConst
from programmingtheiot.cda.sim.BaseSensorSimTask import BaseSensorSimTask
from programmingtheiot.cda.sim.SensorDataGenerator import SensorDataGenerator
from programmingtheiot.data.SensorData import SensorData

class CO2SensorSimTask(BaseSensorSimTask):
	"""
	CO2 sensor simulator for Smart Office Environmental Management.
	
	Simulates CO2 levels in an office environment using a sinusoidal pattern
	to mimic occupancy patterns throughout the day:
	- Morning (8am-10am): Rising CO2 as people arrive
	- Midday (12pm-2pm): Peak CO2 during lunch meetings
	- Afternoon (3pm-5pm): Declining CO2 as people leave
	- Night (6pm-8am): Low baseline CO2 (empty office)
	
	Safe office CO2 levels: 400-1000 ppm
	Threshold for ventilation: >1000 ppm
	"""
	
	# CO2 level constants (in ppm - parts per million)
	MIN_CO2_LEVEL = 400.0    # Outdoor/baseline CO2 level
	NORMAL_CO2_LEVEL = 800.0 # Normal occupied office
	HIGH_CO2_LEVEL = 1200.0  # Poor ventilation, triggers alert
	MAX_CO2_LEVEL = 2000.0   # Maximum simulated value
	
	def __init__(self, dataSet = None):
		"""
		Constructor for CO2SensorSimTask.
		
		@param dataSet Optional pre-defined data set for simulation
		"""
		# Generate sinusoidal CO2 data mimicking office occupancy patterns
		if not dataSet:
			dataGenerator = SensorDataGenerator()
			
			# Generate daily pattern with higher CO2 during work hours (8am-6pm)
			dataSet = dataGenerator.generateDailySensorDataSet(
				curveType = SensorDataGenerator.BELL_CURVE,  # Bell curve for occupancy pattern
				noiseLevel = 15,  # Some randomness for realistic simulation
				minValue = self.MIN_CO2_LEVEL,
				maxValue = self.HIGH_CO2_LEVEL,
				startHour = 0,
				endHour = 24,
				useSeconds = False
			)
		
		super(CO2SensorSimTask, self).__init__(
			name = ConfigConst.CO2_SENSOR_NAME,
			typeID = ConfigConst.CO2_SENSOR_TYPE,
			dataSet = dataSet,
			minVal = self.MIN_CO2_LEVEL,
			maxVal = self.HIGH_CO2_LEVEL)
		
		logging.info("CO2 sensor simulator initialized with occupancy-based pattern.")