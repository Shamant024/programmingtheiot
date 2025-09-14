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
import java.lang.management.MemoryMXBean;

import java.lang.management.MemoryUsage;

import programmingtheiot.common.ConfigConst;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class SystemMemUtilTask extends BaseSystemUtilTask {
	// constructors

	/**
	 * Default.
	 * 
	 */
	public SystemMemUtilTask() {
		super(ConfigConst.MEM_UTIL_NAME, ConfigConst.MEM_UTIL_TYPE);
	}

	// public methods

	@Override
	public float getTelemetryValue() {
		MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
		long totalMem = memBean.getHeapMemoryUsage().getMax() +
				memBean.getNonHeapMemoryUsage().getMax();
		long usedMem = memBean.getHeapMemoryUsage().getUsed() +
				memBean.getNonHeapMemoryUsage().getUsed();

		return (float) ((usedMem * 100.0) / totalMem);
	}

}
