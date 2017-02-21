/**
 * (C) Copyright IBM Corp. 2016,2017,2019
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watsonhealth.fhir.notification;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.InitialContext;

import com.ibm.watsonhealth.fhir.model.Resource;
import com.ibm.watsonhealth.fhir.notification.exception.FHIRNotificationException;
import com.ibm.watsonhealth.fhir.persistence.interceptor.FHIRPersistenceEvent;
import com.ibm.watsonhealth.fhir.persistence.interceptor.FHIRPersistenceInterceptor;
import com.ibm.watsonhealth.fhir.persistence.interceptor.FHIRPersistenceInterceptorException;
import com.ibm.watsonhealth.fhir.persistence.interceptor.impl.FHIRPersistenceInterceptorMgr;

/**
 * This class coordinates the activities of the FHIR Server notification service.
 */
public class FHIRNotificationService implements FHIRPersistenceInterceptor {
    private static final Logger log = java.util.logging.Logger.getLogger(FHIRNotificationService.class.getName());
    private static final String JNDINAME_INCLUDED_RESOURCE_TYPES = "com.ibm.watsonhealth.fhir.notification.includeResourceTypes";
    private List<FHIRNotificationSubscriber> subscribers = new CopyOnWriteArrayList<FHIRNotificationSubscriber>();
    private static final FHIRNotificationService INSTANCE = new FHIRNotificationService();
    private Set<String> includedResourceTypes = Collections.synchronizedSortedSet(new TreeSet<String>());

    private FHIRNotificationService() {
        log.entering(this.getClass().getName(), "FHIRNotificationService");

        // Register the notification service as an interceptor so we can rely on the
        // interceptor methods to trigger the 'publish' of the notification events.
        FHIRPersistenceInterceptorMgr.getInstance().addPrioritizedInterceptor(this);
        initResourceTypes();
        log.exiting(this.getClass().getName(), "FHIRNotificationService");
    }

    /**
     * Retrieve the JNDI entry containing included resource types and initialize our set appropriately.
     */
    private void initResourceTypes() {
        String jndiValue = null;
        try {
            InitialContext ctx = new InitialContext();
            jndiValue = (String) ctx.lookup(JNDINAME_INCLUDED_RESOURCE_TYPES);
        } catch (Throwable t) {
            // Ignore any exceptions while looking up the JNDI entry.
        }

        // If we retrieved a valid non-empty jndi value, then parse it apart and store the resource type
        // names in our set, which will be used to determine if notification events should be published or not.
        if (jndiValue != null && !jndiValue.isEmpty()) {
            includedResourceTypes.clear();
            String[] list = jndiValue.split(",");
            if (list.length > 0) {
                for (int i = 0; i < list.length; i++) {
                    includedResourceTypes.add(list[i].trim());
                }
            }
        }

        log.finer("Notification service will publish events for these resource types: "
                + (includedResourceTypes.isEmpty() ? "ALL" : "\n" + includedResourceTypes.toString()));
    }

    public static FHIRNotificationService getInstance() {
        return INSTANCE;
    }

    /**
     * Method for broadcasting message to each subscriber.
     *
     * @param event
     */
    public void publish(FHIRNotificationEvent event) {
        log.entering(this.getClass().getName(), "publish");
        for (FHIRNotificationSubscriber subscriber : subscribers) {
            try {
                subscriber.notify(event);
            } catch (FHIRNotificationException e) {
                subscribers.remove(subscriber);
                log.log(Level.WARNING, FHIRNotificationService.class.getName() + ": unable to publish event", e);
            }
        }
        log.exiting(this.getClass().getName(), "publish");
    }

    /**
     * Method to subscribe the target notification implementation
     *
     * @param subscriber
     */
    public void subscribe(FHIRNotificationSubscriber subscriber) {
        log.entering(this.getClass().getName(), "subscribe");
        try {
            if (!subscribers.contains(subscriber)) {
                subscribers.add(subscriber);
            }
        } finally {
            log.exiting(this.getClass().getName(), "subscribe");
        }
    }

    /**
     * Method to unsubscribe the target notification implementation
     *
     * @param subscriber
     */
    public void unsubscribe(FHIRNotificationSubscriber subscriber) {
        log.entering(this.getClass().getName(), "unsubscribe");
        try {
            if (subscribers.contains(subscriber)) {
                subscribers.remove(subscriber);
            }
        } finally {
            log.exiting(this.getClass().getName(), "unsubscribe");
        }
    }

    /**
     * Check if this subscriber has subscribed to this service
     *
     * @param subscriber
     * @return
     */
    public boolean isSubscribed(FHIRNotificationSubscriber subscriber) {
        log.entering(this.getClass().getName(), "isSubscribed");
        try {
            return subscribers.contains(subscriber);
        } finally {
            log.exiting(this.getClass().getName(), "isSubscribed");
        }
    }

    /**
     * The following set of methods are from the FHIRPersistenceInterceptor interface and are implemented here to allow
     * the notification service to be registered as a persistence interceptor. All we really need to do in these methods
     * is perform the "publish" action.
     */

    /*
     * (non-Javadoc)
     * @see
     * com.ibm.watsonhealth.fhir.persistence.interceptor.FHIRPersistenceInterceptor#afterCreate(com.ibm.watsonhealth.
     * fhir.persistence.interceptor.FHIRPersistenceEvent)
     */
    @Override
    public void afterCreate(FHIRPersistenceEvent pEvent) throws FHIRPersistenceInterceptorException {
        if (shouldPublish(pEvent)) {
            this.publish(buildNotificationEvent("create", pEvent));
        }
    }

    /*
     * (non-Javadoc)
     * @see
     * com.ibm.watsonhealth.fhir.persistence.interceptor.FHIRPersistenceInterceptor#afterUpdate(com.ibm.watsonhealth.
     * fhir.persistence.interceptor.FHIRPersistenceEvent)
     */
    @Override
    public void afterUpdate(FHIRPersistenceEvent pEvent) throws FHIRPersistenceInterceptorException {
        if (shouldPublish(pEvent)) {
            this.publish(buildNotificationEvent("update", pEvent));
        }
    }

    /*
     * (non-Javadoc)
     * @see
     * com.ibm.watsonhealth.fhir.persistence.interceptor.FHIRPersistenceInterceptor#beforeCreate(com.ibm.watsonhealth.
     * fhir.persistence.interceptor.FHIRPersistenceEvent)
     */
    @Override
    public void beforeCreate(FHIRPersistenceEvent pEvent) throws FHIRPersistenceInterceptorException {
        // Nothing to do for 'beforeCreate'.
    }

    /*
     * (non-Javadoc)
     * @see
     * com.ibm.watsonhealth.fhir.persistence.interceptor.FHIRPersistenceInterceptor#beforeUpdate(com.ibm.watsonhealth.
     * fhir.persistence.interceptor.FHIRPersistenceEvent)
     */
    @Override
    public void beforeUpdate(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        // Nothing to do for 'beforeUpdate'.
    }

    /**
     * Returns true iff we should publish the specified persistence event as a notification event.
     */
    private boolean shouldPublish(FHIRPersistenceEvent pEvent) {
        log.entering(this.getClass().getName(), "shouldPublish");

        try {
            // If our resource type filter is empty, then we should publish all notification events.
            if (includedResourceTypes == null || includedResourceTypes.isEmpty()) {
                log.finer("Resource type filter not specified, publishing all events.");
                return true;
            }

            // Retrieve the resource type associated with the event.
            String resourceType = null;
            String locationURI = (String) pEvent.getProperty(FHIRPersistenceEvent.PROPNAME_RESOURCE_LOCATION_URI);
            if (locationURI != null && !locationURI.isEmpty()) {
                String[] tokens = locationURI.split("/");
                if (tokens.length > 0) {
                    resourceType = tokens[0];
                    log.finer("Retrieved resource type from locationURI: " + resourceType);
                }
            }

            return (resourceType != null ? includedResourceTypes.contains(resourceType) : false);
        } catch (Throwable t) {
            throw new IllegalStateException("Unexpected exception while checking notification resource type inclusion.", t);
        } finally {
            log.exiting(this.getClass().getName(), "shouldPublish");
        }
    }

    /**
     * Builds a FHIRNotificationEvent object from the specified FHIRPersistenceEvent.
     */
    private FHIRNotificationEvent buildNotificationEvent(String operation, FHIRPersistenceEvent pEvent) {
        try {
            FHIRNotificationEvent event = new FHIRNotificationEvent();
            event.setOperationType(operation);
            Resource resource = pEvent.getFhirResource();
            event.setLastUpdated(resource.getMeta().getLastUpdated().getValue().toString());
            event.setLocation((String) pEvent.getProperty(FHIRPersistenceEvent.PROPNAME_RESOURCE_LOCATION_URI));
            event.setResourceId(resource.getId().getValue());
            event.setResource(resource);
            event.setHttpHeaders(pEvent.getHttpHeaders());

            return event;
        } catch (Exception e) {
            log.log(Level.SEVERE, this.getClass().getName() + ": unable to build notification event", e);
            throw e;
        }
    }
}
