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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.SystemPerformanceData;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class SystemPerformanceManager
{
	// private var's
	private static final Logger _Logger = Logger.getLogger(SystemPerformanceManager.class.getName());
    
    private ScheduledExecutorService schedExecSvc = null;
    private SystemCpuUtilTask sysCpuUtilTask = null;
    private SystemMemUtilTask sysMemUtilTask = null;
    private Runnable taskRunner = null;
    private boolean isStarted = false;
    private int pollRate = ConfigConst.DEFAULT_POLL_CYCLES;
	
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public SystemPerformanceManager()
	{
		ConfigUtil configUtil = ConfigUtil.getInstance();
        this.pollRate = configUtil.getInteger(
            ConfigConst.GATEWAY_DEVICE, 
            ConfigConst.POLL_CYCLES_KEY,
            ConfigConst.DEFAULT_POLL_CYCLES
        );

		this.sysCpuUtilTask = new SystemCpuUtilTask();
        this.sysMemUtilTask = new SystemMemUtilTask();
        this.schedExecSvc = Executors.newScheduledThreadPool(1);
        
        this.taskRunner = () -> {
            this.handleTelemetry();
        };
	}
	
	
	// public methods
	
	public void handleTelemetry()
	{
		float cpuUtil = this.sysCpuUtilTask.getTelemetryValue();
        float memUtil = this.sysMemUtilTask.getTelemetryValue();
        
        _Logger.info("CPU utilization: " + cpuUtil + "%, Mem utilization: " + memUtil + "%");
	}
	
	public void setDataMessageListener(IDataMessageListener listener)
	{
	}
	
	public void startManager()
	{
		if (!this.isStarted) {
            _Logger.info("Starting SystemPerformanceManager...");
            
            ScheduledFuture<?> futureTask = this.schedExecSvc.scheduleAtFixedRate(
                this.taskRunner, 1L, this.pollRate, TimeUnit.SECONDS
            );
            
            this.isStarted = true;
            _Logger.info("SystemPerformanceManager started");
        }
	}
	
	public void stopManager()
	{
		if (this.isStarted) {
            _Logger.info("Stopping SystemPerformanceManager...");
            
            if (this.schedExecSvc != null) {
                this.schedExecSvc.shutdown();
            }
            
            this.isStarted = false;
            _Logger.info("SystemPerformanceManager stopped");
        }
	}
	
}
