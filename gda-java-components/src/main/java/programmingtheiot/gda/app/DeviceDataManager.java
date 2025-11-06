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

import java.util.logging.Level;
import java.util.logging.Logger;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;

import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

import programmingtheiot.gda.connection.CloudClientConnector;
import programmingtheiot.gda.connection.CoapServerGateway;
import programmingtheiot.gda.connection.IPersistenceClient;
import programmingtheiot.gda.connection.IPubSubClient;
import programmingtheiot.gda.connection.IRequestResponseClient;
import programmingtheiot.gda.connection.MqttClientConnector;
import programmingtheiot.gda.connection.RedisPersistenceAdapter;
import programmingtheiot.gda.connection.SmtpClientConnector;

import programmingtheiot.gda.system.SystemPerformanceManager;

/**
 * Central data management hub for the Gateway Device Application (GDA).
 * 
 * Implements IDataMessageListener to handle incoming data from various sources:
 * - SystemPerformanceManager (local system metrics)
 * - MQTT Client (messages from CDA)
 * - CoAP Server (messages from CDA)
 * - Cloud services
 * 
 * Coordinates data flow between components and manages lifecycle of all subsystems.
 */
public class DeviceDataManager implements IDataMessageListener
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(DeviceDataManager.class.getName());
	
	// private var's
	
	// Configuration flags for enabling/disabling various subsystems
	private boolean enableMqttClient = true;
	private boolean enableCoapServer = false;
	private boolean enableCloudClient = false;
	private boolean enableSmtpClient = false;
	private boolean enablePersistenceClient = false;
	private boolean enableSystemPerf = true;
	
	// Manager and connector references
	private SystemPerformanceManager sysPerfMgr = null;
	private IActuatorDataListener actuatorDataListener = null;
	private IPubSubClient mqttClient = null;
	private IPubSubClient cloudClient = null;
	private IPersistenceClient persistenceClient = null;
	private IRequestResponseClient smtpClient = null;
	private CoapServerGateway coapServer = null;
	
	// constructors
	
	/**
	 * Default constructor.
	 * Uses default configuration for all subsystems.
	 */
	public DeviceDataManager()
	{
		super();
		
		_Logger.info("Initializing DeviceDataManager...");
		
		initConnections();
		
		_Logger.info("DeviceDataManager initialization complete.");
	}
	
	/**
	 * Constructor with configuration parameters.
	 * Allows custom enable/disable of subsystems.
	 * 
	 * @param enableMqttClient Enable MQTT client connectivity
	 * @param enableCoapClient Enable CoAP server connectivity
	 * @param enableCloudClient Enable cloud service connectivity
	 * @param enableSmtpClient Enable SMTP email notifications
	 * @param enablePersistenceClient Enable Redis persistence
	 */
	public DeviceDataManager(
		boolean enableMqttClient,
		boolean enableCoapClient,
		boolean enableCloudClient,
		boolean enableSmtpClient,
		boolean enablePersistenceClient)
	{
		super();
		
		_Logger.info("Initializing DeviceDataManager with custom configuration...");
		
		this.enableMqttClient = enableMqttClient;
		this.enableCoapServer = enableCoapClient;
		this.enableCloudClient = enableCloudClient;
		this.enableSmtpClient = enableSmtpClient;
		this.enablePersistenceClient = enablePersistenceClient;
		
		initConnections();
		
		_Logger.info("DeviceDataManager initialization complete.");
	}
	
	
	// public methods
	
	/**
	 * Handles actuator command responses from the CDA.
	 * These are responses to commands previously sent to actuators.
	 * 
	 * @param resourceName The resource that generated the response
	 * @param data The actuator response data
	 * @return boolean True if handled successfully, false otherwise
	 */
	@Override
	public boolean handleActuatorCommandResponse(ResourceNameEnum resourceName, ActuatorData data)
	{
		if (data != null) {
			_Logger.info("Handling actuator response from CDA: " + data.getName());
			_Logger.fine("Actuator response data: " + data.toString());
			
			// Log the response for tracking
			if (data.hasError()) {
				_Logger.warning("Actuator response has error flag set: " + data.getStatusCode());
			}
			
			// In Lab Module 05, we just log the response
			// Future labs will add persistence and cloud forwarding
			
			return true;
		} else {
			_Logger.warning("Received null ActuatorData response. Ignoring.");
			return false;
		}
	}

	/**
	 * Handles actuator command requests (typically from cloud or internal logic).
	 * These are commands to be sent to actuators on the CDA.
	 * 
	 * @param resourceName The target resource for the command
	 * @param data The actuator command data
	 * @return boolean True if handled successfully, false otherwise
	 */
	@Override
	public boolean handleActuatorCommandRequest(ResourceNameEnum resourceName, ActuatorData data)
	{
		if (data != null) {
			_Logger.info("Handling actuator command request: " + data.getName());
			_Logger.fine("Actuator command: " + data.getCommand() + ", Value: " + data.getValue());
			
			// In Lab Module 05, we just log the command
			// Future labs will forward this to CDA via MQTT/CoAP
			
			return true;
		} else {
			_Logger.warning("Received null ActuatorData request. Ignoring.");
			return false;
		}
	}

	/**
	 * Handles incoming messages in raw string format.
	 * Typically used for MQTT/CoAP message callbacks.
	 * 
	 * @param resourceName The resource that sent the message
	 * @param msg The message payload (usually JSON)
	 * @return boolean True if handled successfully, false otherwise
	 */
	@Override
	public boolean handleIncomingMessage(ResourceNameEnum resourceName, String msg)
	{
		if (msg != null && msg.trim().length() > 0) {
			_Logger.info("Handling incoming message from resource: " + resourceName.getResourceName());
			_Logger.fine("Message payload: " + msg);
			
			// In Lab Module 05, we just log the message
			// Future labs will parse JSON and route to appropriate handlers
			
			return true;
		} else {
			_Logger.warning("Received empty or null message. Ignoring.");
			return false;
		}
	}

	/**
	 * Handles sensor data messages from the CDA.
	 * 
	 * @param resourceName The resource that sent the sensor data
	 * @param data The sensor data
	 * @return boolean True if handled successfully, false otherwise
	 */
	@Override
	public boolean handleSensorMessage(ResourceNameEnum resourceName, SensorData data)
	{
		if (data != null) {
			_Logger.info("Handling sensor message from CDA: " + data.getName());
			_Logger.fine("Sensor data - Type: " + data.getTypeID() + ", Value: " + data.getValue());
			
			// Convert to JSON for logging/transmission
			String jsonData = DataUtil.getInstance().sensorDataToJson(data);
			_Logger.fine("SensorData as JSON: " + jsonData);
			
			// Check for error conditions
			if (data.hasError()) {
				_Logger.warning("Sensor data has error flag set: " + data.getStatusCode());
			}
			
			// In Lab Module 05, we just process and log
			// Future labs will add:
			// - Persistence to Redis
			// - Forwarding to cloud services
			// - Data analysis and actuation triggers
			
			return true;
		} else {
			_Logger.warning("Received null SensorData. Ignoring.");
			return false;
		}
	}

	/**
	 * Handles system performance data messages.
	 * Can come from local SystemPerformanceManager or remote CDA.
	 * 
	 * @param resourceName The resource that sent the performance data
	 * @param data The system performance data
	 * @return boolean True if handled successfully, false otherwise
	 */
	@Override
	public boolean handleSystemPerformanceMessage(ResourceNameEnum resourceName, SystemPerformanceData data)
	{
		if (data != null) {
			_Logger.info("Handling system performance message: " + data.getName());
			_Logger.fine(
				String.format("System Performance - CPU: %.2f%%, Memory: %.2f%%, Disk: %.2f%%",
					data.getCpuUtilization(),
					data.getMemoryUtilization(),
					data.getDiskUtilization())
			);
			
			// Convert to JSON for logging/transmission
			String jsonData = DataUtil.getInstance().systemPerformanceDataToJson(data);
			_Logger.fine("SystemPerformanceData as JSON: " + jsonData);
			
			// Check for error conditions
			if (data.hasError()) {
				_Logger.warning("System performance data has error flag set: " + data.getStatusCode());
			}
			
			// In Lab Module 05, we just process and log
			// Future labs will add:
			// - Persistence to Redis
			// - Forwarding to cloud services
			// - Performance threshold monitoring
			
			return true;
		} else {
			_Logger.warning("Received null SystemPerformanceData. Ignoring.");
			return false;
		}
	}
	
	/**
	 * Sets the actuator data listener for handling actuator events.
	 * 
	 * @param name The name/identifier for this listener
	 * @param listener The listener implementation
	 */
	public void setActuatorDataListener(String name, IActuatorDataListener listener)
	{
		if (listener != null) {
			_Logger.info("Setting actuator data listener: " + name);
			this.actuatorDataListener = listener;
		} else {
			_Logger.warning("Attempted to set null actuator data listener. Ignoring.");
		}
	}
	
	/**
	 * Starts the DeviceDataManager and all enabled subsystems.
	 * Should be called after construction to begin operations.
	 */
	public void startManager()
	{
		_Logger.info("Starting DeviceDataManager...");
		
		// Start SystemPerformanceManager if enabled
		if (this.sysPerfMgr != null) {
			_Logger.info("Starting SystemPerformanceManager...");
			this.sysPerfMgr.startManager();
		}
		
		// Start MQTT client if enabled
		if (this.mqttClient != null) {
			_Logger.info("Starting MQTT client connector...");
			// Will be implemented in Lab Module 06
			// this.mqttClient.connectClient();
		}
		
		// Start CoAP server if enabled
		if (this.coapServer != null) {
			_Logger.info("Starting CoAP server gateway...");
			// Will be implemented in Lab Module 08
			// this.coapServer.startServer();
		}
		
		// Start persistence client if enabled
		if (this.persistenceClient != null) {
			_Logger.info("Starting persistence client...");
			// Will be implemented in Lab Module 05 (optional)
			// this.persistenceClient.connectClient();
		}
		
		// Start cloud client if enabled
		if (this.cloudClient != null) {
			_Logger.info("Starting cloud client connector...");
			// Will be implemented in Lab Module 11
			// this.cloudClient.connectClient();
		}
		
		_Logger.info("DeviceDataManager started successfully.");
	}
	
	/**
	 * Stops the DeviceDataManager and all enabled subsystems.
	 * Should be called before application shutdown for clean cleanup.
	 */
	public void stopManager()
	{
		_Logger.info("Stopping DeviceDataManager...");
		
		// Stop SystemPerformanceManager if running
		if (this.sysPerfMgr != null) {
			_Logger.info("Stopping SystemPerformanceManager...");
			this.sysPerfMgr.stopManager();
		}
		
		// Stop MQTT client if connected
		if (this.mqttClient != null) {
			_Logger.info("Stopping MQTT client connector...");
			// Will be implemented in Lab Module 06
			// this.mqttClient.disconnectClient();
		}
		
		// Stop CoAP server if running
		if (this.coapServer != null) {
			_Logger.info("Stopping CoAP server gateway...");
			// Will be implemented in Lab Module 08
			// this.coapServer.stopServer();
		}
		
		// Stop persistence client if connected
		if (this.persistenceClient != null) {
			_Logger.info("Stopping persistence client...");
			// Will be implemented in Lab Module 05 (optional)
			// this.persistenceClient.disconnectClient();
		}
		
		// Stop cloud client if connected
		if (this.cloudClient != null) {
			_Logger.info("Stopping cloud client connector...");
			// Will be implemented in Lab Module 11
			// this.cloudClient.disconnectClient();
		}
		
		_Logger.info("DeviceDataManager stopped successfully.");
	}

	
	// private methods
	
	/**
	 * Initializes the enabled connections. This will NOT start them, but only create the
	 * instances that will be used in the {@link #startManager() and #stopManager()) methods.
	 */
	private void initConnections()
	{
		_Logger.info("Initializing connection subsystems...");
		
		// Load configuration
		ConfigUtil configUtil = ConfigUtil.getInstance();
		
		// Read system performance enable flag from config
		this.enableSystemPerf = 
			configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE, 
				ConfigConst.ENABLE_SYSTEM_PERF_KEY);
		
		// Initialize SystemPerformanceManager if enabled
		if (this.enableSystemPerf) {
			_Logger.info("System performance monitoring enabled. Creating SystemPerformanceManager...");
			this.sysPerfMgr = new SystemPerformanceManager();
			
			// CRITICAL: Set this DeviceDataManager as the listener for callbacks
			this.sysPerfMgr.setDataMessageListener(this);
			
			_Logger.info("SystemPerformanceManager created and listener registered.");
		} else {
			_Logger.info("System performance monitoring disabled by configuration.");
		}
		
		// Initialize MQTT client if enabled
		if (this.enableMqttClient) {
			_Logger.info("MQTT client enabled. Will be initialized in Lab Module 06.");
			// this.mqttClient = new MqttClientConnector();
			// this.mqttClient.setDataMessageListener(this);
		}
		
		// Initialize CoAP server if enabled
		if (this.enableCoapServer) {
			_Logger.info("CoAP server enabled. Will be initialized in Lab Module 08.");
			// this.coapServer = new CoapServerGateway();
			// this.coapServer.setDataMessageListener(this);
		}
		
		// Initialize persistence client if enabled
		if (this.enablePersistenceClient) {
			_Logger.info("Persistence client enabled. Will be initialized in Lab Module 05 (optional).");
			// this.persistenceClient = new RedisPersistenceAdapter();
		}
		
		// Initialize cloud client if enabled
		if (this.enableCloudClient) {
			_Logger.info("Cloud client enabled. Will be initialized in Lab Module 11.");
			// this.cloudClient = new CloudClientConnector();
			// this.cloudClient.setDataMessageListener(this);
		}
		
		// Initialize SMTP client if enabled
		if (this.enableSmtpClient) {
			_Logger.info("SMTP client enabled. Will be initialized in future lab modules.");
			// this.smtpClient = new SmtpClientConnector();
		}
		
		_Logger.info("Connection subsystem initialization complete.");
	}
	
}