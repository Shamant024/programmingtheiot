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

package programmingtheiot.gda.system;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import programmingtheiot.common.ConfigConst;

/**
 * Provides CPU utilization using the system MXBean.
 * Returns percentage value in the range [0.0, 100.0].
 */
public class SystemCpuUtilTask extends BaseSystemUtilTask
{
	// constructors
	
	/**
	 * Default.
	 */
	public SystemCpuUtilTask()
	{
		super(ConfigConst.CPU_UTIL_NAME, ConfigConst.CPU_UTIL_TYPE);
	}
	
	// public methods
	
	@Override
	public float getTelemetryValue()
	{
		// Cast the JVM's OperatingSystemMXBean to com.sun.management version
		OperatingSystemMXBean osBean = 
			(OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		
		if (osBean != null)
		{
			// getSystemCpuLoad returns a double in [0.0, 1.0]
			@SuppressWarnings("deprecation")
			double cpuLoad = osBean.getSystemCpuLoad();
			
			// If -1.0 is returned, the value is not available
			if (cpuLoad >= 0.0)
			{
				return (float) (cpuLoad * 100.0); // convert to percentage
			}
		}
		
		// Fallback if unsupported
		return 0.0f;
	}
}
