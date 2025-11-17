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

import java.net.InetSocketAddress;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.config.Configuration;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.DataUtil;
import programmingtheiot.gda.connection.handlers.GenericCoapResourceHandler;

/**
 * CoAP Server Gateway for the Gateway Device Application (GDA).
 * 
 * This class implements a CoAP server using Eclipse Californium library.
 * It listens for CoAP requests from clients (such as the CDA) and handles
 * GET, POST, PUT, and DELETE operations on registered resources.
 * 
 * It also provides CoAP client functionality to send actuator commands
 * to the CDA.
 * 
 * Key Responsibilities:
 * - Initialize and manage the CoAP server lifecycle
 * - Register CoAP resources (handlers) for different endpoints
 * - Forward received data to DeviceDataManager via IDataMessageListener
 * - Send actuator commands to CDA via CoAP client requests
 * - Handle server start/stop operations
 */
public class CoapServerGateway {
	// static

	private static final Logger _Logger = Logger.getLogger(CoapServerGateway.class.getName());

	// params

	private CoapServer coapServer = null;
	private IDataMessageListener dataMsgListener = null;

	// Configuration parameters
	private String host = ConfigConst.DEFAULT_HOST;
	private int port = ConfigConst.DEFAULT_COAP_PORT;

	// constructors

	/**
	 * Default constructor.
	 * Creates a CoAP server with default resource handlers.
	 */
	public CoapServerGateway() {
		super();

		_Logger.info("Initializing CoAP server gateway...");

		// Load configuration
		ConfigUtil configUtil = ConfigUtil.getInstance();
		this.host = configUtil.getProperty(
				ConfigConst.COAP_GATEWAY_SERVICE,
				ConfigConst.HOST_KEY,
				ConfigConst.DEFAULT_HOST);

		this.port = configUtil.getInteger(
				ConfigConst.COAP_GATEWAY_SERVICE,
				ConfigConst.PORT_KEY,
				ConfigConst.DEFAULT_COAP_PORT);

		_Logger.info("CoAP server will bind to host: " + this.host + " on port: " + this.port);

		// Initialize the server with default resources
		initServer();

		_Logger.info("CoAP server gateway initialized.");
	}

	/**
	 * Constructor with data message listener.
	 * Creates a CoAP server with specified listener for handling incoming messages.
	 * 
	 * @param dataMsgListener The listener to handle incoming CoAP messages
	 */
	public CoapServerGateway(IDataMessageListener dataMsgListener) {
		super();

		_Logger.info("Initializing CoAP server gateway with data message listener...");

		this.dataMsgListener = dataMsgListener;

		// Load configuration
		ConfigUtil configUtil = ConfigUtil.getInstance();
		this.host = configUtil.getProperty(
				ConfigConst.COAP_GATEWAY_SERVICE,
				ConfigConst.HOST_KEY,
				ConfigConst.DEFAULT_HOST);

		this.port = configUtil.getInteger(
				ConfigConst.COAP_GATEWAY_SERVICE,
				ConfigConst.PORT_KEY,
				ConfigConst.DEFAULT_COAP_PORT);

		_Logger.info("CoAP server will bind to host: " + this.host + " on port: " + this.port);

		// Initialize the server with default resources
		initServer();

		_Logger.info("CoAP server gateway initialized with listener.");
	}

	// public methods

	/**
	 * Adds a resource handler to the CoAP server.
	 * This method creates the resource chain and adds it to the server, reusing
	 * existing parent resources to avoid conflicts.
	 * 
	 * @param resource The resource name enum representing the endpoint to add
	 */
	public void addResource(ResourceNameEnum resource) {
		if (resource != null) {
			_Logger.info("Adding CoAP resource handler: " + resource.getResourceName());

			// Get the resource name chain
			List<String> resourceNameChain = resource.getResourceNameChain();

			if (resourceNameChain == null || resourceNameChain.isEmpty()) {
				_Logger.warning("Resource name chain is empty for: " + resource.getResourceName());
				return;
			}

			// Start from server root
			Resource currentParent = this.coapServer.getRoot();

			// Build the hierarchy, reusing existing parents
			for (int i = 0; i < resourceNameChain.size(); i++) {
				String resourceName = resourceNameChain.get(i);
				boolean isLeafResource = (i == resourceNameChain.size() - 1);

				// Check if this resource already exists
				Resource existingResource = currentParent.getChild(resourceName);

				if (existingResource != null) {
					// Resource already exists, use it as parent for next level
					_Logger.fine("Resource already exists, reusing: " + resourceName);
					currentParent = existingResource;
				} else {
					// Create new resource
					Resource newResource = null;

					if (isLeafResource) {
						// Create the actual handler for the leaf resource
						_Logger.info("Creating handler for leaf resource: " + resourceName);

						GenericCoapResourceHandler handler = new GenericCoapResourceHandler(resource);

						// Set the data message listener on the handler
						if (this.dataMsgListener != null) {
							handler.setDataMessageListener(this.dataMsgListener);
							_Logger.info("Data message listener set on resource handler: " + resourceName);
						}

						newResource = handler;
					} else {
						// Create a simple parent resource (no handler logic)
						_Logger.fine("Creating parent resource: " + resourceName);
						newResource = new CoapResource(resourceName);
					}

					// Add to parent
					currentParent.add(newResource);
					currentParent = newResource;

					_Logger.fine("Added new resource: " + resourceName + " to parent");
				}
			}

			_Logger.info("CoAP resource added successfully: " + resource.getResourceName());
		} else {
			_Logger.warning("Cannot add null resource to CoAP server.");
		}
	}

	/**
	 * Checks if the server has a resource with the given name.
	 * 
	 * @param name The resource name to check
	 * @return boolean True if the resource exists, false otherwise
	 */
	public boolean hasResource(String name) {
		if (name != null && this.coapServer != null) {
			// Get the root resource and search for the named resource
			Resource resource = this.coapServer.getRoot().getChild(name);
			boolean hasResource = (resource != null);

			_Logger.fine("Resource '" + name + "' exists: " + hasResource);
			return hasResource;
		}

		return false;
	}

	/**
	 * Sends an actuator command to the CDA via CoAP PUT request.
	 * This method allows the GDA to notify the CDA of actuation updates.
	 * 
	 * @param resource The target resource (e.g., CDA_ACTUATOR_CMD_RESOURCE)
	 * @param payload  The JSON payload containing the actuator command
	 * @return boolean True if the command was sent successfully, false otherwise
	 */
	public boolean sendActuatorCommand(ResourceNameEnum resource, String payload) {
		if (resource == null || payload == null || payload.trim().isEmpty()) {
			_Logger.warning("Cannot send actuator command with null resource or empty payload.");
			return false;
		}

		try {
			// Build the CoAP URI for the CDA resource
			// Format: coap://host:port/PIOT/ConstrainedDevice/ActuatorCmd
			String coapUri = "coap://" + this.host + ":" + this.port + "/" + resource.getResourceName();

			_Logger.info("Sending actuator command to CDA via CoAP PUT: " + coapUri);
			_Logger.fine("Payload: " + payload);

			// Create a CoAP client for this request
			CoapClient client = new CoapClient(coapUri);

			// Send PUT request with JSON payload
			CoapResponse response = client.put(payload, MediaTypeRegistry.APPLICATION_JSON);

			// Check if we got a response
			if (response != null) {
				_Logger.info("Received CoAP response: " + response.getCode());
				_Logger.fine("Response payload: " + response.getResponseText());

				// Check if the response indicates success (2.xx codes)
				if (response.isSuccess()) {
					_Logger.info("Actuator command sent successfully to CDA.");
					client.shutdown();
					return true;
				} else {
					_Logger.warning("CDA returned error response: " + response.getCode());
					client.shutdown();
					return false;
				}
			} else {
				_Logger.warning("No response received from CDA for actuator command.");
				client.shutdown();
				return false;
			}

		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to send actuator command to CDA.", e);
			return false;
		}
	}

	/**
	 * Sets the data message listener for handling incoming CoAP messages.
	 * 
	 * @param listener The IDataMessageListener implementation
	 */
	public void setDataMessageListener(IDataMessageListener listener) {
		if (listener != null) {
			_Logger.info("Setting data message listener for CoAP server.");
			this.dataMsgListener = listener;
		} else {
			_Logger.warning("Attempted to set null data message listener. Ignoring.");
		}
	}

	/**
	 * Starts the CoAP server.
	 * This begins listening for incoming CoAP requests on the configured port.
	 * 
	 * @return boolean True if server started successfully, false otherwise
	 */
	public boolean startServer() {
		try {
			if (this.coapServer != null) {
				_Logger.info("Starting CoAP server on port: " + this.port);

				// Start the Californium CoAP server
				this.coapServer.start();

				_Logger.info("CoAP server started successfully.");
				_Logger.info("CoAP server is listening at: coap://" + this.host + ":" + this.port);

				return true;
			} else {
				_Logger.warning("CoAP server instance is null. Cannot start server.");
				return false;
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to start CoAP server.", e);
			return false;
		}
	}

	/**
	 * Stops the CoAP server.
	 * This stops listening for incoming CoAP requests and releases resources.
	 * 
	 * @return boolean True if server stopped successfully, false otherwise
	 */
	public boolean stopServer() {
		try {
			if (this.coapServer != null) {
				_Logger.info("Stopping CoAP server...");

				// Stop the Californium CoAP server
				this.coapServer.stop();

				_Logger.info("CoAP server stopped successfully.");
				return true;
			} else {
				_Logger.warning("CoAP server instance is null. Nothing to stop.");
				return false;
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to stop CoAP server.", e);
			return false;
		}
	}

	// private methods

	/**
	 * Initializes the CoAP server with optional resources.
	 * This method creates the CoapServer instance and registers default resources.
	 * 
	 * Resources registered:
	 * - GDA_MGMT_STATUS_MSG_RESOURCE: For receiving management status from CDA
	 * - GDA_MGMT_STATUS_CMD_RESOURCE: For receiving management commands from CDA
	 * - GDA_SYSTEM_PERF_MSG_RESOURCE: For receiving system performance data from
	 * CDA
	 * 
	 * @param resources Optional varargs of ResourceNameEnum to register at
	 *                  initialization
	 */
	private void initServer(ResourceNameEnum... resources) {
		try {
			_Logger.info("Initializing CoAP server instance...");

			// Create Californium configuration
			Configuration config = Configuration.createStandardWithoutFile();

			// Configure the CoAP port
			config.set(org.eclipse.californium.core.config.CoapConfig.COAP_PORT, this.port);

			// Create network configuration for the server
			CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
			builder.setConfiguration(config);
			builder.setInetSocketAddress(new InetSocketAddress(this.port));

			// Create the Californium CoapServer instance
			this.coapServer = new CoapServer(config);

			// Add the configured endpoint to the server
			this.coapServer.addEndpoint(builder.build());

			_Logger.info("CoAP server instance created on port: " + this.port);

			// Add default GDA resources for receiving updates from CDA
			_Logger.info("Adding default GDA resources for bidirectional communication...");

			// Resources for RECEIVING data FROM CDA
			addResource(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE);
			addResource(ResourceNameEnum.GDA_MGMT_STATUS_CMD_RESOURCE);
			addResource(ResourceNameEnum.GDA_SYSTEM_PERF_MSG_RESOURCE);

			_Logger.info("Default GDA resources added successfully.");

			// Add any additional resources passed during initialization
			if (resources != null && resources.length > 0) {
				_Logger.info("Adding " + resources.length + " additional resources to CoAP server...");

				for (ResourceNameEnum resource : resources) {
					if (resource != null) {
						addResource(resource);
					}
				}

				_Logger.info("All additional resources added to CoAP server.");
			}

		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to initialize CoAP server.", e);
		}
	}
}