/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 - 2025 by Andrew D. King
 */

package programmingtheiot.common;

/**
 * Interface for connection event notifications.
 * Allows external listeners to be notified of connection state changes.
 */
public interface IConnectionListener {
    /**
     * Called when a connection is successfully established.
     */
    public void onConnect();

    /**
     * Called when a connection is lost or disconnected.
     */
    public void onDisconnect();
}