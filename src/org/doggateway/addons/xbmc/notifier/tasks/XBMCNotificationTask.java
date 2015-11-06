/*
 * Dog - Addons - XBMC Notifier
 * 
 * Copyright (c) 2013-2015 Dario Bonino
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package org.doggateway.addons.xbmc.notifier.tasks;

import it.polito.elite.dog.core.library.model.notification.NonParametricNotification;
import it.polito.elite.dog.core.library.model.notification.Notification;
import it.polito.elite.dog.core.library.util.LogHelper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;

import org.osgi.service.log.LogService;

/**
 * XBMCNotificationTask, takes care of delivering a Dog notification to a set of
 * XBMC servers through the XBMC JSON-RPC API.
 * 
 * @author <a href="mailto:dario.bonino@gmail.com">Dario Bonino</a>
 *
 */
public class XBMCNotificationTask implements Runnable
{
	// The Notification to be delivered
	private Notification notification;

	// The class logger
	private LogHelper logger;

	// The set of XBMC servers to which deliver notifications
	private Set<String> xbmcServers;

	/**
	 * Class constructor, builds an XBMCNotificationTask with a given
	 * Notification to be delivered to a given set of Servers.
	 */
	public XBMCNotificationTask(Notification notification,
			Set<String> xbmcServers, LogHelper logger)
	{
		// store the notification to forward
		this.notification = notification;

		// store the logger reference
		this.logger = logger;

		// store tge reference to the servers
		this.xbmcServers = xbmcServers;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run()
	{
		// take the notification and build the message to send to XBMC
		String message = this.getNotificationValueAsString(this.notification);

		// prepare the request body
		String requestBody = "{\"jsonrpc\":\"2.0\",\"method\":\"GUI.ShowNotification\",\"id\":1,"
				+ "\"params\":{\"title\":\"Dog says:\",\"message\":\""
				+ message + "\", \"image\":\"info\"}}";

		// for all connected XBMC servers
		for (String xbmcServer : this.xbmcServers)
		{
			try
			{
				// transform the server url string into a URL object, the
				// request parameter shall be equal to the called method.
				URL xbmcServerUrl = new URL(xbmcServer
						+ "/jsonrpc?GUI.ShowNotification");

				// get a connection
				HttpURLConnection connection = (HttpURLConnection) xbmcServerUrl
						.openConnection();

				// set the POST method
				connection.setRequestMethod("POST");
				connection.setDoOutput(true);

				// set the content type
				connection.setRequestProperty("Content-Type",
						"application/json");

				// write on the outputstream
				OutputStream oStream = connection.getOutputStream();
				BufferedWriter writer = new BufferedWriter(
						new OutputStreamWriter(oStream));
				writer.write(requestBody);
				
				// flush and close
				writer.flush();
				writer.close();
				oStream.flush();
				oStream.close();

				// connect: actually send the request
				connection.connect();

				// get response code and log
				this.logger.log(LogService.LOG_INFO, "Response code:"
						+ connection.getResponseCode());
			}
			catch (IOException e)
			{
				//log the error
				this.logger.log(LogService.LOG_ERROR, "Error while delivering notfications to XBMC["+xbmcServer+"] ",e);
			}

		}
	}

	/**
	 * Provides a string representation of the notification value (TODO: check
	 * whether this can be moved in the auto-generated class in the core
	 * library)
	 * 
	 * @param notification
	 * @return
	 */
	private String getNotificationValueAsString(Notification notification)
	{
		String notificationAsString = "";

		// switch between parametric (having value) and non parametric
		// notifications
		if (notification instanceof NonParametricNotification)
		{
			try
			{
				String name = (String) notification.getClass()
						.getDeclaredField("notificationName").get(notification);

				notificationAsString = "The " + notification.getDeviceUri()
						+ " is now in " + name + " state.";
			}
			catch (IllegalArgumentException | IllegalAccessException
					| NoSuchFieldException | SecurityException e)
			{
				this.logger.log(LogService.LOG_ERROR,
						"Unable to get Non Parametric notification value.", e);
			}
		}
		else
		{
			// the value shall be retrieved
		}

		return notificationAsString;
	}

}
