/*! LICENSE
 *
 * Copyright (c) 2015, The Agile Factory SA and/or its affiliates. All rights
 * reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package framework.services.plugins;

import static akka.actor.SupervisorStrategy.resume;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import models.framework_models.plugin.PluginConfiguration;
import models.framework_models.plugin.PluginDefinition;
import models.framework_models.plugin.PluginLog;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import play.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Identify;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.SupervisorStrategy.Directive;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import akka.japi.Function;
import akka.pattern.AskableActorSelection;
import akka.routing.RoundRobinPool;
import akka.util.Timeout;
import framework.commons.DataType;
import framework.commons.IFrameworkConstants;
import framework.commons.message.EventMessage;
import framework.commons.message.EventMessage.MessageType;
import framework.services.ext.ExtensionManagerException;
import framework.services.ext.IExtension;
import framework.services.plugins.api.EventInterfaceConfiguration;
import framework.services.plugins.api.IPluginContext;
import framework.services.plugins.api.IPluginRunner;
import framework.services.plugins.api.IPluginRunnerConfigurator;
import framework.services.plugins.api.IStaticPluginRunnerDescriptor;
import framework.services.plugins.api.PluginException;
import framework.utils.Msg;
import framework.utils.Utilities;

/**
 * This service implementation manages the lifecycle of the plugins.<br/>
 * 
 * @author Pierre-Yves Cloux
 */
public class PluginManagerServiceImpl implements IPluginManagerService {
    private static Logger.ALogger log = Logger.of(PluginManagerServiceImpl.class);
    private ActorSystem actorSystem;
    private ActorRef pluginStatusCallbackActorRef;

    /**
     * Loaded plugin extensions
     */
    private Map<String, IExtension> pluginExtensions;

    /**
     * Map : key=plugin id , value= {@link PluginRegistrationEntry}.
     */
    private Map<Long, PluginRegistrationEntry> pluginByIds;

    /**
     * Creates a {@link OneForOneStrategy} using the specified parameters.
     * 
     * @param numberOfRetry
     *            a number of retry
     * @param withinTimeRange
     *            the time range
     * @param pluginConfigurationId
     *            the unique id of the plugin configuration
     */
    private static SupervisorStrategy getSupervisorStrategy(int numberOfRetry, Duration withinTimeRange, Long pluginConfigurationId) {
        final String errorMessage = String.format("An provisioning processor of the plugin %d reported an exception, retry", pluginConfigurationId);
        return new OneForOneStrategy(numberOfRetry, withinTimeRange, new Function<Throwable, Directive>() {
            @Override
            public Directive apply(Throwable t) {
                log.error(errorMessage, t);
                return resume();
            }
        });
    }

    /**
     * Default constructor.
     */
    public PluginManagerServiceImpl() {
        pluginByIds = Collections.synchronizedMap(new HashMap<Long, PluginRegistrationEntry>());
        pluginExtensions = Collections.synchronizedMap(new HashMap<String, IExtension>());
    }

    @Override
    public void loadPluginExtension(IExtension pluginExtension, String identifier, String clazz, boolean isAvailable) throws PluginException {
        PluginDefinition pluginDefinition = PluginDefinition.getPluginDefinitionFromIdentifier(identifier);
        if (pluginDefinition == null) {
            // TODO : If the plugin is not currently listed into the database,
            // do not load it
            return;
        }
        pluginDefinition.clazz = clazz;
        getPluginExtensions().put(identifier, pluginExtension);
        log.info("Plugin definition loaded with identifier " + identifier + " and class " + clazz);
        pluginDefinition.save();
    }

    @Override
    public InputStream getPluginSmallImageSrc(String pluginDefinitionIdentifier) {
        IExtension extension = getPluginExtensions().get(pluginDefinitionIdentifier);
        if (extension == null)
            return null;
        return extension.getResourceAsStream(String.format(IFrameworkConstants.PLUGIN_SMALL_IMAGE_TEMPLATE, pluginDefinitionIdentifier));
    }

    @Override
    public InputStream getPluginBigImageSrc(String pluginDefinitionIdentifier) {
        IExtension extension = getPluginExtensions().get(pluginDefinitionIdentifier);
        if (extension == null)
            return null;
        return extension.getResourceAsStream(String.format(IFrameworkConstants.PLUGIN_BIG_IMAGE_TEMPLATE, pluginDefinitionIdentifier));
    }

    @Override
    public Map<Pair<String, Boolean>, IStaticPluginRunnerDescriptor> getAllPluginDescriptors() {
        List<PluginDefinition> pluginDefinitions = PluginDefinition.getAllPluginDefinitions();
        Map<Pair<String, Boolean>, IStaticPluginRunnerDescriptor> pluginDescriptions = new HashMap<Pair<String, Boolean>, IStaticPluginRunnerDescriptor>();
        if (pluginDefinitions != null) {
            for (PluginDefinition pluginDefinition : pluginDefinitions) {
                try {
                    IPluginRunner pluginRunner = getPluginRunnerFromDefinition(pluginDefinition);
                    pluginDescriptions.put(Pair.of(pluginRunner.getStaticDescriptor().getPluginDefinitionIdentifier(), pluginDefinition.isAvailable),
                            pluginRunner.getStaticDescriptor());
                } catch (Exception e) {
                    String message = String.format("Unable to instanciate the plugin %s", pluginDefinition.identifier);
                    log.error(message, e);
                }
            }
        }
        return pluginDescriptions;
    }

    @Override
    public boolean isPluginAvailable(String pluginDefinitionIdentifier) {
        PluginDefinition pluginDefinition = PluginDefinition.getPluginDefinitionFromIdentifier(pluginDefinitionIdentifier);
        return pluginDefinition != null && pluginDefinition.isAvailable;
    }

    @Override
    public IStaticPluginRunnerDescriptor getAvailablePluginDescriptor(String pluginDefinitionIdentifier) {
        PluginDefinition pluginDefinition = PluginDefinition.getAvailablePluginDefinitionFromIdentifier(pluginDefinitionIdentifier);
        if (pluginDefinition == null || !pluginDefinition.isAvailable) {
            return null;
        }
        try {
            return getPluginRunnerFromDefinition(pluginDefinition).getStaticDescriptor();
        } catch (Exception e) {
            String message = String.format("Unable to instanciate the plugin %s", pluginDefinition.identifier);
            log.error(message, e);
        }
        return null;
    }

    @Override
    public IStaticPluginRunnerDescriptor getPluginDescriptor(String pluginDefinitionIdentifier) {
        PluginDefinition pluginDefinition = PluginDefinition.getPluginDefinitionFromIdentifier(pluginDefinitionIdentifier);
        if (pluginDefinition == null) {
            return null;
        }
        try {
            return getPluginRunnerFromDefinition(pluginDefinition).getStaticDescriptor();
        } catch (Exception e) {
            String message = String.format("Unable to instanciate the plugin %s", pluginDefinition.identifier);
            log.error(message, e);
        }
        return null;
    }

    /**
     * Return the {@link IPluginRunner} class associated with the specified
     * definition.<br/>
     * 
     * @param pluginDefinition
     *            a plugin definition
     * @return
     * @throws ClassNotFoundException
     */
    private IPluginRunner getPluginRunnerFromDefinition(PluginDefinition pluginDefinition) throws ClassNotFoundException {
        IExtension extension = getPluginExtensions().get(pluginDefinition.identifier);
        try {
            return extension.createPluginInstance(pluginDefinition.identifier);
        } catch (ExtensionManagerException e) {
            throw new ClassNotFoundException("No class for the plugin " + pluginDefinition.identifier, e);
        }
    }

    @Override
    public void createActors(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;

        // Check the plugin definitions before starting
        try {
            hideNotLoadedPluginDefinitions();
        } catch (PluginException e) {
            throw new RuntimeException("UNEXPECTED ERROR : unable to check the plugin definitions", e);
        }

        // Start the actor which will receive the notifications from the actors
        // (I am started, I am stopped)
        this.pluginStatusCallbackActorRef = getActorSystem().actorOf(Props.create(new PluginStatusCallbackActorCreator(getPluginByIds())));
        List<PluginConfiguration> pluginConfigurations = PluginConfiguration.getAllAvailablePlugins();
        if (pluginConfigurations != null) {
            for (PluginConfiguration pluginConfiguration : pluginConfigurations) {
                try {
                    registerPluginRunner(pluginConfiguration.id);
                    if (pluginConfiguration.isAutostart) {
                        // If the plugin is maked as "autostart" then auto-start
                        // it
                        startPluginRunner(pluginConfiguration.id);
                    }
                } catch (Exception e) {
                    log.error("Error while starting the PluginManagerService", e);
                }
            }
        }
    }

    /**
     * Set all the plugin definitions for which no class exist in the manager to
     * "not available"
     * 
     * @throws PluginException
     */
    private void hideNotLoadedPluginDefinitions() throws PluginException {
        // Set all the NOT LOADED plugin definitions to "unavailable"
        List<PluginDefinition> pluginDefinitions = PluginDefinition.getAllPluginDefinitions();
        if (pluginDefinitions != null) {
            for (PluginDefinition pluginDefinition : pluginDefinitions) {
                if (!getPluginExtensions().containsKey(pluginDefinition.identifier)) {
                    log.warn("Make plugin definition " + pluginDefinition.identifier + " unavailable since it is not loaded");
                    pluginDefinition.isAvailable = false;
                    pluginDefinition.save();

                    // Check if this definition has a configuration
                    if (pluginDefinition.pluginConfigurations != null) {
                        for (PluginConfiguration pluginConfiguration : pluginDefinition.pluginConfigurations) {
                            log.error("WARNING>>> A plugin configuration " + pluginConfiguration.name + " is loaded for the plugin "
                                    + pluginDefinition.identifier + " while this one is not loaded and not avalable");
                        }
                    }
                }
            }
        }
    }

    @Override
    public void shutdown() {
        if (isActorSystemReady()) {
            try {
                stopAll();
            } catch (Exception e) {
                log.error("Exception while stopping all the plugins at shutdown");
            }
            try {
                getActorSystem().stop(getPluginStatusCallbackActorRef());
            } catch (Exception e) {
                log.error("Exception stopping the status callback actor");
            }
        }
    }

    @Override
    public void registerPlugin(Long pluginConfigurationId) throws PluginException {
        if (isActorSystemReady()) {
            registerPluginRunner(pluginConfigurationId);
        }
    }

    /**
     * Register the plugin (if it is not yet registered).<br/>
     * It instantiate the plugin runner class and record it in memory into the
     * plugin manager registry.
     * 
     * @param pluginConfigurationId
     *            a plugin configuration id
     * @throws PluginException
     */
    private void registerPluginRunner(Long pluginConfigurationId) throws PluginException {
        synchronized (getPluginByIds()) {
            if (!getPluginByIds().containsKey(pluginConfigurationId)) {
                PluginConfiguration pluginConfiguration = PluginConfiguration.getAvailablePluginById(pluginConfigurationId);
                PluginRegistrationEntry pluginRegistrationEntry = initializePlugin(pluginConfiguration);
                if (pluginRegistrationEntry != null) {
                    getPluginByIds().put(pluginConfigurationId, pluginRegistrationEntry);
                }
            }
        }
    }

    @Override
    public void unregisterPlugin(Long pluginConfigurationId) throws PluginException {
        if (isActorSystemReady()) {
            synchronized (getPluginByIds()) {
                PluginRegistrationEntry pluginRegistrationEntry = getPluginByIds().get(pluginConfigurationId);
                if (pluginRegistrationEntry != null && pluginRegistrationEntry.getPluginStatus().equals(PluginStatus.STOPPED)) {
                    unInitializePlugin(pluginRegistrationEntry);
                    getPluginByIds().remove(pluginConfigurationId);
                } else {
                    throw new PluginException("Cannot unregister a plugin which is already running");
                }
            }
        }
    }

    @Override
    public Map<Long, IPluginInfo> getRegisteredPluginDescriptors() {
        HashMap<Long, IPluginInfo> registeredPluginDescriptors = new HashMap<Long, IPluginInfo>();
        for (PluginRegistrationEntry pluginRegistrationEntry : getPluginByIds().values()) {
            registeredPluginDescriptors.put(pluginRegistrationEntry.getPluginConfigurationId(), pluginRegistrationEntry);
        }
        return registeredPluginDescriptors;
    }

    /**
     * Load the plugin class, call the "init" method by passing the
     * {@link IPluginContext} to the plugin, start the lifecycle management
     * actor and create the {@link PluginRegistrationEntry}.
     * 
     * @param pluginConfiguration
     *            a plugin configuration extracted from the database.
     * @return a plugin registration entry ready to be added to the plugin
     *         register
     * @throws PluginException
     */
    private PluginRegistrationEntry initializePlugin(PluginConfiguration pluginConfiguration) throws PluginException {
        IPluginRunner pluginRunner;
        log.info(String.format("[BEGIN] initialize the plugin %d", pluginConfiguration.id));
        try {
            pluginRunner = getPluginRunnerFromDefinition(pluginConfiguration.pluginDefinition);
            log.info(String.format("The class for the plugin %d has been found and instanciated", pluginConfiguration.id));
            if (pluginRunner.getStaticDescriptor() == null || pluginRunner.getStaticDescriptor().getPluginDefinitionIdentifier() == null
                    || !pluginRunner.getStaticDescriptor().getPluginDefinitionIdentifier().equals(pluginConfiguration.pluginDefinition.identifier)) {
                throw new PluginException(String.format(
                        "Plugin description is null or the plugin definition id %s is not matching the id of the implementation class %s",
                        pluginRunner.getStaticDescriptor() != null ? pluginRunner.getStaticDescriptor().getPluginDefinitionIdentifier() : "null",
                        pluginConfiguration.pluginDefinition.identifier));
            }
            pluginRunner.init(new PluginContextImpl(pluginConfiguration));
            ActorRef pluginLifeCycleControllingActorRef = getActorSystem().actorOf(
                    Props.create(new PluginLifeCycleControllingActorCreator(pluginConfiguration.id, pluginRunner, getPluginStatusCallbackActorRef())));
            log.info(String.format("[END] the plugin %d has been initialized", pluginConfiguration.id));
            return new PluginRegistrationEntry(pluginConfiguration.id, pluginRunner, pluginLifeCycleControllingActorRef);
        } catch (Exception e) {
            String message = String.format("Unable to initiaize the plugin %d", pluginConfiguration.id);
            log.error(message, e);
            throw new PluginException(message, e);
        }
    }

    /**
     * This method stop the plugin lifecycle management actor.
     * 
     * @param pluginRegistrationEntry
     */
    private void unInitializePlugin(PluginRegistrationEntry pluginRegistrationEntry) {
        try {
            getActorSystem().stop(pluginRegistrationEntry.getLifeCycleControllingRouter());
        } catch (Exception e) {
            log.error(String.format("Unable to uninitialize the plugin %d", pluginRegistrationEntry.getPluginConfigurationId()), e);
        }
    }

    @Override
    public PluginStatus getPluginStatus(Long pluginConfigurationId) throws PluginException {
        PluginRegistrationEntry pluginRegistrationEntry = getPluginByIds().get(pluginConfigurationId);
        if (pluginRegistrationEntry != null) {
            return pluginRegistrationEntry.getPluginStatus();
        } else {
            throw new PluginException(String.format("Unknown plugin %d cannot get status", pluginConfigurationId));
        }
    }

    @Override
    public void startPlugin(Long pluginConfigurationId) throws PluginException {
        if (isActorSystemReady()) {
            startPluginRunner(pluginConfigurationId);
        }
    }

    /**
     * Start the specified plugin.<br/>
     * Actually this method
     * <ol>
     * <li>send the START message to the plugin lifecycle management actor</li>
     * <li>start the actor managing the IN interface (if any)</li>
     * <li>start the actor managing the OUT interface (if any)</li>
     * </ol>
     * This requires all the associated actors (lifecycle management and IN/OUT
     * interfaces) to be fully stopped.
     * 
     * @param pluginConfigurationId
     *            the plugin configuration id
     * @throws PluginException
     */
    private void startPluginRunner(Long pluginConfigurationId) {
        PluginRegistrationEntry pluginRegistrationEntry = getPluginByIds().get(pluginConfigurationId);
        if (pluginRegistrationEntry == null) {
            log.error(String.format("Attempt to start an unknown or unregistered plugin %d", pluginConfigurationId));
            return;
        }
        synchronized (pluginRegistrationEntry) {
            ActorRef outActorRef = null;
            ActorRef inActorRef = null;
            // Check if the plugin is stopped
            if (pluginRegistrationEntry.getPluginStatus().equals(PluginStatus.STOPPED)
                    && isActorStopped(FlowType.IN.getRouterPrefix() + pluginRegistrationEntry.getPluginConfigurationId(), getActorSystem())
                    && isActorStopped(FlowType.OUT.getRouterPrefix() + pluginRegistrationEntry.getPluginConfigurationId(), getActorSystem())) {
                try {
                    // Set the plugin as Starting
                    pluginRegistrationEntry.setPluginStatus(PluginStatus.STARTING);

                    // Send START message to the lifecycle management router
                    pluginRegistrationEntry.getLifeCycleControllingRouter().tell(LifeCycleMessage.START, ActorRef.noSender());
                    // Start the OUT interface routing actor (if any)
                    outActorRef = startEventMessageProcessingActor(pluginRegistrationEntry, FlowType.OUT);
                    pluginRegistrationEntry.setOutEventMessageProcessingActorRef(outActorRef);
                    // Start the IN interface routing actor (if any)
                    inActorRef = startEventMessageProcessingActor(pluginRegistrationEntry, FlowType.IN);
                    pluginRegistrationEntry.setInEventMessageProcessingActorRef(inActorRef);

                    log.info(String.format("The plugin %d is starting", pluginConfigurationId));
                } catch (Exception e) {
                    String uuid = UUID.randomUUID().toString();
                    log.error(String.format("The plugin %d cannot be started, unexpected error %s", pluginConfigurationId, uuid), e);
                    PluginLog.saveStartPluginLog(pluginConfigurationId, Msg.get("plugin.failed.start", pluginConfigurationId, uuid), true);
                }
            } else {
                log.error(String.format("The router for the plugin configuration %d is not stopped, cannot start it", pluginConfigurationId));
                return;
            }
        }
    }

    /**
     * Start the router associated with the specified flow type (IN or OUT).
     * 
     * @param pluginRegistrationEntry
     *            a plugin registration entry
     * @param flowType
     *            a type of flow
     * @return the actor ref created
     */
    private ActorRef startEventMessageProcessingActor(PluginRegistrationEntry pluginRegistrationEntry, FlowType flowType) {
        ActorRef actorRef = null;
        EventInterfaceConfiguration eventInterfaceConfiguration = null;

        if (pluginRegistrationEntry.getPluginRunner().getConfigurator() != null) {
            if (flowType.equals(FlowType.IN)) {
                eventInterfaceConfiguration = pluginRegistrationEntry.getPluginRunner().getConfigurator().getInInterfaceConfiguration();
            } else {
                eventInterfaceConfiguration = pluginRegistrationEntry.getPluginRunner().getConfigurator().getOutInterfaceConfiguration();
            }
        }
        if (eventInterfaceConfiguration != null) {
            actorRef = getActorSystem().actorOf(
                    (new RoundRobinPool(eventInterfaceConfiguration.getPoolSize())).withSupervisorStrategy(
                            getSupervisorStrategy(eventInterfaceConfiguration.getNumberOfRetry(), eventInterfaceConfiguration.getRetryDuration(),
                                    pluginRegistrationEntry.getPluginConfigurationId())).props(
                            Props.create(new EventMessageProcessingActorCreator(pluginRegistrationEntry.getPluginConfigurationId(), pluginRegistrationEntry
                                    .getPluginRunner(), FlowType.OUT))), flowType.getRouterPrefix() + pluginRegistrationEntry.getPluginConfigurationId());
            String message = "The %s interface for the plugin %d has been started";
            log.info(String.format(message, flowType.name(), pluginRegistrationEntry.getPluginConfigurationId()));
            return actorRef;
        }
        return null;
    }

    @Override
    public void stopPlugin(Long pluginConfigurationId) {
        if (isActorSystemReady()) {
            stopPluginRunner(pluginConfigurationId);
        }
    }

    /**
     * Stop the specified plugin.<br/>
     * <b>It is assumed that the plugin exists.</b> Here is the sequence which
     * is executed:
     * <ol>
     * <li>Stopping the IN interface (if any)</li>
     * <li>Stopping the OUT interface (if any)</li>
     * <li>Sending to the plugin lifecycle management actor the STOP message</li>
     * </ol>
     * 
     * <b>WARNING</b> : if the stop process succeed, the plugin status is
     * STOPPING. The status may be checked later for STOPPED status (which can
     * occur asynchronously).
     * 
     * @param pluginConfigurationId
     *            the unique id of the plugin configuration
     * @throws PluginException
     */
    private void stopPluginRunner(Long pluginConfigurationId) {
        PluginRegistrationEntry pluginRegistrationEntry = getPluginByIds().get(pluginConfigurationId);
        if (pluginRegistrationEntry == null) {
            log.error(String.format("Attempt to start an unknown or unregistered plugin %d", pluginConfigurationId));
            return;
        }
        synchronized (pluginRegistrationEntry) {
            if (pluginRegistrationEntry.getPluginStatus().equals(PluginStatus.STARTED)
                    || pluginRegistrationEntry.getPluginStatus().equals(PluginStatus.START_FAILED)) {
                try {
                    pluginRegistrationEntry.setPluginStatus(PluginStatus.STOPPING);

                    // Send STOP message to the lifecycle management router
                    pluginRegistrationEntry.getLifeCycleControllingRouter().tell(LifeCycleMessage.STOP, ActorRef.noSender());
                    // Stop the listening interfaces
                    stopEventMessageProcessingActor(pluginRegistrationEntry, FlowType.IN);
                    stopEventMessageProcessingActor(pluginRegistrationEntry, FlowType.OUT);

                    log.info(String.format("The plugin %d is stopping", pluginConfigurationId));
                } catch (Exception e) {
                    String uuid = UUID.randomUUID().toString();
                    log.error(String.format("The plugin %d cannot be stopped, status is unknown, id of error is %s", pluginConfigurationId, uuid), e);
                    PluginLog.saveStopPluginLog(pluginConfigurationId, Msg.get("plugin.failed.stop", pluginConfigurationId, uuid), true);
                }
            } else {
                log.error(String.format("The plugin %d is not started, cannot stop it", pluginConfigurationId));
            }
        }
    }

    /**
     * Stop the actor which is associated with an event processing interface.
     * 
     * @param pluginRegistrationEntry
     * @param flowType
     */
    private void stopEventMessageProcessingActor(PluginRegistrationEntry pluginRegistrationEntry, FlowType flowType) {
        ActorRef router = null;
        if (flowType.equals(FlowType.IN)) {
            router = pluginRegistrationEntry.getInEventMessageProcessingActorRef();
        } else {
            router = pluginRegistrationEntry.getOutEventMessageProcessingActorRef();
        }
        if (router != null) {
            // Stop the out interface
            getActorSystem().stop(router);
            log.info(String.format("The %s interface router for the plugin %d has been stopped", flowType.name(),
                    pluginRegistrationEntry.getPluginConfigurationId()));
        }
    }

    /**
     * Stop all the registered plugin.
     */
    private void stopAllPluginFlows() {
        synchronized (getPluginByIds()) {
            for (Long pluginConfigurationId : getPluginByIds().keySet()) {
                stopPluginRunner(pluginConfigurationId);
            }
        }
    }

    @Override
    public void stopAll() {
        if (isActorSystemReady()) {
            stopAllPluginFlows();
        }
    }

    /**
     * Post a provisioning message which will be handled asynchronously.
     * 
     * @param eventMessage
     *            a event message
     */
    public void postOutMessage(EventMessage eventMessage) {
        postMessage(FlowType.OUT, eventMessage);
    }

    @Override
    public void postInMessage(EventMessage eventMessage) {
        postMessage(FlowType.IN, eventMessage);
    }

    @Override
    public List<Triple<Long, String, IPluginInfo>> getPluginSupportingRegistrationForDataType(DataType dataType) {
        List<Triple<Long, String, IPluginInfo>> pluginsSupportingRegistration = new ArrayList<Triple<Long, String, IPluginInfo>>();
        for (PluginRegistrationEntry pluginRegistrationEntry : getPluginByIds().values()) {
            if (pluginRegistrationEntry.getConfigurator() != null && pluginRegistrationEntry.getConfigurator().getDataTypesWithRegistration() != null) {
                Set<DataType> supportedDataTypes = pluginRegistrationEntry.getConfigurator().getDataTypesWithRegistration().keySet();
                if (supportedDataTypes != null && supportedDataTypes.contains(dataType)) {
                    pluginsSupportingRegistration.add(Triple.of(pluginRegistrationEntry.getPluginConfigurationId(),
                            PluginConfiguration.getAvailablePluginById(pluginRegistrationEntry.getPluginConfigurationId()).name,
                            (IPluginInfo) pluginRegistrationEntry));
                }
            }
        }
        return pluginsSupportingRegistration;
    }

    /**
     * Post a provisioning message which will be handled asynchronously. There
     * is two possible scenarios:
     * <ul>
     * <li>The message is CUSTOM : this means that one specific plugin must be
     * notified and its identifier should be hold by the {@link EventMessage}</li>
     * <li>The message is not CUSTOM : then all the plugins which are
     * "compatible" with the {@link DataType} of the message are notified</li>
     * </ul>
     * 
     * @param flowType
     *            the type of the event flow (IN or OUT)
     * @param eventMessage
     *            a event message
     * @throws PluginException
     */
    private void postMessage(FlowType flowType, EventMessage eventMessage) {
        if (isActorSystemReady()) {
            if (eventMessage != null && eventMessage.isConsistent()) {
                if (eventMessage.getMessageType().equals(MessageType.CUSTOM)) {
                    log.info(String.format("Dispatching the event %s to the plugin %d through the %s interface", eventMessage.getTransactionId(),
                            eventMessage.getPluginConfigurationId(), flowType.name()));
                    PluginRegistrationEntry pluginRegistrationEntry = getPluginByIds().get(eventMessage.getPluginConfigurationId());
                    if (pluginRegistrationEntry != null && pluginRegistrationEntry.getPluginStatus().equals(PluginStatus.STARTED)) {
                        dispatchMessage(flowType, eventMessage, pluginRegistrationEntry, eventMessage.getPluginConfigurationId());
                    } else {
                        log.error(String.format(
                                "Attempt to dispatch the event %s to the plugin %d while this one is not started or not existing. Here is the message : %s.",
                                eventMessage.getTransactionId(), eventMessage.getPluginConfigurationId(), eventMessage.toString()));
                    }
                } else {
                    log.info(String.format("Dispatching the event %s to all the plugins %s interface", eventMessage.getTransactionId(), flowType.name()));
                    for (PluginRegistrationEntry pluginRegistrationEntry : getPluginByIds().values()) {
                        // Check if the plugin is compatible with the message
                        // data type and dispatch
                        if (pluginRegistrationEntry.getPluginStatus().equals(PluginStatus.STARTED)
                                && pluginRegistrationEntry.isPluginCompatible(eventMessage.getDataType())) {
                            dispatchMessage(flowType, eventMessage, pluginRegistrationEntry, pluginRegistrationEntry.getPluginConfigurationId());
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("Invalid message posted " + eventMessage);
            }
        }
    }

    /**
     * Dispatch a message according to the specified flow and to the specified
     * plugin.
     * 
     * @param flowType
     *            a flow type (IN or OUT)
     * @param eventMessage
     *            a message to dispatch
     * @param pluginRegistrationEntry
     *            a registration entry
     * @param pluginConfigurationId
     *            the plugin configuration id
     */
    private void dispatchMessage(FlowType flowType, EventMessage eventMessage, PluginRegistrationEntry pluginRegistrationEntry, Long pluginConfigurationId) {
        ActorRef actorRef;
        if (flowType.equals(FlowType.OUT)) {
            actorRef = pluginRegistrationEntry.getOutEventMessageProcessingActorRef();
        } else {
            actorRef = pluginRegistrationEntry.getInEventMessageProcessingActorRef();
        }
        if (actorRef != null) {
            actorRef.tell(eventMessage, ActorRef.noSender());
        } else {
            log.info(String.format("No event dispatched %s to the plugin %d since its %s interface is not started", eventMessage.getTransactionId(),
                    pluginConfigurationId, flowType.name()));
        }
    }

    private ActorSystem getActorSystem() {
        return actorSystem;
    }

    private boolean isActorSystemReady() {
        return getActorSystem() != null && !getActorSystem().isTerminated();
    }

    private Map<Long, PluginRegistrationEntry> getPluginByIds() {
        return pluginByIds;
    }

    private Map<String, IExtension> getPluginExtensions() {
        return pluginExtensions;
    }

    private ActorRef getPluginStatusCallbackActorRef() {
        return pluginStatusCallbackActorRef;
    }

    /**
     * Return true if the specified actor exists.
     * 
     * @param actorPath
     *            the path to an actor
     * @param actorSystem
     *            the Akka actor system
     * @return a boolean
     */
    private static boolean isActorStopped(String actorPath, ActorSystem actorSystem) {
        ActorSelection actorSelection = actorSystem.actorSelection(actorPath);
        Timeout t = new Timeout(5, TimeUnit.SECONDS);
        AskableActorSelection asker = new AskableActorSelection(actorSelection);
        Future<Object> fut = asker.ask(new Identify(1), t);
        ActorIdentity ident;
        try {
            ident = (ActorIdentity) Await.result(fut, t.duration());
            ActorRef ref = ident.getRef();
            return ref == null;
        } catch (Exception e) {
            log.error(String.format("Error while searching for an actor path %s to check if the actor is running or not", actorPath), e);
        }
        return false;
    }

    /**
     * Direction of the plugin configuration (OUT or IN).
     * 
     * @author Pierre-Yves Cloux
     */
    public enum FlowType {
        IN("in-router-"), OUT("out-router-");

        private String routerPrefix;

        /**
         * Default constructor.
         * 
         * @param routerPrefix
         */
        private FlowType(String routerPrefix) {
            this.routerPrefix = routerPrefix;
        }

        /**
         * Return the prefix for the Akka router associated with the specified
         * interface (IN or OUT)
         * 
         * @return a string
         */
        public String getRouterPrefix() {
            return routerPrefix;
        }
    }

    /**
     * The lifecycle message which could receive a plugin control interface.
     */
    public enum LifeCycleMessage {
        START, STOP
    }

    /**
     * The lifecycle message which are notified by the lifecycle controlling
     * actor of a plugin to the {@link PluginStatusCallbackActor}.
     */
    public static class CallbackLifeCycleMessage implements Serializable {
        private static final long serialVersionUID = -1298110917944515230L;
        private PluginStatus pluginStatus;
        private Long pluginConfigurationId;

        public CallbackLifeCycleMessage(PluginStatus pluginStatus, Long pluginConfigurationId) {
            super();
            this.pluginStatus = pluginStatus;
            this.pluginConfigurationId = pluginConfigurationId;
        }

        public Long getPluginConfigurationId() {
            return pluginConfigurationId;
        }

        public PluginStatus getPluginStatus() {
            return pluginStatus;
        }
    }

    /**
     * A plugin registration entry which holds the data related to a registered
     * plugin.<br/>
     * <ul>
     * <li>pluginConfigurationId : the unique id of the plugin configuration</li>
     * <li>pluginStatus : the status of the plugin (started, stopped)</li>
     * <li>inEventMessageProcessingActorRef : the reference to the actor which
     * is managing the IN interface of the plugin (if any)</li>
     * <li>outEventMessageProcessingActorRef : the reference to the actor which
     * is managing the OUT interface of the plugin (if any)</li>
     * <li>lifeCycleControllingRouter : the reference to the actor which is
     * managing the interface which controls the life cycle (start, stop) of the
     * plugin</li>
     * <li>pluginRunner : a reference to the plugin itself</li>
     * </ul>
     */
    public static class PluginRegistrationEntry implements IPluginInfo {
        private Long pluginConfigurationId;
        private PluginStatus pluginStatus;
        private ActorRef inEventMessageProcessingActorRef;
        private ActorRef outEventMessageProcessingActorRef;
        private ActorRef lifeCycleControllingRouter;
        private IPluginRunner pluginRunner;

        public PluginRegistrationEntry(Long pluginConfigurationId, IPluginRunner pluginRunner, ActorRef lifeCycleControllingRouter) {
            super();
            this.pluginConfigurationId = pluginConfigurationId;
            this.pluginRunner = pluginRunner;
            this.lifeCycleControllingRouter = lifeCycleControllingRouter;
            pluginStatus = PluginStatus.STOPPED;
        }

        public synchronized ActorRef getOutEventMessageProcessingActorRef() {
            return outEventMessageProcessingActorRef;
        }

        public synchronized ActorRef getInEventMessageProcessingActorRef() {
            return inEventMessageProcessingActorRef;
        }

        public synchronized void setInEventMessageProcessingActorRef(ActorRef inEventMessageProcessingActorRef) {
            this.inEventMessageProcessingActorRef = inEventMessageProcessingActorRef;
        }

        public synchronized void setOutEventMessageProcessingActorRef(ActorRef outEventMessageProcessingActorRef) {
            this.outEventMessageProcessingActorRef = outEventMessageProcessingActorRef;
        }

        public synchronized Long getPluginConfigurationId() {
            return pluginConfigurationId;
        }

        public synchronized boolean isPluginCompatible(DataType dataType) {
            if (getPluginRunner().getStaticDescriptor().getSupportedDataTypes() == null) {
                return false;
            }
            return getPluginRunner().getStaticDescriptor().getSupportedDataTypes().contains(dataType);
        }

        public synchronized void setPluginStatus(PluginStatus pluginStatus) {
            this.pluginStatus = pluginStatus;
        }

        public synchronized PluginStatus getPluginStatus() {
            return pluginStatus;
        }

        public synchronized IStaticPluginRunnerDescriptor getStaticDescriptor() {
            return getPluginRunner().getStaticDescriptor();
        }

        public synchronized IPluginRunnerConfigurator getConfigurator() {
            return getPluginRunner().getConfigurator();
        }

        public synchronized IPluginRunner getPluginRunner() {
            return pluginRunner;
        }

        public synchronized ActorRef getLifeCycleControllingRouter() {
            return lifeCycleControllingRouter;
        }

        @Override
        public String getPluginSmallImage() {
            return String.format(IFrameworkConstants.PLUGIN_SMALL_IMAGE_TEMPLATE, getStaticDescriptor().getPluginDefinitionIdentifier());
        }

        @Override
        public String getPluginBigImage() {
            return String.format(IFrameworkConstants.PLUGIN_BIG_IMAGE_TEMPLATE, getStaticDescriptor().getPluginDefinitionIdentifier());
        }

        @Override
        public String toString() {
            return "PluginRegistrationEntry [pluginConfigurationId=" + pluginConfigurationId + ", pluginStatus=" + pluginStatus
                    + ", inEventMessageProcessingActorRef=" + inEventMessageProcessingActorRef + ", outEventMessageProcessingActorRef="
                    + outEventMessageProcessingActorRef + ", lifeCycleControllingRouter=" + lifeCycleControllingRouter + ", pluginRunner=" + pluginRunner + "]";
        }
    }

    /**
     * A creator class for the actor {@link PluginStatusCallbackActor}
     * 
     * @author Pierre-Yves Cloux
     */
    public static class PluginStatusCallbackActorCreator implements Creator<PluginStatusCallbackActor> {
        private static final long serialVersionUID = 4075638451954038626L;
        private Map<Long, PluginRegistrationEntry> pluginByIds;

        public PluginStatusCallbackActorCreator(Map<Long, PluginRegistrationEntry> pluginByIds) {
            this.pluginByIds = pluginByIds;
        }

        @Override
        public PluginStatusCallbackActor create() throws Exception {
            return new PluginStatusCallbackActor(pluginByIds);
        }
    }

    /**
     * The actor which is notified from the status of the of the plugins
     * asynchronously.
     * 
     * @author Pierre-Yves Cloux
     */
    public static class PluginStatusCallbackActor extends UntypedActor {
        private Map<Long, PluginRegistrationEntry> pluginByIds;

        public PluginStatusCallbackActor(Map<Long, PluginRegistrationEntry> pluginByIds) {
            this.pluginByIds = pluginByIds;
        }

        @Override
        public void onReceive(Object message) throws Exception {
            if (message instanceof CallbackLifeCycleMessage) {
                CallbackLifeCycleMessage callbackLifeCycleMessage = (CallbackLifeCycleMessage) message;
                switch (callbackLifeCycleMessage.getPluginStatus()) {
                case STARTED:
                    getPluginByIds().get(callbackLifeCycleMessage.getPluginConfigurationId()).setPluginStatus(PluginStatus.STARTED);
                    log.info(String.format("The plugin %d has reported as successfull start", callbackLifeCycleMessage.getPluginConfigurationId()));
                    break;
                case STOPPED:
                    getPluginByIds().get(callbackLifeCycleMessage.getPluginConfigurationId()).setPluginStatus(PluginStatus.STOPPED);
                    log.info(String.format("The plugin %d has reported a successfull stop", callbackLifeCycleMessage.getPluginConfigurationId()));
                    break;
                case START_FAILED:
                    getPluginByIds().get(callbackLifeCycleMessage.getPluginConfigurationId()).setPluginStatus(PluginStatus.STOPPED);
                    log.info(String.format("The plugin %d has reported an error at startup", callbackLifeCycleMessage.getPluginConfigurationId()));
                    break;
                default:
                    break;
                }
            } else {
                unhandled(message);
            }
        }

        private Map<Long, PluginRegistrationEntry> getPluginByIds() {
            return pluginByIds;
        }
    }

    /**
     * A creator class for a plugin runner lifecycle management actor. This
     * class is used to create some controlling actors.
     * 
     * @author Pierre-Yves Cloux
     */
    public static class PluginLifeCycleControllingActorCreator implements Creator<PluginLifeCycleControllingActor> {
        private static final long serialVersionUID = -5423956994117942818L;
        private Long pluginConfigurationId;
        private IPluginRunner pluginRunner;
        private ActorRef pluginStatusCallbackActorRef;

        public PluginLifeCycleControllingActorCreator(Long pluginConfigurationId, IPluginRunner pluginRunner, ActorRef pluginStatusCallbackActorRef) {
            this.pluginStatusCallbackActorRef = pluginStatusCallbackActorRef;
            this.pluginConfigurationId = pluginConfigurationId;
            this.pluginRunner = pluginRunner;
        }

        @Override
        public PluginLifeCycleControllingActor create() throws Exception {
            return new PluginLifeCycleControllingActor(pluginConfigurationId, pluginRunner, pluginStatusCallbackActorRef);
        }
    }

    /**
     * This actor is to manage the lifecycle of a plugin.<br/>
     * It received messages such as:
     * <ul>
     * <li>START</li>
     * <li>STOP</li>
     * </ul>
     * 
     * @author Pierre-Yves Cloux
     */
    public static class PluginLifeCycleControllingActor extends UntypedActor {
        private Long pluginConfigurationId;
        private IPluginRunner pluginRunner;
        private ActorRef pluginStatusCallbackActorRef;

        public PluginLifeCycleControllingActor(Long pluginConfigurationId, IPluginRunner pluginRunner, ActorRef pluginStatusCallbackActorRef) {
            super();
            this.pluginConfigurationId = pluginConfigurationId;
            this.pluginRunner = pluginRunner;
            this.pluginStatusCallbackActorRef = pluginStatusCallbackActorRef;
        }

        @Override
        public void onReceive(Object message) throws Exception {
            if (message instanceof LifeCycleMessage) {
                switch ((LifeCycleMessage) message) {
                case START:
                    try {
                        getPluginRunner().start();
                        getPluginStatusCallbackActorRef().tell(new CallbackLifeCycleMessage(PluginStatus.STARTED, getPluginConfigurationId()),
                                ActorRef.noSender());
                        PluginLog.saveStartPluginLog(getPluginConfigurationId(), Msg.get("plugin.success.start", getPluginConfigurationId()), false);
                    } catch (Exception e) {
                        getPluginStatusCallbackActorRef().tell(new CallbackLifeCycleMessage(PluginStatus.START_FAILED, getPluginConfigurationId()),
                                ActorRef.noSender());
                        log.error(String.format("The plugin %d cannot be started, unexpected error", pluginConfigurationId), e);
                        PluginLog.saveStartPluginLog(pluginConfigurationId,
                                Msg.get("plugin.failed.start", pluginConfigurationId, Utilities.getExceptionAsString(e)), true);
                    }
                    break;
                case STOP:
                    try {
                        getPluginRunner().stop();
                        PluginLog.saveStopPluginLog(pluginConfigurationId, Msg.get("plugin.success.stop", pluginConfigurationId), false);
                    } catch (Exception e) {
                        String uuid = UUID.randomUUID().toString();
                        log.error(String.format("The plugin %d has reported an error while stopping, id of error is %s", pluginConfigurationId, uuid), e);
                        PluginLog.saveStopPluginLog(pluginConfigurationId, Msg.get("plugin.failed.stop", pluginConfigurationId, uuid), true);
                    }
                    getPluginStatusCallbackActorRef().tell(new CallbackLifeCycleMessage(PluginStatus.STOPPED, getPluginConfigurationId()), ActorRef.noSender());
                    break;
                }
            } else {
                unhandled(message);
            }
        }

        private Long getPluginConfigurationId() {
            return pluginConfigurationId;
        }

        private IPluginRunner getPluginRunner() {
            return pluginRunner;
        }

        private ActorRef getPluginStatusCallbackActorRef() {
            return pluginStatusCallbackActorRef;
        }
    }

    /**
     * A creator class for the interface management actors (IN/OUT).<br/>
     * This one is used to instantiate the message processing actors.
     * 
     * @author Pierre-Yves Cloux
     */
    public static class EventMessageProcessingActorCreator implements Creator<EventMessageProcessingActor> {
        private static final long serialVersionUID = 2033974676603900632L;
        private Long pluginConfigurationId;
        private IPluginRunner pluginRunner;
        private FlowType flowType;

        public EventMessageProcessingActorCreator(Long pluginConfigurationId, IPluginRunner pluginRunner, FlowType flowType) {
            this.pluginConfigurationId = pluginConfigurationId;
            this.pluginRunner = pluginRunner;
            this.flowType = flowType;
        }

        @Override
        public EventMessageProcessingActor create() throws Exception {
            return new EventMessageProcessingActor(pluginConfigurationId, pluginRunner, flowType);
        }
    }

    /**
     * An actor which is to be used to forward the {@link EventMessage}
     * asynchronously to the OUT interface of the plugin
     * 
     * @author Pierre-Yves Cloux
     */
    public static class EventMessageProcessingActor extends UntypedActor {
        private Long pluginConfigurationId;
        private IPluginRunner pluginRunner;
        private FlowType flowType;

        public EventMessageProcessingActor(Long pluginConfigurationId, IPluginRunner pluginRunner, FlowType flowType) {
            this.pluginConfigurationId = pluginConfigurationId;
            this.pluginRunner = pluginRunner;
            this.flowType = flowType;
        }

        @Override
        public void postStop() throws Exception {
            super.postStop();
            log.info(String.format("Stopping event message processing actor [%s] for plugin %d", getSelf().path().toString(), getPluginConfigurationId()));
        }

        @Override
        public void preStart() throws Exception {
            super.preStart();
            log.info(String.format("Starting event message processing actor [%s] for plugin %d", getSelf().path().toString(), getPluginConfigurationId()));
        }

        @Override
        public void onReceive(Object message) throws Exception {
            if (message != null && message instanceof EventMessage) {
                EventMessage eventMessage = (EventMessage) message;
                try {
                    log.info(String.format("[BEGIN] Transaction %s for event message processing actor for plugin %d with message type %s",
                            eventMessage.getTransactionId(), getPluginConfigurationId(), eventMessage.getMessageType().name()));
                    if (getFlowType().equals(FlowType.OUT)) {
                        getPluginRunner().handleOutProvisioningMessage(eventMessage);
                    } else {
                        getPluginRunner().handleInProvisioningMessage(eventMessage);
                    }
                    log.info(String.format("[SUCCESS] Transaction %s for event message processing actor for plugin %d with message type %s",
                            eventMessage.getTransactionId(), getPluginConfigurationId(), eventMessage.getMessageType().name()));
                } catch (PluginException e) {
                    /*
                     * If the message was not a RESYNC message, then attempt to
                     * recover (by sending a RESYNC command) Otherwise, throw an
                     * exception to log the issue.
                     */
                    if (!eventMessage.getMessageType().equals(EventMessage.MessageType.RESYNC)
                            && !eventMessage.getMessageType().equals(EventMessage.MessageType.CUSTOM)) {
                        // Notify the parent (= the message router associated
                        // with the plugin flow) with a resync message
                        getContext().parent().tell(eventMessage.getResyncProvisioningMessage(), getSelf());
                        log.warn(String.format("[FAILURE] Transaction %s for event message processing actor for plugin %d, attempt to recover with a resync",
                                eventMessage.getTransactionId(), getPluginConfigurationId()), e);

                    } else {
                        String errorMessage = String.format("[FAILURE] Transaction %s for event message processing actor for plugin %d failed",
                                eventMessage.getTransactionId(), getPluginConfigurationId());
                        PluginLog.saveOnEventHandlingPluginLog(eventMessage.getTransactionId(), getPluginConfigurationId(), true,
                                eventMessage.getMessageType(), errorMessage + "\nMessage was : " + eventMessage.toString(), eventMessage.getDataType(),
                                eventMessage.getInternalId(), eventMessage.getExternalId());
                        throw new PluginException(errorMessage, e);
                    }
                }
            } else {
                unhandled(message);
            }
        }

        private IPluginRunner getPluginRunner() {
            return pluginRunner;
        }

        private FlowType getFlowType() {
            return flowType;
        }

        private Long getPluginConfigurationId() {
            return pluginConfigurationId;
        }
    }
}
