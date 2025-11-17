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
 * Coordinates data flow between components and manages lifecycle of all
 * subsystems.
 */
public class DeviceDataManager implements IDataMessageListener {
	// static

	private static final Logger _Logger = Logger.getLogger(DeviceDataManager.class.getName());

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
	public DeviceDataManager() {
		super();
		_Logger.info("Initializing DeviceDataManager...");
		initConnections();
		_Logger.info("DeviceDataManager initialization complete.");
	}

	/**
	 * Constructor with configuration parameters.
	 * Allows custom enable/disable of subsystems.
	 *
	 * @param enableMqttClient        Enable MQTT client connectivity
	 * @param enableCoapClient        Enable CoAP server connectivity
	 * @param enableCloudClient       Enable cloud service connectivity
	 * @param enableSmtpClient        Enable SMTP email notifications
	 * @param enablePersistenceClient Enable Redis persistence
	 */
	public DeviceDataManager(
			boolean enableMqttClient,
			boolean enableCoapClient,
			boolean enableCloudClient,
			boolean enableSmtpClient,
			boolean enablePersistenceClient) {

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

	@Override
	public boolean handleActuatorCommandResponse(ResourceNameEnum resourceName, ActuatorData data) {
		if (data != null) {
			_Logger.info("Handling actuator response from CDA: " + data.getName());
			_Logger.fine("Actuator response data: " + data.toString());

			if (data.hasError()) {
				_Logger.warning("Actuator response has error flag set: " + data.getStatusCode());
			}

			String jsonData = DataUtil.getInstance().actuatorDataToJson(data);
			_Logger.fine("ActuatorData as JSON: " + jsonData);

			return true;
		} else {
			_Logger.warning("Received null ActuatorData response. Ignoring.");
			return false;
		}
	}

	/**
	 * Handles actuator command requests (typically from cloud or internal logic).
	 */
	@Override
	public boolean handleActuatorCommandRequest(ResourceNameEnum resourceName, ActuatorData data) {
		if (data != null) {
			_Logger.info("Handling actuator command request: " + data.getName());
			_Logger.fine("Actuator command: " + data.getCommand() + ", Value: " + data.getValue());

			String jsonData = DataUtil.getInstance().actuatorDataToJson(data);

			boolean mqttSuccess = false;
			boolean coapSuccess = false;

			if (this.mqttClient != null && jsonData != null) {
				_Logger.info("Publishing actuator command to CDA via MQTT...");

				mqttSuccess = this.mqttClient.publishMessage(
						ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE,
						jsonData,
						ConfigConst.DEFAULT_QOS);

				if (mqttSuccess) {
					_Logger.info("Actuator command published successfully via MQTT.");
				} else {
					_Logger.warning("Failed to publish actuator command via MQTT.");
				}
			}

			if (this.coapServer != null && jsonData != null) {
				_Logger.info("Sending actuator command to CDA via CoAP...");

				coapSuccess = this.coapServer.sendActuatorCommand(
						ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE,
						jsonData);

				if (coapSuccess) {
					_Logger.info("Actuator command sent successfully via CoAP.");
				} else {
					_Logger.warning("Failed to send actuator command via CoAP.");
				}
			}

			return mqttSuccess || coapSuccess;

		} else {
			_Logger.warning("Received null ActuatorData request. Ignoring.");
			return false;
		}
	}

	@Override
	public boolean handleIncomingMessage(ResourceNameEnum resourceName, String msg) {
		if (msg != null && msg.trim().length() > 0) {
			_Logger.info("Handling incoming message from resource: " + resourceName.getResourceName());
			_Logger.fine("Message payload: " + msg);

			try {
				if (resourceName == ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE) {
					SensorData sensorData = DataUtil.getInstance().jsonToSensorData(msg);
					if (sensorData != null) return this.handleSensorMessage(resourceName, sensorData);
				} else if (resourceName == ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE) {
					ActuatorData actuatorData = DataUtil.getInstance().jsonToActuatorData(msg);
					if (actuatorData != null) return this.handleActuatorCommandResponse(resourceName, actuatorData);
				} else if (resourceName == ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE) {
					SystemPerformanceData sysPerfData = DataUtil.getInstance().jsonToSystemPerformanceData(msg);
					if (sysPerfData != null) return this.handleSystemPerformanceMessage(resourceName, sysPerfData);
				} else {
					_Logger.info("Received generic message from resource: " + resourceName.getResourceName());
					_Logger.fine("Message content: " + msg);
					return true;
				}

			} catch (Exception e) {
				_Logger.log(Level.WARNING, "Failed to convert incoming message from JSON.", e);
			}

			return false;

		} else {
			_Logger.warning("Received empty or null message. Ignoring.");
			return false;
		}
	}

	@Override
	public boolean handleSensorMessage(ResourceNameEnum resourceName, SensorData data) {
		if (data != null) {
			_Logger.info("Handling sensor message from CDA: " + data.getName());
			_Logger.fine("Sensor data - Type: " + data.getTypeID() + ", Value: " + data.getValue());

			String jsonData = DataUtil.getInstance().sensorDataToJson(data);
			_Logger.fine("SensorData as JSON: " + jsonData);

			if (data.hasError()) {
				_Logger.warning("Sensor data has error flag set: " + data.getStatusCode());
			}

			return true;
		} else {
			_Logger.warning("Received null SensorData. Ignoring.");
			return false;
		}
	}

	@Override
	public boolean handleSystemPerformanceMessage(ResourceNameEnum resourceName, SystemPerformanceData data) {
		if (data != null) {
			_Logger.info("Handling system performance message: " + data.getName());
			_Logger.fine(String.format(
					"System Performance - CPU: %.2f%%, Memory: %.2f%%, Disk: %.2f%%",
					data.getCpuUtilization(),
					data.getMemoryUtilization(),
					data.getDiskUtilization()));

			String jsonData = DataUtil.getInstance().systemPerformanceDataToJson(data);
			_Logger.fine("SystemPerformanceData as JSON: " + jsonData);

			if (data.hasError()) {
				_Logger.warning("System performance data has error flag set: " + data.getStatusCode());
			}

			return true;
		} else {
			_Logger.warning("Received null SystemPerformanceData. Ignoring.");
			return false;
		}
	}

	public void setActuatorDataListener(String name, IActuatorDataListener listener) {
		if (listener != null) {
			_Logger.info("Setting actuator data listener: " + name);
			this.actuatorDataListener = listener;
		} else {
			_Logger.warning("Attempted to set null actuator data listener. Ignoring.");
		}
	}

	public void startManager() {
		_Logger.info("Starting DeviceDataManager...");

		if (this.mqttClient != null) {
			_Logger.info("Starting MQTT client connector...");

			if (this.mqttClient.connectClient()) {
				_Logger.info("MQTT client connected successfully.");

				_Logger.info("Subscribing to CDA topics...");

				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, ConfigConst.DEFAULT_QOS);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, ConfigConst.DEFAULT_QOS);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, ConfigConst.DEFAULT_QOS);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_MGMT_STATUS_MSG_RESOURCE, ConfigConst.DEFAULT_QOS);

				_Logger.info("Subscribed to all CDA topics successfully.");
			} else {
				_Logger.warning("Failed to connect MQTT client.");
			}
		}

		if (this.sysPerfMgr != null) {
			_Logger.info("Starting SystemPerformanceManager...");
			this.sysPerfMgr.startManager();
		}

		if (this.coapServer != null) {
			_Logger.info("Starting CoAP server gateway...");

			if (this.coapServer.startServer()) {
				_Logger.info("CoAP server started successfully.");
			} else {
				_Logger.warning("Failed to start CoAP server.");
			}
		}

		if (this.persistenceClient != null) {
			_Logger.info("Starting persistence client...");
		}

		if (this.cloudClient != null) {
			_Logger.info("Starting cloud client connector...");
		}

		_Logger.info("DeviceDataManager started successfully.");
	}

	public void stopManager() {
		_Logger.info("Stopping DeviceDataManager...");

		if (this.sysPerfMgr != null) {
			_Logger.info("Stopping SystemPerformanceManager...");
			this.sysPerfMgr.stopManager();
		}

		if (this.mqttClient != null) {
			_Logger.info("Stopping MQTT client connector...");
			_Logger.info("Unsubscribing from CDA topics...");

			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE);
			this.mqttClient.unsubscribeFromTopic(ResourceNameEnum.CDA_MGMT_STATUS_MSG_RESOURCE);

			if (this.mqttClient.disconnectClient()) {
				_Logger.info("MQTT client disconnected successfully.");
			} else {
				_Logger.warning("Failed to disconnect MQTT client.");
			}
		}

		if (this.coapServer != null) {
			_Logger.info("Stopping CoAP server gateway...");

			if (this.coapServer.stopServer()) {
				_Logger.info("CoAP server stopped successfully.");
			} else {
				_Logger.warning("Failed to stop CoAP server.");
			}
		}

		if (this.persistenceClient != null) {
			_Logger.info("Stopping persistence client...");
		}

		if (this.cloudClient != null) {
			_Logger.info("Stopping cloud client connector...");
		}

		_Logger.info("DeviceDataManager stopped successfully.");
	}

	// private methods

	private void initConnections() {
		_Logger.info("Initializing connection subsystems...");

		ConfigUtil configUtil = ConfigUtil.getInstance();

		this.enableMqttClient = configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_MQTT_CLIENT_KEY);
		this.enableCoapServer = configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_COAP_SERVER_KEY);
		this.enableSystemPerf = configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_SYSTEM_PERF_KEY);

		if (this.enableMqttClient) {
			_Logger.info("MQTT client enabled. Creating MqttClientConnector...");
			this.mqttClient = new MqttClientConnector();
			this.mqttClient.setDataMessageListener(this);
			_Logger.info("MqttClientConnector created and listener registered.");
		} else {
			_Logger.info("MQTT client disabled by configuration.");
		}

		if (this.enableSystemPerf) {
			_Logger.info("System performance monitoring enabled. Creating SystemPerformanceManager...");
			this.sysPerfMgr = new SystemPerformanceManager();
			this.sysPerfMgr.setDataMessageListener(this);
			_Logger.info("SystemPerformanceManager created and listener registered.");
		} else {
			_Logger.info("System performance monitoring disabled by configuration.");
		}

		if (this.enableCoapServer) {
			_Logger.info("CoAP server enabled. Creating CoapServerGateway...");
			this.coapServer = new CoapServerGateway(this);
			_Logger.info("CoapServerGateway created and listener registered.");
		} else {
			_Logger.info("CoAP server disabled by configuration.");
		}

		if (this.enablePersistenceClient) {
			_Logger.info("Persistence client enabled. Will be initialized in Lab Module 05 (optional).");
		}

		if (this.enableCloudClient) {
			_Logger.info("Cloud client enabled. Will be initialized in Lab Module 11.");
		}

		if (this.enableSmtpClient) {
			_Logger.info("SMTP client enabled. Will be initialized in future lab modules.");
		}

		_Logger.info("Connection subsystem initialization complete.");
	}
}