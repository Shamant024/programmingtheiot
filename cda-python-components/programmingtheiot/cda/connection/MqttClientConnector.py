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
import ssl

from programmingtheiot.common.ConfigUtil import ConfigUtil
from programmingtheiot.common.ResourceNameEnum import ResourceNameEnum
from programmingtheiot.common.IDataMessageListener import IDataMessageListener

import programmingtheiot.common.ConfigConst as ConfigConst

from programmingtheiot.cda.connection.IPubSubClient import IPubSubClient

class MqttClientConnector(IPubSubClient):
	"""
	MQTT client connector for CDA to communicate with MQTT broker.
	Handles connection, publishing, subscribing, and message callbacks.
	"""
	
	def __init__(self, clientID: str = None):
		"""
		Constructor for MqttClientConnector.
		
		@param clientID Optional client ID (uses config if not provided)
		"""
		# Initialize configuration
		self.config = ConfigUtil()
		
		# Get MQTT broker configuration
		self.host = self.config.getProperty(
			section=ConfigConst.MQTT_GATEWAY_SERVICE,
			key=ConfigConst.HOST_KEY,
			defaultVal=ConfigConst.DEFAULT_HOST)
		
		self.port = self.config.getInteger(
			section=ConfigConst.MQTT_GATEWAY_SERVICE,
			key=ConfigConst.PORT_KEY,
			defaultVal=ConfigConst.DEFAULT_MQTT_PORT)
		
		self.keepAlive = self.config.getInteger(
			section=ConfigConst.MQTT_GATEWAY_SERVICE,
			key=ConfigConst.KEEP_ALIVE_KEY,
			defaultVal=ConfigConst.DEFAULT_KEEP_ALIVE)
		
		# Get encryption and auth settings
		self.enableEncryption = self.config.getBoolean(
			section=ConfigConst.MQTT_GATEWAY_SERVICE,
			key=ConfigConst.ENABLE_CRYPT_KEY)
		
		self.pemFileName = self.config.getProperty(
			section=ConfigConst.MQTT_GATEWAY_SERVICE,
			key=ConfigConst.CERT_FILE_KEY)
		
		# Get client ID
		if not clientID:
			self.clientID = self.config.getProperty(
				section=ConfigConst.CONSTRAINED_DEVICE,
				key=ConfigConst.DEVICE_LOCATION_ID_KEY,
				defaultVal='constraineddevice001')
		else:
			self.clientID = clientID
		
		# Initialize MQTT client
		self.mqttClient = None
		self.dataMsgListener = None
		
		logging.info('      MQTT Client ID:   ' + self.clientID)
		logging.info('      MQTT Broker Host: ' + self.host)
		logging.info('      MQTT Broker Port: ' + str(self.port))
		logging.info('      MQTT Keep Alive:  ' + str(self.keepAlive))
	
	def connectClient(self) -> bool:
		"""
		Connect to the MQTT broker.
		
		@return bool True if connection successful, False otherwise
		"""
		if not self.mqttClient:
			# Create MQTT client with clean session
			self.mqttClient = mqttClient.Client(
				client_id=self.clientID,
				clean_session=True)
			
			# Setup TLS encryption if enabled
			try:
				if self.enableEncryption:
					logging.info("Enabling TLS encryption...")
					
					self.port = self.config.getInteger(
						section=ConfigConst.MQTT_GATEWAY_SERVICE,
						key=ConfigConst.SECURE_PORT_KEY,
						defaultVal=ConfigConst.DEFAULT_MQTT_SECURE_PORT)
					
					# Set TLS configuration
					self.mqttClient.tls_set(
						self.pemFileName,
						tls_version=ssl.PROTOCOL_TLS_CLIENT)
					
					logging.info("TLS encryption enabled.")
			except Exception as e:
				logging.warning("Failed to enable TLS encryption: " + str(e))
			
			# Set callback methods
			self.mqttClient.on_connect = self.onConnect
			self.mqttClient.on_disconnect = self.onDisconnect
			self.mqttClient.on_message = self.onMessage
			self.mqttClient.on_publish = self.onPublish
			self.mqttClient.on_subscribe = self.onSubscribe
		
		if not self.mqttClient.is_connected():
			logging.info('Connecting to MQTT broker at host: ' + self.host + ' port: ' + str(self.port))
			
			# Connect to broker
			self.mqttClient.connect(self.host, self.port, self.keepAlive)
			
			# Start network loop
			self.mqttClient.loop_start()
			
			return True
		else:
			logging.warning('MQTT client is already connected.')
			return False
	
	def disconnectClient(self) -> bool:
		"""
		Disconnect from the MQTT broker.
		
		@return bool True if disconnection successful, False otherwise
		"""
		if self.mqttClient and self.mqttClient.is_connected():
			logging.info('Disconnecting from MQTT broker...')
			
			# Stop network loop
			self.mqttClient.loop_stop()
			
			# Disconnect from broker
			self.mqttClient.disconnect()
			
			return True
		else:
			logging.warning('MQTT client is not connected.')
			return False
	
	def publishMessage(self, resource: ResourceNameEnum, msg: str, qos: int = ConfigConst.DEFAULT_QOS) -> bool:
		"""
		Publish a message to the specified resource topic.
		
		@param resource The resource enumeration for the topic
		@param msg The message payload (JSON string)
		@param qos Quality of Service level (0, 1, or 2)
		@return bool True if publish successful, False otherwise
		"""
		if not resource:
			logging.warning('Resource is None. Cannot publish message.')
			return False
		
		if not msg:
			logging.warning('Message is None or empty. Cannot publish message.')
			return False
		
		if qos < 0 or qos > 2:
			qos = ConfigConst.DEFAULT_QOS
		
		# Publish message
		logging.info('Publishing message to topic: ' + resource.value)
		logging.debug('Message payload: ' + msg)
		
		msgInfo = self.mqttClient.publish(
			topic=resource.value,
			payload=msg,
			qos=qos)
		
		return True
	
	def subscribeToTopic(self, resource: ResourceNameEnum, qos: int = ConfigConst.DEFAULT_QOS) -> bool:
		"""
		Subscribe to a topic for receiving messages.
		
		@param resource The resource enumeration for the topic
		@param qos Quality of Service level (0, 1, or 2)
		@return bool True if subscription successful, False otherwise
		"""
		if not resource:
			logging.warning('Resource is None. Cannot subscribe to topic.')
			return False
		
		if qos < 0 or qos > 2:
			qos = ConfigConst.DEFAULT_QOS
		
		logging.info('Subscribing to topic: ' + resource.value + ' with QoS: ' + str(qos))
		
		# Subscribe to topic
		result, mid = self.mqttClient.subscribe(resource.value, qos)
		
		if result == mqttClient.MQTT_ERR_SUCCESS:
			logging.info('Successfully subscribed to topic: ' + resource.value)
			return True
		else:
			logging.error('Failed to subscribe to topic: ' + resource.value)
			return False
	
	def unsubscribeFromTopic(self, resource: ResourceNameEnum) -> bool:
		"""
		Unsubscribe from a topic.
		
		@param resource The resource enumeration for the topic
		@return bool True if unsubscription successful, False otherwise
		"""
		if not resource:
			logging.warning('Resource is None. Cannot unsubscribe from topic.')
			return False
		
		logging.info('Unsubscribing from topic: ' + resource.value)
		
		# Unsubscribe from topic
		result, mid = self.mqttClient.unsubscribe(resource.value)
		
		if result == mqttClient.MQTT_ERR_SUCCESS:
			logging.info('Successfully unsubscribed from topic: ' + resource.value)
			return True
		else:
			logging.error('Failed to unsubscribe from topic: ' + resource.value)
			return False
	
	def setDataMessageListener(self, listener: IDataMessageListener) -> bool:
		"""
		Set the data message listener for handling incoming messages.
		
		@param listener The IDataMessageListener implementation
		@return bool True if listener set successfully, False otherwise
		"""
		if listener:
			self.dataMsgListener = listener
			logging.info('Data message listener set successfully.')
			return True
		else:
			logging.warning('No data message listener provided.')
			return False
	
	# MQTT Callback Methods
	
	def onConnect(self, client, userdata, flags, rc):
		"""
		Callback when connection to MQTT broker is established.
		
		@param client The MQTT client instance
		@param userdata The private user data
		@param flags Response flags from broker
		@param rc Connection result code (0 = success)
		"""
		if rc == 0:
			logging.info('[Callback] Connected to MQTT broker. Result code: ' + str(rc))
			logging.info('MQTT connection successful.')
		else:
			logging.error('[Callback] Failed to connect to MQTT broker. Result code: ' + str(rc))
	
	def onDisconnect(self, client, userdata, rc):
		"""
		Callback when disconnected from MQTT broker.
		
		@param client The MQTT client instance
		@param userdata The private user data
		@param rc Disconnection result code
		"""
		logging.info('[Callback] Disconnected from MQTT broker. Result code: ' + str(rc))
	
	def onMessage(self, client, userdata, msg):
		"""
		Callback when a message is received on any subscribed topic.
		
		@param client The MQTT client instance
		@param userdata The private user data
		@param msg The message received (contains topic and payload)
		"""
		logging.info('[Callback] Message received on topic: ' + msg.topic)
		
		# Decode payload from bytes to string
		payload = msg.payload.decode('utf-8')
		logging.info('Message payload: ' + payload)
		
		# If a data message listener is set, pass the message to it
		if self.dataMsgListener:
			try:
				# Find matching ResourceNameEnum by comparing topic string
				resourceEnum = None
				
				for resource in ResourceNameEnum:
					if resource.value == msg.topic:
						resourceEnum = resource
						break
				
				if resourceEnum:
					logging.info('Matched topic to resource: ' + str(resourceEnum))
					self.dataMsgListener.handleIncomingMessage(resourceEnum, payload)
				else:
					logging.warning('Unknown topic received: ' + msg.topic)
					
			except Exception as e:
				logging.error('Failed to handle incoming message: ' + str(e))
		else:
			logging.warning('No data message listener registered. Message not processed.')
	
	def onPublish(self, client, userdata, mid):
		"""
		Callback when a message has been published.
		
		@param client The MQTT client instance
		@param userdata The private user data
		@param mid The message ID of the published message
		"""
		logging.debug('[Callback] Message published with message ID: ' + str(mid))
	
	def onSubscribe(self, client, userdata, mid, granted_qos):
		"""
		Callback when subscription is confirmed.
		
		@param client The MQTT client instance
		@param userdata The private user data
		@param mid The message ID
		@param granted_qos The QoS level granted by broker
		"""
		logging.info('[Callback] Subscription successful with message ID: ' + str(mid) + ' and QoS: ' + str(granted_qos))