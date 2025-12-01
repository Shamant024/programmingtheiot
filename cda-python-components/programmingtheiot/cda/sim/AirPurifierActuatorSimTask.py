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

import programmingtheiot.common.ConfigConst as ConfigConst
from programmingtheiot.data.ActuatorData import ActuatorData
from programmingtheiot.cda.sim.BaseActuatorSimTask import BaseActuatorSimTask

class AirPurifierActuatorSimTask(BaseActuatorSimTask):
	"""
	Air purifier actuator for Smart Office Environmental Management.
	
	Controls air purification based on humidity levels.
	Activates when:
	- Humidity > 60% (too humid - mold risk)
	"""
	def __init__(self):
		"""
		Constructor for AirPurifierActuatorSimTask.
		
		Initializes the air purifier actuator with appropriate configuration constants.
		"""
		super(AirPurifierActuatorSimTask, self).__init__(
			name = ConfigConst.AIR_PURIFIER_ACTUATOR_NAME,
			typeID = ConfigConst.AIR_PURIFIER_ACTUATOR_TYPE,
			simpleName = "AIR_PURIFIER")