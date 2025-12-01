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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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
import programmingtheiot.gda.connection.ICloudClient;
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
	private ICloudClient cloudClient = null;
	private IPersistenceClient persistenceClient = null;
	private IRequestResponseClient smtpClient = null;
	private CoapServerGateway coapServer = null;

	// Humidity threshold crossing variables
	private ActuatorData latestHumidifierActuatorData = null;
	private ActuatorData latestHumidifierActuatorResponse = null;
	private SensorData latestHumiditySensorData = null;
	private OffsetDateTime latestHumiditySensorTimeStamp = null;

	private boolean handleHumidityChangeOnDevice = false;
	private int lastKnownHumidifierCommand = ConfigConst.OFF_COMMAND;
	private long humidityMaxTimePastThreshold = 300;
	private float nominalHumiditySetting = 40.0f;
	private float triggerHumidifierFloor = 30.0f;
	private float triggerHumidifierCeiling = 50.0f;

	// constructors

	/**
	 * Default constructor.
	 * Uses default configuration for all subsystems.
	 */
	public DeviceDataManager() {
		super();
		_Logger.info("Initializing DeviceDataManager...");
		initConnections();
		loadHumidityThresholdConfig();
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
		loadHumidityThresholdConfig();
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

			// Store latest humidifier response if applicable
			if (data.getTypeID() == ConfigConst.HUMIDIFIER_ACTUATOR_TYPE) {
				this.latestHumidifierActuatorResponse = data;
				_Logger.fine("Stored latest humidifier actuator response.");
			}

			String jsonData = DataUtil.getInstance().actuatorDataToJson(data);
			_Logger.fine("ActuatorData as JSON: " + jsonData);

			return true;
		} else {
			_Logger.warning("Received null ActuatorData response. Ignoring.");
			return false;
		}
	}

	@Override
	public boolean handleActuatorCommandRequest(ResourceNameEnum resourceName, ActuatorData data) {
		if (data != null) {
			_Logger.info("Handling actuator command request: " + data.getName());
			_Logger.fine("Actuator command: " + data.getCommand() + ", Value: " + data.getValue());

			if (data.hasError()) {
				_Logger.warning("Error flag set for ActuatorData instance.");
			}

			int qos = ConfigConst.DEFAULT_QOS;

			// Send command to CDA
			this.sendActuatorCommandToCda(resourceName, data);

			return true;
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
				// Handle ActuatorData from cloud
				if (resourceName == ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE) {
					_Logger.info("Handling incoming ActuatorData message from cloud: " + msg);

					// Convert JSON to ActuatorData for validation
					ActuatorData ad = DataUtil.getInstance().jsonToActuatorData(msg);
					String jsonData = DataUtil.getInstance().actuatorDataToJson(ad);

					// Forward to CDA via MQTT
					if (this.mqttClient != null) {
						_Logger.info("Publishing ActuatorData to CDA via MQTT...");
						return this.mqttClient.publishMessage(resourceName, jsonData, ConfigConst.DEFAULT_QOS);
					}

					return false;
				}
				// Handle SensorData from CDA
				else if (resourceName == ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE) {
					SensorData sensorData = DataUtil.getInstance().jsonToSensorData(msg);
					if (sensorData != null)
						return this.handleSensorMessage(resourceName, sensorData);
				}
				// Handle ActuatorResponse from CDA
				else if (resourceName == ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE) {
					ActuatorData actuatorData = DataUtil.getInstance().jsonToActuatorData(msg);
					if (actuatorData != null)
						return this.handleActuatorCommandResponse(resourceName, actuatorData);
				}
				// Handle SystemPerformanceData from CDA
				else if (resourceName == ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE) {
					SystemPerformanceData sysPerfData = DataUtil.getInstance().jsonToSystemPerformanceData(msg);
					if (sysPerfData != null)
						return this.handleSystemPerformanceMessage(resourceName, sysPerfData);
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

			if (data.hasError()) {
				_Logger.warning("Sensor data has error flag set: " + data.getStatusCode());
			}

			String jsonData = DataUtil.getInstance().sensorDataToJson(data);
			_Logger.fine("SensorData as JSON: " + jsonData);

			int qos = ConfigConst.DEFAULT_QOS;

			// Store in persistence if enabled
			if (this.enablePersistenceClient && this.persistenceClient != null) {
				this.persistenceClient.storeData(resourceName.getResourceName(), qos, data);
			}

			// Analyze sensor data for threshold crossings
			this.handleIncomingDataAnalysis(resourceName, data);

			// Send to cloud or upstream
			this.handleUpstreamTransmission(resourceName, data);

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

			if (data.hasError()) {
				_Logger.warning("System performance data has error flag set: " + data.getStatusCode());
			}

			String jsonData = DataUtil.getInstance().systemPerformanceDataToJson(data);
			_Logger.fine("SystemPerformanceData as JSON: " + jsonData);

			// Send to cloud or upstream
			this.handleUpstreamTransmission(resourceName, data);

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

		// Connect MQTT client first
		if (this.mqttClient != null) {
			_Logger.info("Starting MQTT client connector...");

			if (this.mqttClient.connectClient()) {
				_Logger.info("MQTT client connected successfully.");

				_Logger.info("Subscribing to CDA topics...");

				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE,
						ConfigConst.DEFAULT_QOS);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, ConfigConst.DEFAULT_QOS);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE,
						ConfigConst.DEFAULT_QOS);
				this.mqttClient.subscribeToTopic(ResourceNameEnum.CDA_MGMT_STATUS_MSG_RESOURCE,
						ConfigConst.DEFAULT_QOS);

				_Logger.info("Subscribed to all CDA topics successfully.");
			} else {
				_Logger.warning("Failed to connect MQTT client.");
			}
		}

		// Connect cloud client
		if (this.cloudClient != null) {
			_Logger.info("Starting cloud client connector...");

			if (this.cloudClient.connectClient()) {
				_Logger.info("Cloud client connected successfully.");
			} else {
				_Logger.warning("Failed to connect cloud client.");
			}
		}

		// Start system performance manager last
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
			_Logger.info("Persistence client ready.");
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

		if (this.cloudClient != null) {
			_Logger.info("Stopping cloud client connector...");

			if (this.cloudClient.disconnectClient()) {
				_Logger.info("Cloud client disconnected successfully.");
			} else {
				_Logger.warning("Failed to disconnect cloud client.");
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
			_Logger.info("Persistence client stopped.");
		}

		_Logger.info("DeviceDataManager stopped successfully.");
	}

	// private methods

	private void initConnections() {
		_Logger.info("Initializing connection subsystems...");

		ConfigUtil configUtil = ConfigUtil.getInstance();

		this.enableMqttClient = configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_MQTT_CLIENT_KEY);
		this.enableCoapServer = configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_COAP_SERVER_KEY);
		this.enableCloudClient = configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_CLOUD_CLIENT_KEY);
		this.enableSystemPerf = configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE, ConfigConst.ENABLE_SYSTEM_PERF_KEY);

		if (this.enableMqttClient) {
			_Logger.info("MQTT client enabled. Creating MqttClientConnector...");
			this.mqttClient = new MqttClientConnector();
			this.mqttClient.setDataMessageListener(this);
			_Logger.info("MqttClientConnector created and listener registered.");
		} else {
			_Logger.info("MQTT client disabled by configuration.");
		}

		if (this.enableCloudClient) {
			_Logger.info("Cloud client enabled. Creating CloudClientConnector...");
			this.cloudClient = new CloudClientConnector();
			this.cloudClient.setDataMessageListener(this);
			_Logger.info("CloudClientConnector created and listener registered.");
		} else {
			_Logger.info("Cloud client disabled by configuration.");
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
			_Logger.info("Persistence client enabled (optional).");
		}

		if (this.enableSmtpClient) {
			_Logger.info("SMTP client enabled (future implementation).");
		}

		_Logger.info("Connection subsystem initialization complete.");
	}

	private void loadHumidityThresholdConfig() {
		ConfigUtil configUtil = ConfigUtil.getInstance();

		this.handleHumidityChangeOnDevice = configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE,
				"handleHumidityChangeOnDevice");

		this.humidityMaxTimePastThreshold = configUtil.getInteger(
				ConfigConst.GATEWAY_DEVICE,
				"humidityMaxTimePastThreshold");

		this.nominalHumiditySetting = configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE,
				"nominalHumiditySetting");

		this.triggerHumidifierFloor = configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE,
				"triggerHumidifierFloor");

		this.triggerHumidifierCeiling = configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE,
				"triggerHumidifierCeiling");

		if (this.humidityMaxTimePastThreshold < 10 || this.humidityMaxTimePastThreshold > 7200) {
			_Logger.warning("Invalid humidityMaxTimePastThreshold value. Setting to default (300 seconds).");
			this.humidityMaxTimePastThreshold = 300;
		}

		_Logger.info("Humidity threshold configuration loaded:");
		_Logger.info("  Handle humidity changes: " + this.handleHumidityChangeOnDevice);
		_Logger.info("  Max time past threshold: " + this.humidityMaxTimePastThreshold + " seconds");
		_Logger.info("  Nominal humidity: " + this.nominalHumiditySetting + "%");
		_Logger.info("  Humidifier floor: " + this.triggerHumidifierFloor + "%");
		_Logger.info("  Humidifier ceiling: " + this.triggerHumidifierCeiling + "%");
	}

	private void handleIncomingDataAnalysis(ResourceNameEnum resourceName, SensorData data) {
		_Logger.fine("Analyzing incoming sensor data...");

		if (data.getTypeID() == ConfigConst.HUMIDITY_SENSOR_TYPE) {
			this.handleHumiditySensorAnalysis(resourceName, data);
		}
	}

	private void handleHumiditySensorAnalysis(ResourceNameEnum resourceName, SensorData data) {
		if (!this.handleHumidityChangeOnDevice) {
			_Logger.fine("Humidity change handling disabled. Skipping analysis.");
			return;
		}

		_Logger.fine("Analyzing humidity data from CDA: " + data.getLocationID() + ". Value: " + data.getValue());

		boolean isLow = data.getValue() < this.triggerHumidifierFloor;
		boolean isHigh = data.getValue() > this.triggerHumidifierCeiling;

		if (isLow || isHigh) {
			_Logger.fine("Humidity data from CDA exceeds nominal range.");

			if (this.latestHumiditySensorData == null) {
				this.latestHumiditySensorData = data;
				this.latestHumiditySensorTimeStamp = this.getDateTimeFromData(data);

				_Logger.fine("Starting humidity threshold crossing timer. Waiting for " +
						this.humidityMaxTimePastThreshold + " seconds.");
			} else {
				OffsetDateTime currentTimeStamp = this.getDateTimeFromData(data);
				long secondsElapsed = java.time.Duration.between(
						this.latestHumiditySensorTimeStamp,
						currentTimeStamp).getSeconds();

				_Logger.fine("Time since last threshold crossing: " + secondsElapsed + " seconds");

				if (secondsElapsed >= this.humidityMaxTimePastThreshold) {
					_Logger.info("Humidity threshold exceeded for " + secondsElapsed
							+ " seconds. Triggering actuation event.");

					ActuatorData actuatorData = new ActuatorData();
					actuatorData.setName(ConfigConst.HUMIDIFIER_ACTUATOR_NAME);
					actuatorData.setTypeID(ConfigConst.HUMIDIFIER_ACTUATOR_TYPE);
					actuatorData.setLocationID(data.getLocationID());

					if (isLow) {
						actuatorData.setCommand(ConfigConst.ON_COMMAND);
						actuatorData.setValue(this.nominalHumiditySetting);
						actuatorData.setStateData("Humidity too low - turning humidifier ON");
						_Logger.info("Humidity below floor threshold. Turning humidifier ON.");
					} else if (isHigh) {
						actuatorData.setCommand(ConfigConst.OFF_COMMAND);
						actuatorData.setValue(this.nominalHumiditySetting);
						actuatorData.setStateData("Humidity too high - turning humidifier OFF");
						_Logger.info("Humidity above ceiling threshold. Turning humidifier OFF.");
					}

					this.sendActuatorCommandToCda(resourceName, actuatorData);

					this.latestHumiditySensorData = data;
					this.latestHumiditySensorTimeStamp = currentTimeStamp;
					this.lastKnownHumidifierCommand = actuatorData.getCommand();
				} else {
					_Logger.fine("Threshold crossing detected but insufficient time elapsed. Continuing to monitor.");
				}
			}
		} else {
			if (this.latestHumiditySensorData != null) {
				_Logger.fine("Humidity returned to nominal range. Resetting threshold tracking.");
				this.latestHumiditySensorData = null;
				this.latestHumiditySensorTimeStamp = null;
			}
		}
	}

	private void sendActuatorCommandToCda(ResourceNameEnum resourceName, ActuatorData data) {
		if (data != null) {
			_Logger.info("Sending actuator command to CDA: " + data.getName());

			this.latestHumidifierActuatorData = data;

			String jsonData = DataUtil.getInstance().actuatorDataToJson(data);

			if (this.mqttClient != null) {
				boolean success = this.mqttClient.publishMessage(
						ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE,
						jsonData,
						ConfigConst.DEFAULT_QOS);

				if (success) {
					_Logger.info("Actuator command sent to CDA successfully.");
				} else {
					_Logger.warning("Failed to send actuator command to CDA.");
				}
			} else {
				_Logger.warning("MQTT client not available. Cannot send actuator command.");
			}
		}
	}

	private void handleUpstreamTransmission(ResourceNameEnum resourceName, SensorData data) {
		_Logger.fine("Sending SensorData to cloud: " + resourceName.getResourceName());

		if (this.cloudClient != null) {
			if (this.cloudClient.sendEdgeDataToCloud(resourceName, data)) {
				_Logger.fine("SensorData sent to cloud successfully.");
			} else {
				_Logger.warning("Failed to send SensorData to cloud.");
			}
		}
	}

	private void handleUpstreamTransmission(ResourceNameEnum resourceName, SystemPerformanceData data) {
		_Logger.fine("Sending SystemPerformanceData to cloud: " + resourceName.getResourceName());

		if (this.cloudClient != null) {
			if (this.cloudClient.sendEdgeDataToCloud(resourceName, data)) {
				_Logger.fine("SystemPerformanceData sent to cloud successfully.");
			} else {
				_Logger.warning("Failed to send SystemPerformanceData to cloud.");
			}
		}
	}

	private OffsetDateTime getDateTimeFromData(SensorData data) {
		try {
			String timeStampStr = data.getTimeStamp();

			if (timeStampStr != null && !timeStampStr.isEmpty()) {
				return OffsetDateTime.parse(timeStampStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
			}
		} catch (Exception e) {
			_Logger.fine("Could not parse timestamp from SensorData. Using current time.");
		}

		return OffsetDateTime.now();
	}
}