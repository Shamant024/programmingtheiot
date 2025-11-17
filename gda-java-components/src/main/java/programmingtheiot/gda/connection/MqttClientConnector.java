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

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
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

	// constructors

	/**
	 * Default constructor.
	 * 
	 * Initializes the MQTT client with configuration from PiotConfig.props
	 */
	public MqttClientConnector() {
		super();

		ConfigUtil configUtil = ConfigUtil.getInstance();

		// Load MQTT configuration
		this.host = configUtil.getProperty(
				ConfigConst.MQTT_GATEWAY_SERVICE,
				ConfigConst.HOST_KEY,
				ConfigConst.DEFAULT_HOST);

		this.port = configUtil.getInteger(
				ConfigConst.MQTT_GATEWAY_SERVICE,
				ConfigConst.PORT_KEY,
				ConfigConst.DEFAULT_MQTT_PORT);

		this.keepAlive = configUtil.getInteger(
				ConfigConst.MQTT_GATEWAY_SERVICE,
				ConfigConst.KEEP_ALIVE_KEY,
				ConfigConst.DEFAULT_KEEP_ALIVE);

		this.defaultQos = configUtil.getInteger(
				ConfigConst.MQTT_GATEWAY_SERVICE,
				ConfigConst.DEFAULT_QOS_KEY,
				ConfigConst.DEFAULT_QOS);

		// Get device location ID for client ID
		String deviceLocationID = configUtil.getProperty(
				ConfigConst.GATEWAY_DEVICE,
				ConfigConst.DEVICE_LOCATION_ID_KEY,
				"gatewaydevice001");

		// Create unique client ID
		this.clientID = deviceLocationID;

		// Construct broker address
		this.brokerAddr = this.protocol + "://" + this.host + ":" + this.port;

		_Logger.info("Using URL for broker conn: " + this.brokerAddr);

		// Initialize other client parameters
		this.initClientParameters(ConfigConst.MQTT_GATEWAY_SERVICE);

		_Logger.info("MQTT Client ID: " + this.clientID);
		_Logger.info("MQTT Broker Host: " + this.host);
		_Logger.info("MQTT Broker Port: " + this.port);
		_Logger.info("MQTT Keep Alive: " + this.keepAlive);
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

				// Create connection options
				this.connOpts = new MqttConnectOptions();
				this.connOpts.setKeepAliveInterval(this.keepAlive);
				this.connOpts.setCleanSession(true);
				this.connOpts.setAutomaticReconnect(true);

				// Connect to broker
				this.mqttClient.connect(this.connOpts);

				_Logger.info("Connected to MQTT broker successfully.");
				return true;
			} else {
				_Logger.warning("MQTT client is already connected.");
				return false; // Return false if already connected
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

		if (!this.isConnected()) {
			_Logger.warning("MQTT client is not connected. Unable to publish message.");
			return false;
		}

		try {
			String topic = topicName.getResourceName();

			_Logger.info("Publishing message to topic: " + topic);
			_Logger.fine("Message payload: " + msg);

			// Create MQTT message
			MqttMessage mqttMsg = new MqttMessage(msg.getBytes());
			mqttMsg.setQos(qos);

			// Publish message
			this.mqttClient.publish(topic, mqttMsg);

			_Logger.info("Message published successfully to topic: " + topic);
			return true;

		} catch (MqttPersistenceException e) {
			_Logger.log(Level.SEVERE, "Failed to publish message due to persistence issue.", e);
			return false;
		} catch (MqttException e) {
			_Logger.log(Level.SEVERE, "Failed to publish message.", e);
			return false;
		}
	}

	@Override
	public boolean subscribeToTopic(ResourceNameEnum topicName, int qos) {
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to subscribe.");
			return false;
		}

		if (!this.isConnected()) {
			_Logger.warning("MQTT client is not connected. Unable to subscribe.");
			return false;
		}

		try {
			String topic = topicName.getResourceName();

			_Logger.info("Subscribing to topic: " + topic + " with QoS: " + qos);

			// Subscribe to topic
			this.mqttClient.subscribe(topic, qos);

			_Logger.info("Successfully subscribed to topic: " + topic);
			return true;

		} catch (MqttException e) {
			_Logger.log(Level.SEVERE, "Failed to subscribe to topic.", e);
			return false;
		}
	}

	@Override
	public boolean unsubscribeFromTopic(ResourceNameEnum topicName) {
		if (topicName == null) {
			_Logger.warning("Resource is null. Unable to unsubscribe.");
			return false;
		}

		if (!this.isConnected()) {
			_Logger.warning("MQTT client is not connected. Unable to unsubscribe.");
			return false;
		}

		try {
			String topic = topicName.getResourceName();

			_Logger.info("Unsubscribing from topic: " + topic);

			// Unsubscribe from topic
			this.mqttClient.unsubscribe(topic);

			_Logger.info("Successfully unsubscribed from topic: " + topic);
			return true;

		} catch (MqttException e) {
			_Logger.log(Level.SEVERE, "Failed to unsubscribe from topic.", e);
			return false;
		}
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

	// callbacks

	@Override
	public void connectComplete(boolean reconnect, String serverURI) {
		_Logger.info("[Callback] Connected to MQTT broker: " + serverURI + " | Reconnect: " + reconnect);

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

					// These will be set in MqttConnectOptions when connecting
					// For now, just log that credentials are available
					_Logger.info("MQTT credentials available for authentication.");
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

			// TODO: In future labs, configure SSL/TLS certificates and keystores
			_Logger.info("TLS/SSL certificate configuration will be implemented in future labs.");

		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to configure secure MQTT connection.", e);
		}
	}
}