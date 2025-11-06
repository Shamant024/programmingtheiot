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
 * System performance data container for storing system metrics.
 * Contains CPU, disk, and memory utilization percentages.
 * 
 * CRITICAL: Variable names MUST match CDA Python implementation for JSON compatibility.
 */
public class SystemPerformanceData extends BaseIotData implements Serializable
{
	// static
	
	// Use the default serialVersionUID
	private static final long serialVersionUID = 1L;
	
	
	// private var's
	
	// CRITICAL: These variable names MUST match the Python CDA implementation exactly
	private float cpuUtil = ConfigConst.DEFAULT_VAL;
	private float diskUtil = ConfigConst.DEFAULT_VAL;
	private float memUtil = ConfigConst.DEFAULT_VAL;
	
    
	// constructors
	
	/**
	 * Default constructor.
	 * Initializes all utilization values to defaults and sets the name.
	 */
	public SystemPerformanceData()
	{
		super();
		super.setName(ConfigConst.SYS_PERF_DATA);
		super.setTypeID(ConfigConst.SYSTEM_PERF_TYPE);
	}
	
	
	// public methods
	
	/**
	 * Returns the CPU utilization percentage.
	 * 
	 * @return float The CPU utilization (0.0 - 100.0)
	 */
	public float getCpuUtilization()
	{
		return this.cpuUtil;
	}
	
	/**
	 * Returns the disk utilization percentage.
	 * 
	 * @return float The disk utilization (0.0 - 100.0)
	 */
	public float getDiskUtilization()
	{
		return this.diskUtil;
	}
	
	/**
	 * Returns the memory utilization percentage.
	 * 
	 * @return float The memory utilization (0.0 - 100.0)
	 */
	public float getMemoryUtilization()
	{
		return this.memUtil;
	}
	
	/**
	 * Sets the CPU utilization percentage.
	 * Also updates the timestamp.
	 * 
	 * @param val The CPU utilization value (0.0 - 100.0)
	 */
	public void setCpuUtilization(float val)
	{
		this.cpuUtil = val;
		updateTimeStamp();
	}
	
	/**
	 * Sets the disk utilization percentage.
	 * Also updates the timestamp.
	 * 
	 * @param val The disk utilization value (0.0 - 100.0)
	 */
	public void setDiskUtilization(float val)
	{
		this.diskUtil = val;
		updateTimeStamp();
	}
	
	/**
	 * Sets the memory utilization percentage.
	 * Also updates the timestamp.
	 * 
	 * @param val The memory utilization value (0.0 - 100.0)
	 */
	public void setMemoryUtilization(float val)
	{
		this.memUtil = val;
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
		sb.append(ConfigConst.CPU_UTIL_PROP).append('=').append(this.getCpuUtilization()).append(',');
		sb.append(ConfigConst.DISK_UTIL_PROP).append('=').append(this.getDiskUtilization()).append(',');
		sb.append(ConfigConst.MEM_UTIL_PROP).append('=').append(this.getMemoryUtilization());
		
		return sb.toString();
	}
	
	
	// protected methods
	
	/**
	 * Updates the current SystemPerformanceData instance with data from another 
	 * SystemPerformanceData instance.
	 * This is called by the base class updateData() method.
	 * 
	 * @param data The BaseIotData instance to copy data from (must be SystemPerformanceData)
	 */
	protected void handleUpdateData(BaseIotData data)
	{
		if (data instanceof SystemPerformanceData) {
			SystemPerformanceData spData = (SystemPerformanceData) data;
			
			this.setCpuUtilization(spData.getCpuUtilization());
			this.setDiskUtilization(spData.getDiskUtilization());
			this.setMemoryUtilization(spData.getMemoryUtilization());
		}
	}
	
}