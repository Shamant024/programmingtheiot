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
 * Actuator data container for commanding actuators and receiving responses.
 * Contains command type, value, state data, and response flag.
 * 
 * CRITICAL: Variable names MUST match CDA Python implementation for JSON compatibility.
 */
public class ActuatorData extends BaseIotData implements Serializable
{
	// static
	
	// Use the default serialVersionUID
	private static final long serialVersionUID = 1L;
	
	
	// private var's
	
	// CRITICAL: These variable names MUST match the Python CDA implementation exactly
	private int command = ConfigConst.DEFAULT_COMMAND;
	private float value = ConfigConst.DEFAULT_VAL;
	private boolean isResponse = false;
	private String stateData = "";
	
    
    
	// constructors
	
	/**
	 * Default constructor.
	 * Initializes all properties to default values.
	 */
	public ActuatorData()
	{
		super();
	}
	
	
	// public methods
	
	/**
	 * Returns the command value for this actuator data.
	 * 
	 * @return int The command value (e.g., ON, OFF, INCREASE, DECREASE)
	 */
	public int getCommand()
	{
		return this.command;
	}
	
	/**
	 * Returns the state data string for this actuator.
	 * Useful for sending text messages to displays, etc.
	 * 
	 * @return String The state data
	 */
	public String getStateData()
	{
		return this.stateData;
	}
	
	/**
	 * Returns the numeric value for this actuator data.
	 * 
	 * @return float The actuator value
	 */
	public float getValue()
	{
		return this.value;
	}
	
	/**
	 * Returns the response flag status.
	 * 
	 * @return boolean True if this is a response message, false if command
	 */
	public boolean isResponseFlagEnabled()
	{
		return this.isResponse;
	}
	
	/**
	 * Sets this ActuatorData instance as a response message.
	 * Also updates the timestamp.
	 */
	public void setAsResponse()
	{
		this.isResponse = true;
		updateTimeStamp();
	}
	
	/**
	 * Sets the command value for this actuator.
	 * Also updates the timestamp.
	 * 
	 * @param command The command value to set
	 */
	public void setCommand(int command)
	{
		this.command = command;
		updateTimeStamp();
	}
	
	/**
	 * Sets the state data string for this actuator.
	 * Also updates the timestamp.
	 * 
	 * @param stateData The state data string to set
	 */
	public void setStateData(String stateData)
	{
		if (stateData != null) {
			this.stateData = stateData;
		} else {
			this.stateData = "";
		}
		updateTimeStamp();
	}
	
	/**
	 * Sets the numeric value for this actuator.
	 * Also updates the timestamp.
	 * 
	 * @param val The value to set
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
		sb.append(ConfigConst.COMMAND_PROP).append('=').append(this.getCommand()).append(',');
		sb.append(ConfigConst.IS_RESPONSE_PROP).append('=').append(this.isResponseFlagEnabled()).append(',');
		sb.append(ConfigConst.VALUE_PROP).append('=').append(this.getValue());
		
		return sb.toString();
	}
	
	
	// protected methods
	
	/**
	 * Updates the current ActuatorData instance with data from another ActuatorData instance.
	 * This is called by the base class updateData() method.
	 * 
	 * @param data The BaseIotData instance to copy data from (must be ActuatorData)
	 */
	protected void handleUpdateData(BaseIotData data)
	{
		if (data instanceof ActuatorData) {
			ActuatorData aData = (ActuatorData) data;
			
			this.setCommand(aData.getCommand());
			this.setValue(aData.getValue());
			this.setStateData(aData.getStateData());
			
			if (aData.isResponseFlagEnabled()) {
				this.setAsResponse();
			}
		}
	}
	
}