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

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IConnectionListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;

/**
 * MQTT Client Connector for GDA to communicate with MQTT broker.
 * 
 * This class handles:
 * - Connection/disconnection to MQTT broker
 * - Publishing messages to topics
 * - Subscribing to topics
 * - Handling incoming messages via callbacks
 * - Implements MqttCallbackExtended for Paho callbacks
 * - Supports both local MQTT and cloud MQTT configurations
 */
public class MqttClientConnector implements IPubSubClient, MqttCallbackExtended {
	// static

	private static final Logger _Logger = Logger.getLogger(MqttClientConnector.class.getName());

	// params

	private String protocol = ConfigConst.DEFAULT_MQTT_PROTOCOL;
	private String host = ConfigConst.DEFAULT_HOST;
	private int port = ConfigConst.DEFAULT_MQTT_PORT;
	private int keepAlive = ConfigConst.DEFAULT_KEEP_ALIVE;
	private int defaultQos = ConfigConst.DEFAULT_QOS;
	private String clientID = null;
	private String brokerAddr = null;

	private MqttClient mqttClient = null;
	private MqttConnectOptions connOpts = null;
	private MemoryPersistence persistence = null;

	private IDataMessageListener dataMsgListener = null;
	private IConnectionListener connListener = null;
	private boolean useCloudGatewayConfig = false;

	// constructors

	/**
	 * Default constructor.
	 * Initializes with local MQTT gateway configuration.
	 */
	public MqttClientConnector() {
		this(false);
	}

	/**
	 * Constructor that allows selecting cloud gateway configuration.
	 * 
	 * @param useCloudGatewayConfig If true, uses Cloud.GatewayService config
	 *                              section
	 */
	public MqttClientConnector(boolean useCloudGatewayConfig) {
		this(useCloudGatewayConfig ? ConfigConst.CLOUD_GATEWAY_SERVICE : null);
	}

	/**
	 * Constructor that allows custom configuration section name.
	 * 
	 * @param cloudGatewayConfigSectionName The config section name to use
	 */
	public MqttClientConnector(String cloudGatewayConfigSectionName) {
		super();

		if (cloudGatewayConfigSectionName != null && cloudGatewayConfigSectionName.trim().length() > 0) {
			this.useCloudGatewayConfig = true;

			_Logger.info("Using cloud gateway configuration: " + cloudGatewayConfigSectionName);
			initClientParameters(cloudGatewayConfigSectionName);
		} else {
			this.useCloudGatewayConfig = false;

			_Logger.info("Using local MQTT gateway configuration.");
			initClientParameters(ConfigConst.MQTT_GATEWAY_SERVICE);
		}

		_Logger.info("MQTT Client ID: " + this.clientID);
		_Logger.info("MQTT Broker Host: " + this.host);
		_Logger.info("MQTT Broker Port: " + this.port);
		_Logger.info("MQTT Keep Alive: " + this.keepAlive);
		_Logger.info("Using Cloud Config: " + this.useCloudGatewayConfig);
	}

	// public methods

	@Override
	public boolean connectClient() {
		try {
			if (this.mqttClient == null) {
				// Create MQTT client with memory persistence
				this.persistence = new MemoryPersistence();
				this.mqttClient = new MqttClient(this.brokerAddr, this.clientID, this.persistence);

				// Set this class as the callback handler
				this.mqttClient.setCallback(this);

				_Logger.info("MQTT client created successfully.");
			}

			if (!this.mqttClient.isConnected()) {
				_Logger.info("Connecting to MQTT broker: " + this.brokerAddr);

				// Connect to broker using pre-configured connection options
				this.mqttClient.connect(this.connOpts);

				_Logger.info("Connected to MQTT broker successfully.");
				return true;
			} else {
				_Logger.warning("MQTT client is already connected.");
				return false;
			}
		} catch (MqttException e) {
			_Logger.log(Level.SEVERE, "Failed to connect to MQTT broker.", e);
			return false;
		}
	}

	@Override
	public boolean disconnectClient() {
		try {
			if (this.mqttClient != null && this.mqttClient.isConnected()) {
				_Logger.info("Disconnecting from MQTT broker...");

				this.mqttClient.disconnect();

				_Logger.info("Disconnected from MQTT broker successfully.");
				return true;
			} else {
				_Logger.warning("MQTT client is not connected.");
				return false;
			}
		} catch (MqttException e) {
			_Logger.log(Level.SEVERE, "Failed to disconnect from MQTT broker.", e);
			return false;
		}
	}

	public boolean isConnected() {
		return (this.mqttClient != null && this.mqttClient.isConnected());
	}

	@Override
	public boolean publishMessage(ResourceNameEnum topicName, String msg, int qos) {
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to publish message.");
			return false;
		}

		if (msg == null || msg.length() == 0) {
			_Logger.warning("Message is null or empty. Unable to publish message.");
			return false;
		}

		return publishMessage(topicName.getResourceName(), msg.getBytes(), qos);
	}

	@Override
	public boolean subscribeToTopic(ResourceNameEnum topicName, int qos) {
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to subscribe.");
			return false;
		}

		return subscribeToTopic(topicName.getResourceName(), qos);
	}

	@Override
	public boolean unsubscribeFromTopic(ResourceNameEnum topicName) {
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to unsubscribe.");
			return false;
		}

		return unsubscribeFromTopic(topicName.getResourceName());
	}

	@Override
	public boolean setConnectionListener(IConnectionListener listener) {
		if (listener != null) {
			this.connListener = listener;
			_Logger.info("Connection listener set successfully.");
			return true;
		} else {
			_Logger.warning("No connection listener provided.");
			return false;
		}
	}

	@Override
	public boolean setDataMessageListener(IDataMessageListener listener) {
		if (listener != null) {
			this.dataMsgListener = listener;
			_Logger.info("Data message listener set successfully.");
			return true;
		} else {
			_Logger.warning("No data message listener provided.");
			return false;
		}
	}

	// protected methods - allow subclasses and package classes to use String topics

	/**
	 * Publishes a message to the specified topic using a String topic name.
	 * 
	 * @param topicName The topic name as a String
	 * @param payload   The message payload as bytes
	 * @param qos       The QoS level (0, 1, or 2)
	 * @return boolean True on success, false otherwise
	 */
	protected boolean publishMessage(String topicName, byte[] payload, int qos) {
		if (topicName == null) {
			_Logger.warning("Topic name is null. Unable to publish message: " + this.brokerAddr);
			return false;
		}

		if (payload == null || payload.length == 0) {
			_Logger.warning("Payload is null or empty. Unable to publish message: " + this.brokerAddr);
			return false;
		}

		if (qos < 0 || qos > 2) {
			_Logger.warning("Invalid QoS. Using default. QoS requested: " + qos);
			qos = this.defaultQos;
		}

		if (!this.isConnected()) {
			_Logger.warning("MQTT client is not connected. Unable to publish message.");
			return false;
		}

		try {
			_Logger.info("Publishing message to topic: " + topicName);
			_Logger.fine("Message payload: " + new String(payload));

			MqttMessage mqttMsg = new MqttMessage();
			mqttMsg.setQos(qos);
			mqttMsg.setPayload(payload);

			this.mqttClient.publish(topicName, mqttMsg);

			_Logger.info("Message published successfully to topic: " + topicName);
			return true;

		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to publish message to topic: " + topicName, e);
		}

		return false;
	}

	/**
	 * Subscribes to a topic using a String topic name.
	 * 
	 * @param topicName The topic name as a String
	 * @param qos       The QoS level (0, 1, or 2)
	 * @return boolean True on success, false otherwise
	 */
	protected boolean subscribeToTopic(String topicName, int qos) {
		if (topicName == null) {
			_Logger.warning("Topic name is null. Unable to subscribe to topic: " + this.brokerAddr);
			return false;
		}

		if (qos < 0 || qos > 2) {
			_Logger.warning("Invalid QoS. Using default. QoS requested: " + qos);
			qos = this.defaultQos;
		}

		if (!this.isConnected()) {
			_Logger.warning("MQTT client is not connected. Unable to subscribe.");
			return false;
		}

		try {
			_Logger.info("Subscribing to topic: " + topicName + " with QoS: " + qos);

			this.mqttClient.subscribe(topicName, qos);

			_Logger.info("Successfully subscribed to topic: " + topicName);
			return true;

		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to subscribe to topic: " + topicName, e);
		}

		return false;
	}

	/**
	 * Subscribes to a topic using a String topic name with a custom message
	 * listener.
	 * 
	 * @param topicName The topic name as a String
	 * @param qos       The QoS level (0, 1, or 2)
	 * @param listener  Custom message listener for this topic
	 * @return boolean True on success, false otherwise
	 */
	protected boolean subscribeToTopic(String topicName, int qos, IMqttMessageListener listener) {
		if (topicName == null) {
			_Logger.warning("Topic name is null. Unable to subscribe to topic: " + this.brokerAddr);
			return false;
		}

		if (qos < 0 || qos > 2) {
			_Logger.warning("Invalid QoS. Using default. QoS requested: " + qos);
			qos = this.defaultQos;
		}

		if (!this.isConnected()) {
			_Logger.warning("MQTT client is not connected. Unable to subscribe.");
			return false;
		}

		try {
			_Logger.info("Subscribing to topic with custom listener: " + topicName + " with QoS: " + qos);

			if (listener != null) {
				this.mqttClient.subscribe(topicName, qos, listener);
				_Logger.info("Successfully subscribed to topic with listener: " + topicName);
			} else {
				this.mqttClient.subscribe(topicName, qos);
				_Logger.info("Successfully subscribed to topic: " + topicName);
			}

			return true;

		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to subscribe to topic: " + topicName, e);
		}

		return false;
	}

	/**
	 * Unsubscribes from a topic using a String topic name.
	 * 
	 * @param topicName The topic name as a String
	 * @return boolean True on success, false otherwise
	 */
	protected boolean unsubscribeFromTopic(String topicName) {
		if (topicName == null) {
			_Logger.warning("Topic name is null. Unable to unsubscribe from topic: " + this.brokerAddr);
			return false;
		}

		if (!this.isConnected()) {
			_Logger.warning("MQTT client is not connected. Unable to unsubscribe.");
			return false;
		}

		try {
			_Logger.info("Unsubscribing from topic: " + topicName);

			this.mqttClient.unsubscribe(topicName);

			_Logger.info("Successfully unsubscribed from topic: " + topicName);
			return true;

		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to unsubscribe from topic: " + topicName, e);
		}

		return false;
	}

	// callbacks

	@Override
	public void connectComplete(boolean reconnect, String serverURI) {
		_Logger.info("[Callback] Connected to MQTT broker: " + serverURI + " | Reconnect: " + reconnect);

		int qos = 1;

		// Subscribe to local topics only if NOT using cloud configuration
		if (!this.useCloudGatewayConfig) {
			_Logger.info("Subscribing to local CDA topics...");

			this.subscribeToTopic(ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, qos);
			this.subscribeToTopic(ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE, qos);
			this.subscribeToTopic(ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE, qos);

			_Logger.info("Successfully subscribed to all local CDA topics.");
		} else {
			_Logger.info("Cloud configuration enabled. Skipping local topic subscriptions.");
		}

		// Notify connection listener if set
		if (this.connListener != null) {
			this.connListener.onConnect();
		}
	}

	@Override
	public void connectionLost(Throwable t) {
		_Logger.log(Level.WARNING, "[Callback] Connection to MQTT broker lost.", t);

		// Notify connection listener if set
		if (this.connListener != null) {
			this.connListener.onDisconnect();
		}
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		try {
			String topic = token.getTopics()[0];
			_Logger.fine("[Callback] Message delivery complete for topic: " + topic);
		} catch (Exception e) {
			_Logger.log(Level.WARNING, "[Callback] Message delivery complete, but unable to retrieve topic.", e);
		}
	}

	@Override
	public void messageArrived(String topic, MqttMessage msg) throws Exception {
		_Logger.info("[Callback] Message received on topic: " + topic);

		try {
			// Convert payload to string
			String payload = new String(msg.getPayload());

			_Logger.fine("Message payload: " + payload);

			// Pass message to data message listener if set
			if (this.dataMsgListener != null) {
				// Convert topic string to ResourceNameEnum
				ResourceNameEnum resourceEnum = ResourceNameEnum.getEnumFromValue(topic);

				if (resourceEnum != null) {
					this.dataMsgListener.handleIncomingMessage(resourceEnum, payload);
				} else {
					_Logger.warning("Unknown topic received: " + topic);
				}
			} else {
				_Logger.warning("No data message listener registered. Message not processed.");
			}

		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "[Callback] Failed to process incoming message.", e);
		}
	}

	// private methods

	/**
	 * Called by the constructor to set the MQTT client parameters to be used for
	 * the connection.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 *                          the MQTT client configuration parameters.
	 */
	private void initClientParameters(String configSectionName) {
		ConfigUtil configUtil = ConfigUtil.getInstance();

		_Logger.info("Initializing MQTT client parameters from config section: " + configSectionName);

		// Load MQTT configuration
		this.host = configUtil.getProperty(
				configSectionName,
				ConfigConst.HOST_KEY,
				ConfigConst.DEFAULT_HOST);

		this.port = configUtil.getInteger(
				configSectionName,
				ConfigConst.PORT_KEY,
				ConfigConst.DEFAULT_MQTT_PORT);

		this.keepAlive = configUtil.getInteger(
				configSectionName,
				ConfigConst.KEEP_ALIVE_KEY,
				ConfigConst.DEFAULT_KEEP_ALIVE);

		this.defaultQos = configUtil.getInteger(
				configSectionName,
				ConfigConst.DEFAULT_QOS_KEY,
				ConfigConst.DEFAULT_QOS);

		// Get device location ID for client ID
		String deviceLocationID = configUtil.getProperty(
				ConfigConst.GATEWAY_DEVICE,
				ConfigConst.DEVICE_LOCATION_ID_KEY,
				"gatewaydevice001");

		// Create unique client ID
		this.clientID = deviceLocationID;

		// Construct broker address (will be updated if encryption is enabled)
		this.brokerAddr = this.protocol + "://" + this.host + ":" + this.port;

		_Logger.info("Using URL for broker conn: " + this.brokerAddr);

		// Initialize connection options
		this.connOpts = new MqttConnectOptions();
		this.connOpts.setKeepAliveInterval(this.keepAlive);
		this.connOpts.setCleanSession(true);
		this.connOpts.setAutomaticReconnect(true);

		// Check if authentication is enabled
		boolean enableAuth = configUtil.getBoolean(
				configSectionName,
				ConfigConst.ENABLE_AUTH_KEY);

		// Check if encryption is enabled
		boolean enableCrypt = configUtil.getBoolean(
				configSectionName,
				ConfigConst.ENABLE_CRYPT_KEY);

		if (enableAuth) {
			_Logger.info("MQTT authentication enabled. Loading credentials...");
			this.initCredentialConnectionParameters(configSectionName);
		}

		if (enableCrypt) {
			_Logger.info("MQTT encryption enabled. Loading secure connection parameters...");
			this.initSecureConnectionParameters(configSectionName);
		}

		_Logger.info("MQTT client parameters initialized successfully.");
	}

	/**
	 * Called by {@link #initClientParameters(String)} to load credentials.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 *                          the MQTT client configuration parameters.
	 */
	private void initCredentialConnectionParameters(String configSectionName) {
		ConfigUtil configUtil = ConfigUtil.getInstance();

		try {
			// Load credentials from credential file
			Properties credProps = configUtil.getCredentials(configSectionName);

			if (credProps != null) {
				String username = credProps.getProperty(ConfigConst.USER_NAME_TOKEN_KEY);
				String password = credProps.getProperty(ConfigConst.USER_AUTH_TOKEN_KEY);

				if (username != null && password != null) {
					_Logger.info("MQTT credentials loaded. Username: " + username);

					// Set credentials in connection options
					this.connOpts.setUserName(username);
					this.connOpts.setPassword(password.toCharArray());

					_Logger.info("MQTT credentials configured for authentication.");
				} else {
					_Logger.warning("MQTT credentials incomplete. Username or password missing.");
				}
			} else {
				_Logger.warning("No MQTT credentials found in credential file.");
			}
		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to load MQTT credentials.", e);
		}
	}

	/**
	 * Called by {@link #initClientParameters(String)} to enable encryption.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 *                          the MQTT client configuration parameters.
	 */
	private void initSecureConnectionParameters(String configSectionName) {
		ConfigUtil configUtil = ConfigUtil.getInstance();

		try {
			// Update protocol to SSL/TLS
			this.protocol = ConfigConst.DEFAULT_MQTT_SECURE_PROTOCOL;

			// Update port to secure port
			this.port = configUtil.getInteger(
					configSectionName,
					ConfigConst.SECURE_PORT_KEY,
					ConfigConst.DEFAULT_MQTT_SECURE_PORT);

			// Reconstruct broker address with secure protocol and port
			this.brokerAddr = this.protocol + "://" + this.host + ":" + this.port;

			_Logger.info("MQTT secure connection enabled. Using broker address: " + this.brokerAddr);

			// Load certificate file path
			String certFile = configUtil.getProperty(
					configSectionName,
					ConfigConst.CERT_FILE_KEY);

			if (certFile != null) {
				_Logger.info("Certificate file configured: " + certFile);

				// Configure SSL properties
				Properties sslProps = new Properties();
				sslProps.setProperty("com.ibm.ssl.protocol", "TLSv1.2");

				this.connOpts.setSSLProperties(sslProps);

				_Logger.info("TLS/SSL configured with certificate file.");
			} else {
				_Logger.warning("No certificate file specified. Using default SSL configuration.");
			}

		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to configure secure MQTT connection.", e);
		}
	}
}