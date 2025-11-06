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

import json
import logging
from decimal import Decimal
from json import JSONEncoder

from programmingtheiot.data.ActuatorData import ActuatorData
from programmingtheiot.data.SensorData import SensorData
from programmingtheiot.data.SystemPerformanceData import SystemPerformanceData

class DataUtil():
	"""
	Utility class for converting IoT data objects to/from JSON format.
	Supports ActuatorData, SensorData, and SystemPerformanceData conversions.
	"""

	def __init__(self, encodeToUtf8 = False):
		"""
		Constructor for DataUtil.
		
		@param encodeToUtf8 If True, encode JSON to UTF-8 bytes; otherwise, return string
		"""
		self.encodeToUtf8 = encodeToUtf8
		logging.info("Created DataUtil instance.")
	
	def actuatorDataToJson(self, data: ActuatorData = None, useDecForFloat: bool = False):
		"""
		Converts ActuatorData object to JSON string.
		
		@param data The ActuatorData object to convert
		@param useDecForFloat If True, use Decimal for float values
		@return JSON string representation of ActuatorData, or empty string if data is None
		"""
		if not data:
			logging.debug("ActuatorData is null. Returning empty string.")
			return ""
		
		jsonData = self._generateJsonData(obj = data, useDecForFloat = useDecForFloat)
		return jsonData
	
	def sensorDataToJson(self, data: SensorData = None, useDecForFloat: bool = False):
		"""
		Converts SensorData object to JSON string.
		
		@param data The SensorData object to convert
		@param useDecForFloat If True, use Decimal for float values
		@return JSON string representation of SensorData, or empty string if data is None
		"""
		if not data:
			logging.debug("SensorData is null. Returning empty string.")
			return ""
		
		jsonData = self._generateJsonData(obj = data, useDecForFloat = useDecForFloat)
		return jsonData

	def systemPerformanceDataToJson(self, data: SystemPerformanceData = None, useDecForFloat: bool = False):
		"""
		Converts SystemPerformanceData object to JSON string.
		
		@param data The SystemPerformanceData object to convert
		@param useDecForFloat If True, use Decimal for float values
		@return JSON string representation of SystemPerformanceData, or empty string if data is None
		"""
		if not data:
			logging.debug("SystemPerformanceData is null. Returning empty string.")
			return ""
		
		jsonData = self._generateJsonData(obj = data, useDecForFloat = useDecForFloat)
		return jsonData
	
	def jsonToActuatorData(self, jsonData: str = None, useDecForFloat: bool = False):
		"""
		Converts JSON string to ActuatorData object.
		
		@param jsonData The JSON string to convert
		@param useDecForFloat If True, parse float values as Decimal
		@return ActuatorData object, or None if jsonData is empty/null
		"""
		if not jsonData:
			logging.warning("JSON data is empty or null. Returning null.")
			return None
		
		jsonStruct = self._formatDataAndLoadDictionary(jsonData, useDecForFloat = useDecForFloat)
		ad = ActuatorData()
		self._updateIotData(jsonStruct, ad)
		
		return ad
	
	def jsonToSensorData(self, jsonData: str = None, useDecForFloat: bool = False):
		"""
		Converts JSON string to SensorData object.
		
		@param jsonData The JSON string to convert
		@param useDecForFloat If True, parse float values as Decimal
		@return SensorData object, or None if jsonData is empty/null
		"""
		if not jsonData:
			logging.warning("JSON data is empty or null. Returning null.")
			return None
		
		jsonStruct = self._formatDataAndLoadDictionary(jsonData, useDecForFloat = useDecForFloat)
		sd = SensorData()
		self._updateIotData(jsonStruct, sd)
		
		return sd
	
	def jsonToSystemPerformanceData(self, jsonData: str = None, useDecForFloat: bool = False):
		"""
		Converts JSON string to SystemPerformanceData object.
		
		@param jsonData The JSON string to convert
		@param useDecForFloat If True, parse float values as Decimal
		@return SystemPerformanceData object, or None if jsonData is empty/null
		"""
		if not jsonData:
			logging.warning("JSON data is empty or null. Returning null.")
			return None
		
		jsonStruct = self._formatDataAndLoadDictionary(jsonData, useDecForFloat = useDecForFloat)
		spd = SystemPerformanceData()
		self._updateIotData(jsonStruct, spd)
		
		return spd
	
	def _formatDataAndLoadDictionary(self, jsonData: str, useDecForFloat: bool = False) -> dict:
		"""
		Formats JSON string and converts it to a Python dictionary.
		Replaces single quotes with double quotes and Python boolean strings with JSON boolean strings.
		
		@param jsonData The JSON string to format and parse
		@param useDecForFloat If True, parse float values as Decimal
		@return Dictionary representation of the JSON data
		"""
		# Replace Python-style strings with JSON-compatible strings
		jsonData = jsonData.replace("\'", "\"").replace('False', 'false').replace('True', 'true')
		
		jsonStruct = None
		
		if useDecForFloat:
			jsonStruct = json.loads(jsonData, parse_float = Decimal)
		else:
			jsonStruct = json.loads(jsonData)
		
		return jsonStruct
	
	def _generateJsonData(self, obj, useDecForFloat: bool = False) -> str:
		"""
		Generates JSON string from a Python object using JsonDataEncoder.
		
		@param obj The object to convert to JSON
		@param useDecForFloat If True, use Decimal for float values (not yet implemented)
		@return JSON string representation of the object
		"""
		jsonData = None
		
		if self.encodeToUtf8:
			# Encode to UTF-8 bytes
			jsonData = json.dumps(obj, cls = JsonDataEncoder).encode('utf8')
		else:
			# Return formatted JSON string with indentation
			jsonData = json.dumps(obj, cls = JsonDataEncoder, indent = 4)
		
		if jsonData:
			# Replace Python-style strings with JSON-compatible strings
			jsonData = jsonData.replace("\'", "\"").replace('False', 'false').replace('True', 'true')
		
		return jsonData
	
	def _updateIotData(self, jsonStruct, obj):
		"""
		Updates an IoT data object's attributes from a JSON structure (dictionary).
		Only updates attributes that exist in the object.
		
		@param jsonStruct Dictionary containing the JSON data
		@param obj The IoT data object to update (ActuatorData, SensorData, or SystemPerformanceData)
		"""
		# Get the object's attributes as a dictionary
		varStruct = vars(obj)
		
		# Iterate through JSON keys and update object attributes
		for key in jsonStruct:
			if key in varStruct:
				setattr(obj, key, jsonStruct[key])
				logging.debug("JSON data contains key mappable to object: %s", key)
			else:
				logging.warning("JSON data contains key not mappable to object: %s", key)

	
class JsonDataEncoder(JSONEncoder):
	"""
	Convenience class to facilitate JSON encoding of an object that
	can be converted to a dict.
	"""
	def default(self, o):
		"""
		Override the default method to convert objects to dictionaries.
		
		@param o The object to encode
		@return Dictionary representation of the object using __dict__
		"""
		return o.__dict__