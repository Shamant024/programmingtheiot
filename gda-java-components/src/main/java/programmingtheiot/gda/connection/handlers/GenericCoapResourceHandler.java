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

package programmingtheiot.gda.connection.handlers;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.DataUtil;

/**
 * Generic CoAP resource handler for the GDA.
 * 
 * This class handles incoming CoAP requests (GET, POST, PUT, DELETE) from
 * clients
 * such as the CDA. It processes the requests, extracts payloads, converts JSON
 * to
 * data objects, and forwards them to the DeviceDataManager via
 * IDataMessageListener.
 * 
 * Key Responsibilities:
 * - Handle GET requests (retrieve resource state)
 * - Handle POST requests (create new resource data)
 * - Handle PUT requests (update existing resource data)
 * - Handle DELETE requests (remove resource data)
 * - Convert JSON payloads to appropriate data objects
 * - Forward data to IDataMessageListener for processing
 * - Send appropriate CoAP response codes back to client
 */
public class GenericCoapResourceHandler extends CoapResource {
	// static

	private static final Logger _Logger = Logger.getLogger(GenericCoapResourceHandler.class.getName());

	// params

	private IDataMessageListener dataMsgListener = null;
	private ResourceNameEnum resource = null;

	// constructors

	/**
	 * Constructor.
	 * 
	 * @param resource Basically, the path (or topic)
	 */
	public GenericCoapResourceHandler(ResourceNameEnum resource) {
		// IMPORTANT: Only use the resourceType (last segment), not the full path
		// Californium doesn't support '/' in resource names
		this(resource.getResourceType());

		this.resource = resource;

		// Set observable if the resource supports it
		if (resource.isObservable()) {
			super.setObservable(true);
			_Logger.info("Resource is observable: " + resource.getResourceName());
		}
	}

	/**
	 * Constructor.
	 * 
	 * @param resourceName The name of the resource.
	 */
	public GenericCoapResourceHandler(String resourceName) {
		super(resourceName);

		_Logger.info("Created GenericCoapResourceHandler for resource: " + resourceName);
	}

	// public methods

	/**
	 * Handles DELETE requests.
	 * Typically used to remove or clear resource data.
	 * 
	 * @param context The CoAP exchange context containing request and response
	 */
	@Override
	public void handleDELETE(CoapExchange context) {
		_Logger.info("Handling DELETE request for resource: " + this.getName());

		// For now, we'll acknowledge the DELETE but not perform any action
		// Future implementations might clear cached data or remove resources

		try {
			// Respond with DELETED status code (2.02)
			context.respond(ResponseCode.DELETED);

			_Logger.info("DELETE request handled successfully for resource: " + this.getName());

		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to handle DELETE request for resource: " + this.getName(), e);
			context.respond(ResponseCode.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Handles GET requests.
	 * Retrieves the current state of the resource.
	 * 
	 * @param context The CoAP exchange context containing request and response
	 */
	@Override
	public void handleGET(CoapExchange context) {
		_Logger.info("Handling GET request for resource: " + this.getName());

		try {
			// For now, return a simple acknowledgment
			// Future implementations might return actual resource state

			String responseMessage = "GDA resource: " + this.getName() + " is available";

			// Respond with CONTENT status code (2.05) and the message
			context.respond(ResponseCode.CONTENT, responseMessage);

			_Logger.info("GET request handled successfully for resource: " + this.getName());

		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to handle GET request for resource: " + this.getName(), e);
			context.respond(ResponseCode.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Handles POST requests.
	 * Creates new resource data - this is the primary method for receiving
	 * updates from the CDA.
	 * 
	 * @param context The CoAP exchange context containing request and response
	 */
	@Override
	public void handlePOST(CoapExchange context) {
		_Logger.info("Handling POST request for resource: " + this.getName());

		try {
			// Extract the payload from the request
			String payload = context.getRequestText();

			_Logger.fine("POST payload received: " + payload);

			// Validate payload exists
			if (payload == null || payload.trim().isEmpty()) {
				_Logger.warning("POST request has empty payload for resource: " + this.getName());
				context.respond(ResponseCode.BAD_REQUEST, "Empty payload");
				return;
			}

			// Forward the message to the data message listener
			if (this.dataMsgListener != null && this.resource != null) {
				_Logger.info("Forwarding POST data to listener for resource: " + this.resource.getResourceName());

				boolean success = this.dataMsgListener.handleIncomingMessage(
						this.resource,
						payload);

				if (success) {
					// Respond with CHANGED status code (2.04) indicating success
					context.respond(ResponseCode.CHANGED);
					_Logger.info("POST request processed successfully for resource: " + this.getName());

					// If resource is observable, notify observers of the change
					if (this.resource.isObservable()) {
						super.changed();
						_Logger.fine("Notified observers of resource change: " + this.getName());
					}
				} else {
					_Logger.warning("Listener failed to process POST data for resource: " + this.getName());
					context.respond(ResponseCode.INTERNAL_SERVER_ERROR, "Failed to process data");
				}
			} else {
				_Logger.warning("No data message listener available for resource: " + this.getName());
				context.respond(ResponseCode.INTERNAL_SERVER_ERROR, "No listener available");
			}

		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to handle POST request for resource: " + this.getName(), e);
			context.respond(ResponseCode.INTERNAL_SERVER_ERROR, "Error processing request");
		}
	}

	/**
	 * Handles PUT requests.
	 * Updates existing resource data - similar to POST but typically for updates.
	 * 
	 * @param context The CoAP exchange context containing request and response
	 */
	@Override
	public void handlePUT(CoapExchange context) {
		_Logger.info("Handling PUT request for resource: " + this.getName());

		try {
			// Extract the payload from the request
			String payload = context.getRequestText();

			_Logger.fine("PUT payload received: " + payload);

			// Validate payload exists
			if (payload == null || payload.trim().isEmpty()) {
				_Logger.warning("PUT request has empty payload for resource: " + this.getName());
				context.respond(ResponseCode.BAD_REQUEST, "Empty payload");
				return;
			}

			// Forward the message to the data message listener
			if (this.dataMsgListener != null && this.resource != null) {
				_Logger.info("Forwarding PUT data to listener for resource: " + this.resource.getResourceName());

				boolean success = this.dataMsgListener.handleIncomingMessage(
						this.resource,
						payload);

				if (success) {
					// Respond with CHANGED status code (2.04) indicating success
					context.respond(ResponseCode.CHANGED);
					_Logger.info("PUT request processed successfully for resource: " + this.getName());

					// If resource is observable, notify observers of the change
					if (this.resource.isObservable()) {
						super.changed();
						_Logger.fine("Notified observers of resource change: " + this.getName());
					}
				} else {
					_Logger.warning("Listener failed to process PUT data for resource: " + this.getName());
					context.respond(ResponseCode.INTERNAL_SERVER_ERROR, "Failed to process data");
				}
			} else {
				_Logger.warning("No data message listener available for resource: " + this.getName());
				context.respond(ResponseCode.INTERNAL_SERVER_ERROR, "No listener available");
			}

		} catch (Exception e) {
			_Logger.log(Level.WARNING, "Failed to handle PUT request for resource: " + this.getName(), e);
			context.respond(ResponseCode.INTERNAL_SERVER_ERROR, "Error processing request");
		}
	}

	/**
	 * Sets the data message listener for this resource handler.
	 * The listener will be called when data is received via POST or PUT requests.
	 * 
	 * @param listener The IDataMessageListener implementation (typically
	 *                 DeviceDataManager)
	 */
	public void setDataMessageListener(IDataMessageListener listener) {
		if (listener != null) {
			_Logger.info("Setting data message listener for resource: " + this.getName());
			this.dataMsgListener = listener;
		} else {
			_Logger.warning("Attempted to set null data message listener for resource: " + this.getName());
		}
	}
}