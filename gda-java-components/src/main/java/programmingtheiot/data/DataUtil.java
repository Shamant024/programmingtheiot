/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * You may find it more helpful to your design to adjust the
 * functionality, constants and interfaces (if there are any)
 * provided within in order to meet the needs of your specific
 * Programming the Internet of Things project.
 */

package programmingtheiot.data;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;

import programmingtheiot.common.ConfigConst;

/**
 * Singleton utility class for converting IoT data objects to/from JSON format.
 * Uses Google's Gson library for JSON serialization and deserialization.
 * 
 * Supports conversion for:
 * - ActuatorData
 * - SensorData
 * - SystemPerformanceData
 * - SystemStateData (optional)
 */
public class DataUtil
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(DataUtil.class.getName());
	
	private static final DataUtil _Instance = new DataUtil();

	/**
	 * Returns the Singleton instance of this class.
	 * 
	 * @return DataUtil The singleton instance
	 */
	public static final DataUtil getInstance()
	{
		return _Instance;
	}
	
	
	// private var's
	
	
	// constructors
	
	/**
	 * Default (private) constructor.
	 * Initializes the DataUtil instance.
	 */
	private DataUtil()
	{
		super();
		_Logger.info("DataUtil instance created.");
	}
	
	
	// public methods
	
	/**
	 * Converts an ActuatorData object to a JSON string.
	 * 
	 * @param actuatorData The ActuatorData instance to convert
	 * @return String JSON representation of the ActuatorData, or null if input is null
	 */
	public String actuatorDataToJson(ActuatorData actuatorData)
	{
		String jsonData = null;
		
		if (actuatorData != null) {
			_Logger.fine("Converting ActuatorData to JSON: " + actuatorData.getName());
			
			Gson gson = new Gson();
			jsonData = gson.toJson(actuatorData);
			
			_Logger.fine("ActuatorData converted to JSON: " + jsonData);
		} else {
			_Logger.warning("ActuatorData is null. Returning null JSON string.");
		}
		
		return jsonData;
	}
	
	/**
	 * Converts a SensorData object to a JSON string.
	 * 
	 * @param sensorData The SensorData instance to convert
	 * @return String JSON representation of the SensorData, or null if input is null
	 */
	public String sensorDataToJson(SensorData sensorData)
	{
		String jsonData = null;
		
		if (sensorData != null) {
			_Logger.fine("Converting SensorData to JSON: " + sensorData.getName());
			
			Gson gson = new Gson();
			jsonData = gson.toJson(sensorData);
			
			_Logger.fine("SensorData converted to JSON: " + jsonData);
		} else {
			_Logger.warning("SensorData is null. Returning null JSON string.");
		}
		
		return jsonData;
	}
	
	/**
	 * Converts a SystemPerformanceData object to a JSON string.
	 * 
	 * @param sysPerfData The SystemPerformanceData instance to convert
	 * @return String JSON representation of the SystemPerformanceData, or null if input is null
	 */
	public String systemPerformanceDataToJson(SystemPerformanceData sysPerfData)
	{
		String jsonData = null;
		
		if (sysPerfData != null) {
			_Logger.fine("Converting SystemPerformanceData to JSON: " + sysPerfData.getName());
			
			Gson gson = new Gson();
			jsonData = gson.toJson(sysPerfData);
			
			_Logger.fine("SystemPerformanceData converted to JSON: " + jsonData);
		} else {
			_Logger.warning("SystemPerformanceData is null. Returning null JSON string.");
		}
		
		return jsonData;
	}
	
	/**
	 * Converts a SystemStateData object to a JSON string.
	 * OPTIONAL: Only implement if you have created SystemStateData class.
	 * 
	 * @param sysStateData The SystemStateData instance to convert
	 * @return String JSON representation of the SystemStateData, or null if input is null
	 */
	public String systemStateDataToJson(SystemStateData sysStateData)
	{
		String jsonData = null;
		
		if (sysStateData != null) {
			_Logger.fine("Converting SystemStateData to JSON: " + sysStateData.getName());
			
			Gson gson = new Gson();
			jsonData = gson.toJson(sysStateData);
			
			_Logger.fine("SystemStateData converted to JSON: " + jsonData);
		} else {
			_Logger.warning("SystemStateData is null. Returning null JSON string.");
		}
		
		return jsonData;
	}
	
	/**
	 * Converts a JSON string to an ActuatorData object.
	 * 
	 * @param jsonData The JSON string to convert
	 * @return ActuatorData The deserialized ActuatorData instance, or null if input is null/empty
	 */
	public ActuatorData jsonToActuatorData(String jsonData)
	{
		ActuatorData data = null;
		
		if (jsonData != null && jsonData.trim().length() > 0) {
			_Logger.fine("Converting JSON to ActuatorData: " + jsonData);
			
			Gson gson = new Gson();
			data = gson.fromJson(jsonData, ActuatorData.class);
			
			_Logger.fine("JSON converted to ActuatorData: " + data.getName());
		} else {
			_Logger.warning("JSON data is null or empty. Returning null ActuatorData.");
		}
		
		return data;
	}
	
	/**
	 * Converts a JSON string to a SensorData object.
	 * 
	 * @param jsonData The JSON string to convert
	 * @return SensorData The deserialized SensorData instance, or null if input is null/empty
	 */
	public SensorData jsonToSensorData(String jsonData)
	{
		SensorData data = null;
		
		if (jsonData != null && jsonData.trim().length() > 0) {
			_Logger.fine("Converting JSON to SensorData: " + jsonData);
			
			Gson gson = new Gson();
			data = gson.fromJson(jsonData, SensorData.class);
			
			_Logger.fine("JSON converted to SensorData: " + data.getName());
		} else {
			_Logger.warning("JSON data is null or empty. Returning null SensorData.");
		}
		
		return data;
	}
	
	/**
	 * Converts a JSON string to a SystemPerformanceData object.
	 * 
	 * @param jsonData The JSON string to convert
	 * @return SystemPerformanceData The deserialized SystemPerformanceData instance, or null if input is null/empty
	 */
	public SystemPerformanceData jsonToSystemPerformanceData(String jsonData)
	{
		SystemPerformanceData data = null;
		
		if (jsonData != null && jsonData.trim().length() > 0) {
			_Logger.fine("Converting JSON to SystemPerformanceData: " + jsonData);
			
			Gson gson = new Gson();
			data = gson.fromJson(jsonData, SystemPerformanceData.class);
			
			_Logger.fine("JSON converted to SystemPerformanceData: " + data.getName());
		} else {
			_Logger.warning("JSON data is null or empty. Returning null SystemPerformanceData.");
		}
		
		return data;
	}
	
	/**
	 * Converts a JSON string to a SystemStateData object.
	 * OPTIONAL: Only implement if you have created SystemStateData class.
	 * 
	 * @param jsonData The JSON string to convert
	 * @return SystemStateData The deserialized SystemStateData instance, or null if input is null/empty
	 */
	public SystemStateData jsonToSystemStateData(String jsonData)
	{
		SystemStateData data = null;
		
		if (jsonData != null && jsonData.trim().length() > 0) {
			_Logger.fine("Converting JSON to SystemStateData: " + jsonData);
			
			Gson gson = new Gson();
			data = gson.fromJson(jsonData, SystemStateData.class);
			
			_Logger.fine("JSON converted to SystemStateData: " + data.getName());
		} else {
			_Logger.warning("JSON data is null or empty. Returning null SystemStateData.");
		}
		
		return data;
	}
	
}