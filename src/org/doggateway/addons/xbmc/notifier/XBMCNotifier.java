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
package org.doggateway.addons.xbmc.notifier;

import it.polito.elite.dog.core.library.model.notification.Notification;
import it.polito.elite.dog.core.library.util.LogHelper;

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.doggateway.addons.xbmc.notifier.tasks.XBMCNotificationTask;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;

/**
 * A class which forwards a selected subset of inner events (notifications) to
 * one or more XBMC instances by exploiting the XBMC JSON-RPC API.
 * 
 * In this first version we will assume to forward the same notifications to all
 * connected XBMC servers. Moreover we assume that notifications to forward are
 * filtered on the sole basis of their own topic and not on other parameters
 * such as the sender, etc.
 * 
 * @author <a href="mailto:dario.bonino@gmail.com">Dario Bonino</a>
 *
 */
public class XBMCNotifier implements ManagedService, EventHandler
{
	// constant keys for accessing configuration data
	public static final String XBMC_SERVERS = "xbmc_servers";
	public static final String TOPICS_TO_FORWARD = "topics_to_forward";

	// the class logger
	private LogHelper logger;

	// the context associated to this bundle
	private BundleContext context;

	// the set of topics to register to, hierarchy-based reduction might be
	// applied here
	private Set<String> topicsToListen;

	// the set of XBMC servers to which notifications must be forwarded
	private Set<String> xbmcServers;

	// the service registration handler
	private ServiceRegistration<EventHandler> eventHandler;

	// the event trnslation and delivery service
	private ExecutorService notificationDeliveryService;

	/**
	 * 
	 */
	public XBMCNotifier()
	{
		// create the needed data structures

		// use a Tree Set to account for order and to leave place for topic
		// hierarchy optimization
		this.topicsToListen = new TreeSet<String>();

		// here order does not matter
		this.xbmcServers = new HashSet<String>();

		// create the notification delivery service
		// the number of needed threads can be tuned
		// depending on the actual event delivery rate
		this.notificationDeliveryService = Executors.newSingleThreadExecutor();
	}

	@Override
	/**
	 * Handles configuration data provided through the configuration admin service (Configuration Admin specification)
	 */
	public void updated(Dictionary<String, ?> properties)
			throws ConfigurationException
	{
		this.logger.log(LogService.LOG_INFO, "Received configuration");
		
		// check not null
		if ((properties != null) && (!properties.isEmpty()))
		{
			// configuration data is supposed to be present...

			// get the XBMC servers, different server entries are identifed by
			// comma separation
			String xbmcServerListString = (String) properties
					.get(XBMCNotifier.XBMC_SERVERS);

			// split the string along commas
			String xbmcServerList[] = xbmcServerListString.split(",");

			// check not null or empty
			if ((xbmcServerList != null) && (xbmcServerList.length > 0))
			{
				// fill the inner server list
				// TODO: move to Java8 stream expressions
				for (int i = 0; i < xbmcServerList.length; i++)
				{
					// add to the inner list, trimming leading and trailing
					// spaces
					this.xbmcServers.add(xbmcServerList[i].trim());
				}
			}

			// get the notification topics to forward, for the sake of
			// simplicity these will also be the topics used for listening on
			// the OSGi event bus. This implies that topics should be expressed
			// correctly otherwise no events will be received.

			String topicsListString = (String) properties
					.get(XBMCNotifier.TOPICS_TO_FORWARD);

			// split the string along commas
			String topicsList[] = topicsListString.split(",");

			// check not null or empty
			if ((topicsList != null) && (topicsList.length > 0))
			{
				// fill the inner topic list
				// TODO: move to Java8 stream expressions
				for (int i = 0; i < topicsList.length; i++)
				{
					// add to the inner list, trim leading and trailing spaces
					this.topicsToListen.add(topicsList[i].trim());
				}
			}

			// attempt to register the service, i.e. to subscribe to the given
			// topics.
			if ((this.topicsToListen != null)
					&& (!this.topicsToListen.isEmpty())
					&& (this.xbmcServers != null)
					&& (!this.xbmcServers.isEmpty())
					&& (this.eventHandler == null))
			{
				this.registerService();
			}
		}

	}

	@Override
	public void handleEvent(Event event)
	{
		// listen to notifications only
		Object eventContent = event.getProperty(EventConstants.EVENT);
		
		//debug
		this.logger.log(LogService.LOG_INFO, "Received notification: "+event.getTopic());

		if (eventContent instanceof Notification)
		{
			// delivery task
			this.notificationDeliveryService
					.submit(new XBMCNotificationTask(
							(Notification) eventContent, this.xbmcServers,
							this.logger));
		}

	}

	/**
	 * Handles bundle activation, stores a reference to the bundle context for
	 * performing OSGi-related operations, e.g., registering services.
	 * 
	 * @param ctx
	 */
	protected void activate(BundleContext ctx)
	{
		// initialize the logger with a null logger
		this.logger = new LogHelper(ctx);

		// log the activation
		this.logger.log(LogService.LOG_INFO, "Activated XBMCNotifier");

		// store the bundle context
		this.context = ctx;

		// attempt to register the service, i.e. to subscribe to the given
		// topics.
		if ((this.topicsToListen != null) && (!this.topicsToListen.isEmpty())
				&& (this.xbmcServers != null) && (!this.xbmcServers.isEmpty())
				&& (this.eventHandler == null))
		{
			this.registerService();
		}
	}

	/**
	 * Handle the bundle de-activation
	 */
	protected void deactivate()
	{
		// log the de-activation
		if (this.logger != null)
			this.logger.log(LogService.LOG_INFO, "Deactivated XBMCNotifier");

		// de-register the event handler
		this.unRegisterService();
	}

	/**
	 * register this service as an EventHandler
	 */
	@SuppressWarnings("unchecked")
	private void registerService()
	{
		// register the EventHandler service
		Hashtable<String, Object> p = new Hashtable<String, Object>();

		// prepare the notifications
		// this might seem overkill as events are already received as strings
		// however it permits to keep a clear separation between the
		// configuration data format and the inner data structures.
		String topicsArray[] = new String[this.topicsToListen.size()];
		int i = 0;
		for (String topic : this.topicsToListen)
		{
			topicsArray[i] = topic;
			i++;
		}

		// Add this bundle as a listener for the given notifications
		p.put(EventConstants.EVENT_TOPIC, topicsArray);

		// register as EventHandler listening to the given notification topics
		this.eventHandler = (ServiceRegistration<EventHandler>) this.context
				.registerService(EventHandler.class.getName(), this, p);
	}

	/**
	 * remove this service from the framework
	 */
	private void unRegisterService()
	{
		if (this.eventHandler != null)
			this.eventHandler.unregister();
	}
}
