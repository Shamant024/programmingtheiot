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

package programmingtheiot.gda.connection;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IConnectionListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.SystemPerformanceData;

/**
 * Cloud client connector for Ubidots integration.
 * Handles bidirectional communication between GDA and cloud service.
 */
public class CloudClientConnector implements ICloudClient, IConnectionListener {
	// static

	private static final Logger _Logger = Logger.getLogger(CloudClientConnector.class.getName());

	// private var's

	private String topicPrefix = "";
	private MqttClientConnector mqttClient = null;
	private IDataMessageListener dataMsgListener = null;
	private int qosLevel = 1;

	// constructors

	/**
	 * Default constructor.
	 * Loads cloud service configuration from PiotConfig.props.
	 */
	public CloudClientConnector() {
		super();

		ConfigUtil configUtil = ConfigUtil.getInstance();

		this.topicPrefix = configUtil.getProperty(
				ConfigConst.CLOUD_GATEWAY_SERVICE,
				ConfigConst.BASE_TOPIC_KEY);

		// Ubidots uses topic format: /v1.6/devices/
		if (topicPrefix == null) {
			topicPrefix = "/";
		} else {
			if (!topicPrefix.endsWith("/")) {
				topicPrefix += "/";
			}
		}

		_Logger.info("Cloud client connector initialized. Topic prefix: " + this.topicPrefix);
	}

	// public methods

	@Override
	public boolean connectClient() {
		if (this.mqttClient == null) {
			_Logger.info("Creating MQTT client for cloud connectivity...");

			// Create MQTT client with cloud gateway configuration
			this.mqttClient = new MqttClientConnector(ConfigConst.CLOUD_GATEWAY_SERVICE);

			// Set this as the connection listener
			this.mqttClient.setConnectionListener(this);

			_Logger.info("MQTT client created for cloud service.");
		}

		// Connect to cloud MQTT broker
		_Logger.info("Connecting to cloud service...");
		return this.mqttClient.connectClient();
	}

	@Override
	public boolean disconnectClient() {
		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			_Logger.info("Disconnecting from cloud service...");
			return this.mqttClient.disconnectClient();
		}

		_Logger.warning("Cloud MQTT client not connected. Nothing to disconnect.");
		return false;
	}

	@Override
	public boolean setDataMessageListener(IDataMessageListener listener) {
		if (listener != null) {
			this.dataMsgListener = listener;
			_Logger.info("Data message listener set for cloud client.");
			return true;
		} else {
			_Logger.warning("Attempted to set null data message listener.");
			return false;
		}
	}

	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, SensorData data) {
		if (resource != null && data != null) {
			_Logger.fine("Sending SensorData to cloud: " + data.getName());

			// Convert to TimeAndValue JSON format for Ubidots
			String payload = DataUtil.getInstance().sensorDataToTimeAndValueJson(data);

			// Create topic name
			return publishMessageToCloud(resource, data.getName(), payload);
		}

		_Logger.warning("Cannot send null SensorData to cloud.");
		return false;
	}

	@Override
	public boolean sendEdgeDataToCloud(ResourceNameEnum resource, SystemPerformanceData data) {
		if (resource != null && data != null) {
			_Logger.fine("Sending SystemPerformanceData to cloud...");

			// Send CPU utilization
			SensorData cpuData = new SensorData();
			cpuData.updateData(data);
			cpuData.setName(ConfigConst.CPU_UTIL_NAME);
			cpuData.setValue(data.getCpuUtilization());

			boolean cpuSuccess = sendEdgeDataToCloud(resource, cpuData);

			if (!cpuSuccess) {
				_Logger.warning("Failed to send CPU utilization to cloud.");
			}

			// Send memory utilization
			SensorData memData = new SensorData();
			memData.updateData(data);
			memData.setName(ConfigConst.MEM_UTIL_NAME);
			memData.setValue(data.getMemoryUtilization());

			boolean memSuccess = sendEdgeDataToCloud(resource, memData);

			if (!memSuccess) {
				_Logger.warning("Failed to send memory utilization to cloud.");
			}

			return (cpuSuccess && memSuccess);
		}

		_Logger.warning("Cannot send null SystemPerformanceData to cloud.");
		return false;
	}

	@Override
	public boolean subscribeToCloudEvents(ResourceNameEnum resource) {
		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			String topicName = createTopicName(resource);

			_Logger.info("Subscribing to cloud events topic: " + topicName);

			return this.mqttClient.subscribeToTopic(topicName, this.qosLevel);
		} else {
			_Logger.warning("MQTT client not connected. Cannot subscribe to cloud events.");
			return false;
		}
	}

	@Override
	public boolean unsubscribeFromCloudEvents(ResourceNameEnum resource) {
		if (this.mqttClient != null && this.mqttClient.isConnected()) {
			String topicName = createTopicName(resource);

			_Logger.info("Unsubscribing from cloud events topic: " + topicName);

			return this.mqttClient.unsubscribeFromTopic(topicName);
		} else {
			_Logger.warning("MQTT client not connected. Cannot unsubscribe from cloud events.");
			return false;
		}
	}

	// IConnectionListener implementation

	@Override
	public void onConnect() {
		_Logger.info("Connected to cloud service successfully!");
		_Logger.info("Handling CSP subscriptions and device topic provisioning...");

		// Create LED actuation listener
		LedEnablementMessageListener ledListener = new LedEnablementMessageListener(this.dataMsgListener);

		// Create response actuation event to provision topic if it doesn't exist
		ActuatorData ad = new ActuatorData();
		ad.setAsResponse();
		ad.setName(ConfigConst.LED_ACTUATOR_NAME);
		ad.setValue((float) -1.0); // Invalid value - will be ignored but creates topic

		String ledTopic = createTopicName(
				ledListener.getResource().getDeviceName(),
				ad.getName());

		_Logger.info("LED actuation topic: " + ledTopic);

		// Convert to TimeAndValue JSON
		String adJson = DataUtil.getInstance().actuatorDataToTimeAndValueJson(ad);

		// Publish to create topic
		this.publishMessageToCloud(ledTopic, adJson);

		// Subscribe to LED actuation events
		_Logger.info("Subscribing to LED actuation events from cloud...");
		this.mqttClient.subscribeToTopic(ledTopic, this.qosLevel, ledListener);

		_Logger.info("Cloud service provisioning complete.");
	}

	@Override
	public void onDisconnect() {
		_Logger.info("Disconnected from cloud service.");
	}

	// private methods

	/**
	 * Creates topic name from resource enum.
	 */
	private String createTopicName(ResourceNameEnum resource) {
		return createTopicName(resource.getDeviceName(), resource.getResourceType());
	}

	/**
	 * Creates topic name from device name and resource type.
	 */
	private String createTopicName(String deviceName, String resourceTypeName) {
		StringBuilder buf = new StringBuilder();

		if (deviceName != null && deviceName.trim().length() > 0) {
			buf.append(topicPrefix).append(deviceName);
		}

		if (resourceTypeName != null && resourceTypeName.trim().length() > 0) {
			buf.append('/').append(resourceTypeName);
		}

		return buf.toString().toLowerCase();
	}

	/**
	 * Publishes message to cloud with resource and item name.
	 */
	private boolean publishMessageToCloud(ResourceNameEnum resource, String itemName, String payload) {
		String topicName = createTopicName(resource) + "-" + itemName.toLowerCase();
		return publishMessageToCloud(topicName, payload);
	}

	/**
	 * Publishes message to cloud with explicit topic name.
	 */
	private boolean publishMessageToCloud(String topicName, String payload) {
		try {
			_Logger.finest("Publishing to cloud: " + topicName);
			_Logger.finest("Payload: " + payload);

			this.mqttClient.publishMessage(topicName, payload.getBytes(), this.qosLevel);

			_Logger.fine("Successfully published to cloud: " + topicName);
			return true;

		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to publish to cloud: " + topicName, e);
		}

		return false;
	}

	/**
	 * Inner class: Handles LED actuation events from cloud.
	 */
	private class LedEnablementMessageListener implements IMqttMessageListener {
		private IDataMessageListener dataMsgListener = null;
		private ResourceNameEnum resource = ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE;
		private int typeID = ConfigConst.LED_ACTUATOR_TYPE;
		private String itemName = ConfigConst.LED_ACTUATOR_NAME;

		LedEnablementMessageListener(IDataMessageListener dataMsgListener) {
			this.dataMsgListener = dataMsgListener;
		}

		public ResourceNameEnum getResource() {
			return this.resource;
		}

		@Override
		public void messageArrived(String topic, MqttMessage message) throws Exception {
			try {
				String jsonData = new String(message.getPayload());

				_Logger.info("LED actuation message received from cloud!");
				_Logger.fine("Topic: " + topic);
				_Logger.fine("Payload: " + jsonData);

				// Convert JSON to ActuatorData
				ActuatorData actuatorData = DataUtil.getInstance().jsonToActuatorData(jsonData);

				// Set actuator properties
				actuatorData.setLocationID(ConfigConst.CONSTRAINED_DEVICE);
				actuatorData.setTypeID(this.typeID);
				actuatorData.setName(this.itemName);

				int val = (int) actuatorData.getValue();

				switch (val) {
					case ConfigConst.ON_COMMAND:
						_Logger.info("Received LED enablement message [ON] from cloud.");
						actuatorData.setCommand(ConfigConst.ON_COMMAND);
						actuatorData.setStateData("LED switching ON");
						break;

					case ConfigConst.OFF_COMMAND:
						_Logger.info("Received LED enablement message [OFF] from cloud.");
						actuatorData.setCommand(ConfigConst.OFF_COMMAND);
						actuatorData.setStateData("LED switching OFF");
						break;

					default:
						_Logger.fine("Invalid actuation value received. Ignoring.");
						return;
				}

				// Pass to DeviceDataManager for forwarding to CDA
				if (this.dataMsgListener != null) {
					String adJson = DataUtil.getInstance().actuatorDataToJson(actuatorData);

					_Logger.info("Forwarding LED actuation command to DeviceDataManager...");
					this.dataMsgListener.handleIncomingMessage(
							ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE,
							adJson);
				} else {
					_Logger.warning("No data message listener set. Cannot forward actuation command.");
				}

			} catch (Exception e) {
				_Logger.log(Level.WARNING, "Failed to process LED actuation message from cloud.", e);
			}
		}
	}
}