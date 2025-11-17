#####
# 
# This class is part of the Programming the Internet of Things
# project, and is available via the MIT License, which can be
# found in the LICENSE file at the top level of this repository.
# 
# You may find it more helpful to your design to adjust the
# functionality, constants and interfaces (if there are any)
# provided within in order to meet the needs of your specific
# Programming the Internet of Things project.
# 

import logging
import paho.mqtt.client as mqttClient

import programmingtheiot.common.ConfigConst as ConfigConst

from programmingtheiot.common.ConfigUtil import ConfigUtil
from programmingtheiot.common.IDataMessageListener import IDataMessageListener
from programmingtheiot.common.ResourceNameEnum import ResourceNameEnum

from programmingtheiot.cda.connection.IPubSubClient import IPubSubClient

class MqttClientConnector(IPubSubClient):
	"""
	MQTT Client Connector for CDA to communicate with MQTT broker (GDA or Cloud).
	
	This class handles:
	- Connection/disconnection to MQTT broker
	- Publishing messages to topics
	- Subscribing to topics
	- Handling incoming messages via callbacks
	"""

	def __init__(self, clientID: str = None):
		"""
		Default constructor. This will set remote broker information and client connection
		information based on the default configuration file contents.
		
		@param clientID Defaults to None. Can be set by caller. If this is used, it's
		critically important that a unique, non-conflicting name be used so to avoid
		causing the MQTT broker to disconnect any client using the same name. With
		auto-reconnect enabled, this can cause a race condition where each client with
		the same clientID continuously attempts to re-connect, causing the broker to
		disconnect the previous instance.
		"""
		# Initialize ConfigUtil to read configuration
		self.config = ConfigUtil()
		
		# Load MQTT configuration from PiotConfig.props
		self.host = self.config.getProperty(
			ConfigConst.MQTT_GATEWAY_SERVICE, 
			ConfigConst.HOST_KEY, 
			ConfigConst.DEFAULT_HOST
		)
		
		self.port = self.config.getInteger(
			ConfigConst.MQTT_GATEWAY_SERVICE, 
			ConfigConst.PORT_KEY, 
			ConfigConst.DEFAULT_MQTT_PORT
		)
		
		self.keepAlive = self.config.getInteger(
			ConfigConst.MQTT_GATEWAY_SERVICE, 
			ConfigConst.KEEP_ALIVE_KEY, 
			ConfigConst.DEFAULT_KEEP_ALIVE
		)
		
		self.defaultQos = self.config.getInteger(
			ConfigConst.MQTT_GATEWAY_SERVICE, 
			ConfigConst.DEFAULT_QOS_KEY, 
			ConfigConst.DEFAULT_QOS
		)
		
		# Get device location ID for topic construction
		self.locationID = self.config.getProperty(
			ConfigConst.CONSTRAINED_DEVICE,
			ConfigConst.DEVICE_LOCATION_ID_KEY,
			"constraineddevice001"
		)
		
		# Initialize the Paho MQTT client
		# If clientID is provided, use it; otherwise use locationID
		if clientID:
			self.clientID = clientID
		else:
			self.clientID = self.locationID
		
		# Create MQTT client instance (using MQTT v3.1.1) - FIXED: Added CallbackAPIVersion
		self.mc = mqttClient.Client(mqttClient.CallbackAPIVersion.VERSION1, client_id=self.clientID, clean_session=True)
		
		# Set callback functions
		self.mc.on_connect = self.onConnect
		self.mc.on_disconnect = self.onDisconnect
		self.mc.on_message = self.onMessage
		self.mc.on_publish = self.onPublish
		self.mc.on_subscribe = self.onSubscribe
		
		# Data message listener reference
		self.dataMsgListener = None
		
		logging.info('\tMQTT Client ID:   ' + self.clientID)
		logging.info('\tMQTT Broker Host: ' + self.host)
		logging.info('\tMQTT Broker Port: ' + str(self.port))
		logging.info('\tMQTT Keep Alive:  ' + str(self.keepAlive))

	def connectClient(self) -> bool:
		"""
		Connects to the MQTT broker.
		
		@return bool True on successful connection initiation; False otherwise.
		"""
		if not self.mc:
			logging.warning("MQTT client is not initialized.")
			return False
		
		try:
			logging.info("Connecting to MQTT broker at host: " + self.host + " port: " + str(self.port))
			
			# Connect to the broker
			self.mc.connect(self.host, self.port, self.keepAlive)
			
			# Start the network loop in a separate thread
			self.mc.loop_start()
			
			return True
		except Exception as e:
			logging.error("Failed to connect to MQTT broker: " + str(e))
			return False
		
	def disconnectClient(self) -> bool:
		"""
		Disconnects from the MQTT broker.
		
		@return bool True on successful disconnection; False otherwise.
		"""
		if not self.mc:
			logging.warning("MQTT client is not initialized.")
			return False
		
		try:
			logging.info("Disconnecting from MQTT broker...")
			
			# Stop the network loop
			self.mc.loop_stop()
			
			# Disconnect from broker
			self.mc.disconnect()
			
			return True
		except Exception as e:
			logging.error("Failed to disconnect from MQTT broker: " + str(e))
			return False
		
	def onConnect(self, client, userdata, flags, rc):
		"""
		Callback when connection to broker is established.
		
		@param client The MQTT client instance.
		@param userdata The private user data.
		@param flags Response flags sent by the broker.
		@param rc The connection result code.
		"""
		logging.info('[Callback] Connected to MQTT broker. Result code: ' + str(rc))
		
		# Result codes:
		# 0: Connection successful
		# 1: Connection refused - incorrect protocol version
		# 2: Connection refused - invalid client identifier
		# 3: Connection refused - server unavailable
		# 4: Connection refused - bad username or password
		# 5: Connection refused - not authorized
		
		if rc == 0:
			logging.info("MQTT connection successful.")
			# TODO: Subscribe to topics here if needed
		else:
			logging.warning("MQTT connection failed with result code: " + str(rc))
		
	def onDisconnect(self, client, userdata, rc):
		"""
		Callback when disconnected from broker.
		
		@param client The MQTT client instance.
		@param userdata The private user data.
		@param rc The disconnection result code.
		"""
		logging.info('[Callback] Disconnected from MQTT broker. Result code: ' + str(rc))
		
		if rc != 0:
			logging.warning("Unexpected disconnection from MQTT broker.")
		
	def onMessage(self, client, userdata, msg):
		"""
		Callback when a message is received on any subscribed topic.
		
		@param client The MQTT client instance.
		@param userdata The private user data.
		@param msg The message received (contains topic and payload).
		"""
		logging.info('[Callback] Message received on topic: ' + msg.topic)
		
		# Decode payload from bytes to string
		payload = msg.payload.decode('utf-8')
		logging.info('Message payload: ' + payload)
		
		# If a data message listener is set, pass the message to it
		if self.dataMsgListener:
			try:
				# Convert topic string to ResourceNameEnum - FIXED: Changed to getResourceNameByValue
				resourceEnum = ResourceNameEnum.getResourceNameByValue(msg.topic)
				
				if resourceEnum:
					self.dataMsgListener.handleIncomingMessage(resourceEnum, payload)
				else:
					logging.warning("Unknown topic received: " + msg.topic)
			except Exception as e:
				logging.error("Failed to handle incoming message: " + str(e))
		else:
			logging.warning("No data message listener registered. Message not processed.")
			
	def onPublish(self, client, userdata, mid):
		"""
		Callback when a message has been published.
		
		@param client The MQTT client instance.
		@param userdata The private user data.
		@param mid The message ID of the published message.
		"""
		logging.debug('[Callback] Message published with message ID: ' + str(mid))
	
	def onSubscribe(self, client, userdata, mid, granted_qos):
		"""
		Callback when subscription is acknowledged by broker.
		
		@param client The MQTT client instance.
		@param userdata The private user data.
		@param mid The message ID of the subscribe request.
		@param granted_qos The QoS levels granted by the broker for each subscription.
		"""
		logging.info('[Callback] Subscription successful with message ID: ' + str(mid) + ' and QoS: ' + str(granted_qos))
	
	def onActuatorCommandMessage(self, client, userdata, msg):
		"""
		This callback is defined as a convenience, but does not
		need to be used and can be ignored.
		
		It's simply an example for how you can create your own
		custom callback for incoming messages from a specific
		topic subscription (such as for actuator commands).
		
		@param client The client reference context.
		@param userdata The user reference context.
		@param msg The message context, including the embedded payload.
		"""
		logging.info('[Callback] Actuator command message received.')
		
		# Decode and log the payload
		payload = msg.payload.decode('utf-8')
		logging.info('Actuator command payload: ' + payload)
		
		# Process the actuator command through the data message listener
		if self.dataMsgListener:
			try:
				resourceEnum = ResourceNameEnum.getResourceNameByValue(msg.topic)
				if resourceEnum:
					self.dataMsgListener.handleIncomingMessage(resourceEnum, payload)
			except Exception as e:
				logging.error("Failed to handle actuator command message: " + str(e))
	
	def publishMessage(self, resource: ResourceNameEnum = None, msg: str = None, qos: int = ConfigConst.DEFAULT_QOS):
		"""
		Publishes a message to the specified topic.
		
		@param resource The ResourceNameEnum representing the topic.
		@param msg The message payload to publish (as a string).
		@param qos The Quality of Service level (0, 1, or 2).
		@return bool True on successful publish; False otherwise.
		"""
		if not resource:
			logging.warning("No resource specified for publish. Ignoring.")
			return False
		
		if not msg:
			logging.warning("No message specified for publish. Ignoring.")
			return False
		
		# Get the topic name from ResourceNameEnum - FIXED: Changed to .value property
		topic = resource.value
		
		try:
			logging.info("Publishing message to topic: " + topic)
			logging.debug("Message payload: " + msg)
			
			# Publish the message
			msgInfo = self.mc.publish(topic=topic, payload=msg, qos=qos)
			
			# Wait for publish to complete
			msgInfo.wait_for_publish()
			
			logging.info("Message published successfully to topic: " + topic)
			return True
			
		except Exception as e:
			logging.error("Failed to publish message to topic " + topic + ": " + str(e))
			return False
	
	def subscribeToTopic(self, resource: ResourceNameEnum = None, callback = None, qos: int = ConfigConst.DEFAULT_QOS):
		"""
		Subscribes to a topic.
		
		@param resource The ResourceNameEnum representing the topic.
		@param callback Optional custom callback for this specific topic.
		@param qos The Quality of Service level (0, 1, or 2).
		@return bool True on successful subscription; False otherwise.
		"""
		if not resource:
			logging.warning("No resource specified for subscription. Ignoring.")
			return False
		
		# Get the topic name from ResourceNameEnum - FIXED: Changed to .value property
		topic = resource.value
		
		try:
			logging.info("Subscribing to topic: " + topic + " with QoS: " + str(qos))
			
			# If a custom callback is provided, use it; otherwise use default onMessage
			if callback:
				self.mc.message_callback_add(topic, callback)
				logging.info("Custom callback added for topic: " + topic)
			
			# Subscribe to the topic
			self.mc.subscribe(topic, qos)
			
			logging.info("Successfully subscribed to topic: " + topic)
			return True
			
		except Exception as e:
			logging.error("Failed to subscribe to topic " + topic + ": " + str(e))
			return False
	
	def unsubscribeFromTopic(self, resource: ResourceNameEnum = None):
		"""
		Unsubscribes from a topic.
		
		@param resource The ResourceNameEnum representing the topic.
		@return bool True on successful unsubscription; False otherwise.
		"""
		if not resource:
			logging.warning("No resource specified for unsubscription. Ignoring.")
			return False
		
		# Get the topic name from ResourceNameEnum - FIXED: Changed to .value property
		topic = resource.value
		
		try:
			logging.info("Unsubscribing from topic: " + topic)
			
			# Unsubscribe from the topic
			self.mc.unsubscribe(topic)
			
			logging.info("Successfully unsubscribed from topic: " + topic)
			return True
			
		except Exception as e:
			logging.error("Failed to unsubscribe from topic " + topic + ": " + str(e))
			return False

	def setDataMessageListener(self, listener: IDataMessageListener = None) -> bool:
		"""
		Sets the data message listener for handling incoming messages.
		
		@param listener The IDataMessageListener implementation.
		@return bool True on success; False otherwise.
		"""
		if listener:
			self.dataMsgListener = listener
			logging.info("Data message listener set successfully.")
			return True
		else:
			logging.warning("No data message listener provided.")
			return False