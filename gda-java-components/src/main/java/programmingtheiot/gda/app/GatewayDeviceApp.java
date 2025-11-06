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

package programmingtheiot.gda.app;

import org.apache.commons.cli.*;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main GDA application entry point.
 * 
 * Coordinates the startup and shutdown of all GDA subsystems through DeviceDataManager.
 * Supports command-line configuration and can run continuously or for a fixed duration.
 */
public class GatewayDeviceApp {
	// static

	private static final Logger _Logger = Logger.getLogger(GatewayDeviceApp.class.getName());

	public static final long DEFAULT_TEST_RUNTIME = 60000L;

	// private var's
	
	// CHANGED: Replaced SystemPerformanceManager with DeviceDataManager
	// SystemPerformanceManager is now managed by DeviceDataManager
	private DeviceDataManager dataMgr = null;

	private String configFile = ConfigConst.DEFAULT_CONFIG_FILE_NAME;

	// constructors

	/**
	 * Default constructor.
	 * Initializes the GDA and creates the DeviceDataManager instance.
	 */
	public GatewayDeviceApp() {
		super();

		_Logger.info("Initializing GDA...");

		// CHANGED: Create DeviceDataManager instead of SystemPerformanceManager
		// DeviceDataManager will handle SystemPerformanceManager and all other subsystems
		this.dataMgr = new DeviceDataManager();
		
		_Logger.info("GDA initialization complete.");
	}

	// static

	/**
	 * Main application entry point.
	 * 
	 * Parses command-line arguments, starts the application, and runs either
	 * continuously or for a fixed duration based on configuration.
	 * 
	 * @param args Command-line arguments (optional: -c <config_file_path>)
	 */
	public static void main(String[] args) {
		Map<String, String> argMap = parseArgs(args);

		// Set config file path if provided via command line
		if (argMap.containsKey(ConfigConst.CONFIG_FILE_KEY)) {
			System.setProperty(ConfigConst.CONFIG_FILE_KEY, argMap.get(ConfigConst.CONFIG_FILE_KEY));
		}

		GatewayDeviceApp gwApp = new GatewayDeviceApp();

		gwApp.startApp();

		// Check if should run forever or for fixed duration
		boolean runForever = ConfigUtil.getInstance().getBoolean(
			ConfigConst.GATEWAY_DEVICE,
			ConfigConst.ENABLE_RUN_FOREVER_KEY);

		if (runForever) {
			_Logger.info("GDA configured to run continuously.");
			
			try {
				while (true) {
					Thread.sleep(2000L);
				}
			} catch (InterruptedException e) {
				_Logger.warning("GDA main thread interrupted. Shutting down...");
			}

			gwApp.stopApp(0);
		} else {
			_Logger.info("GDA configured to run for " + DEFAULT_TEST_RUNTIME + " ms.");
			
			try {
				Thread.sleep(DEFAULT_TEST_RUNTIME);
			} catch (InterruptedException e) {
				_Logger.warning("GDA main thread interrupted. Shutting down...");
			}

			gwApp.stopApp(0);
		}
	}

	/**
	 * Parse any arguments passed in on app startup.
	 * 
	 * This method checks if any valid command line args are provided,
	 * including the name of the config file.
	 * 
	 * If any command line args conflict with the config file, the config file
	 * in-memory content should be overridden with the command line argument(s).
	 * 
	 * @param args The args array (may be null or empty)
	 * @return Map of parsed command-line arguments
	 */
	private static Map<String, String> parseArgs(String[] args) {
		// Store command line values in a map
		Map<String, String> argMap = new HashMap<String, String>();

		if (args != null && args.length > 0) {
			_Logger.info("Parsing " + args.length + " command line arguments...");
			
			// Create the parser and options - only need one for now ("c" for config file)
			CommandLineParser parser = new DefaultParser();
			Options options = new Options();

			options.addOption("c", true, "The relative or absolute path of the config file.");

			try {
				CommandLine cmdLineArgs = parser.parse(options, args);

				if (cmdLineArgs.hasOption("c")) {
					String configFilePath = cmdLineArgs.getOptionValue("c");
					argMap.put(ConfigConst.CONFIG_FILE_KEY, configFilePath);
					_Logger.info("Custom config file specified: " + configFilePath);
				} else {
					_Logger.info("No custom config file specified. Using default.");
				}
			} catch (ParseException e) {
				_Logger.warning("Failed to parse command line args. Ignoring - using defaults.");
			}
		} else {
			_Logger.info("No command line args to parse.");
		}

		return argMap;
	}

	// public methods

	/**
	 * Initializes and starts the GDA application.
	 * 
	 * Starts the DeviceDataManager, which in turn manages all subsystems:
	 * - SystemPerformanceManager
	 * - MQTT Client (future labs)
	 * - CoAP Server (future labs)
	 * - Cloud Client (future labs)
	 * - Persistence Client (optional)
	 */
	public void startApp() {
		_Logger.info("Starting GDA...");

		try {
			// CHANGED: Start DeviceDataManager instead of SystemPerformanceManager
			// DeviceDataManager will handle starting all subsystems
			if (this.dataMgr != null) {
				this.dataMgr.startManager();
			} else {
				_Logger.warning("DeviceDataManager is null. Cannot start GDA subsystems.");
			}

			_Logger.info("GDA started successfully.");
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to start GDA. Exiting.", e);

			stopApp(-1);
		}
	}

	/**
	 * Stops the GDA application.
	 * 
	 * Cleanly shuts down all subsystems through DeviceDataManager and exits
	 * the application with the specified exit code.
	 * 
	 * @param code The exit code to pass to {@link System#exit(int)}
	 */
	public void stopApp(int code) {
		_Logger.info("Stopping GDA...");

		try {
			// CHANGED: Stop DeviceDataManager instead of SystemPerformanceManager
			// DeviceDataManager will handle stopping all subsystems
			if (this.dataMgr != null) {
				this.dataMgr.stopManager();
			} else {
				_Logger.warning("DeviceDataManager is null. No subsystems to stop.");
			}

			_Logger.log(Level.INFO, "GDA stopped successfully with exit code {0}.", code);
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to cleanly stop GDA. Exiting.", e);
		}

		System.exit(code);
	}

	// private methods

}