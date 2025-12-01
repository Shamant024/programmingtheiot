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
from programmingtheiot.data.ActuatorData import ActuatorData
from programmingtheiot.cda.sim.BaseActuatorSimTask import BaseActuatorSimTask

class VentilationActuatorSimTask(BaseActuatorSimTask):
	"""
	Ventilation system actuator for Smart Office Environmental Management.
	
	Controls office ventilation based on CO2 levels and temperature.
	Activates when:
	- CO2 > 1000 ppm (poor air quality)
	- Temperature > 26°C (too hot)
	"""
	def __init__(self):
		"""
		Constructor for VentilationActuatorSimTask.
		
		Initializes the ventilation system actuator with appropriate configuration constants.
		"""
		super(VentilationActuatorSimTask, self).__init__(
			name = ConfigConst.VENTILATION_ACTUATOR_NAME,
			typeID = ConfigConst.VENTILATION_ACTUATOR_TYPE,
			simpleName = "VENTILATION")