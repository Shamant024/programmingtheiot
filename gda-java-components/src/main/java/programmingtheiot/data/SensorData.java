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

import java.io.Serializable;

import programmingtheiot.common.ConfigConst;

/**
 * Sensor data container for storing sensor readings.
 * Contains a single float value representing the sensor measurement.
 * 
 * CRITICAL: Variable names MUST match CDA Python implementation for JSON compatibility.
 */
public class SensorData extends BaseIotData implements Serializable
{
	// static
	
	// Use the default serialVersionUID
	private static final long serialVersionUID = 1L;
	
	
	// private var's
	
	// CRITICAL: This variable name MUST match the Python CDA implementation exactly
	private float value = ConfigConst.DEFAULT_VAL;
	
	// Note: sensorType is inherited from BaseIotData's typeID property
	
    
	// constructors
	
	/**
	 * Default constructor.
	 * Initializes value to default.
	 */
	public SensorData()
	{
		super();
	}
	
	/**
	 * Constructor with sensor type.
	 * 
	 * @param sensorType The type of sensor (e.g., TEMP_SENSOR_TYPE, HUMIDITY_SENSOR_TYPE)
	 */
	public SensorData(int sensorType)
	{
		super();
		super.setTypeID(sensorType);
	}
	
	
	// public methods
	
	/**
	 * Returns the sensor value.
	 * 
	 * @return float The sensor measurement value
	 */
	public float getValue()
	{
		return this.value;
	}
	
	/**
	 * Sets the sensor value.
	 * Also updates the timestamp.
	 * 
	 * @param val The sensor value to set
	 */
	public void setValue(float val)
	{
		this.value = val;
		updateTimeStamp();
	}
	
	/**
	 * Returns a string representation of this instance. This will invoke the base class
	 * {@link #toString()} method, then append the output from this call.
	 * 
	 * @return String The string representing this instance, returned in CSV 'key=value' format.
	 */
	public String toString()
	{
		StringBuilder sb = new StringBuilder(super.toString());
		
		sb.append(',');
		sb.append(ConfigConst.VALUE_PROP).append('=').append(this.getValue());
		
		return sb.toString();
	}
	
	
	// protected methods
	
	/**
	 * Updates the current SensorData instance with data from another SensorData instance.
	 * This is called by the base class updateData() method.
	 * 
	 * @param data The BaseIotData instance to copy data from (must be SensorData)
	 */
	protected void handleUpdateData(BaseIotData data)
	{
		if (data instanceof SensorData) {
			SensorData sData = (SensorData) data;
			this.setValue(sData.getValue());
		}
	}
	
}