package lukowicz.application.aadl;

import lukowicz.application.data.*;
import lukowicz.application.memory.Cache;
import lukowicz.application.petrinet.PetriNetPager;
import lukowicz.application.utils.TranslatorTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

public class ElementSearcher {

    private Cache cache = Cache.getInstance();
    private PetriNetPager petriNetPager;
    private DataPort bus = null;

    private String busName = "";

    private static Logger LOG = LoggerFactory.getLogger(ElementSearcher.class);

    public ElementSearcher(PetriNetPager petriNetPager) {
        this.petriNetPager = petriNetPager;
    }

    public void searchElements(NodeList componentInstances, ComponentInstance processingElement) {
        for (int i = 0; i < componentInstances.getLength(); i++) {
            Node component = componentInstances.item(i);
            searchElement(component, processingElement);
        }

    }

    public void searchElement(Node component, ComponentInstance processingElement) {
        LOG.debug("Current Element : {} ", component.getNodeName());

        if (component.getNodeType() == Node.ELEMENT_NODE) {
            ComponentInstance componentInstance;
            Element actualComponent = (Element) component;
            if (processingElement != null) {
                componentInstance = processingElement;
            } else {
                componentInstance = new ComponentInstance(actualComponent.getAttribute("name"),
                        actualComponent.getAttribute("category"));
            }

            LOG.debug("Name: {}", componentInstance.getName());
            if (cache.isUniqueComponentsContain(actualComponent.getAttribute("name"))) {
                return;
            }

            ComponentInstance componentInstanceNested = processingElement != null ?
                    new ComponentInstance(actualComponent.getAttribute("name"), actualComponent.getAttribute("category")) : null;

            NodeList featureInstances = actualComponent.getElementsByTagName("featureInstance");

            NodeList ownedPropertyAssociations = actualComponent.getElementsByTagName("ownedPropertyAssociation");
            String periodValue = "";
            String computeTime = "";
            String computeDeadline = "";
            String priority = "";

            for (int k = 0; k < ownedPropertyAssociations.getLength(); k++) {
                Node ownerProperty = ownedPropertyAssociations.item(k);
                Element ownedPropertyElement = (Element) ownerProperty;
                //LOG.debug("Owned Property {} ", ownedPropertyElement);
                NodeList ownerProperties = ownedPropertyElement.getElementsByTagName("property");
                for (int l = 0; l < ownerProperties.getLength(); ++l) {
                    Node property = ownerProperties.item(l);
                    Element propertyElement = (Element) property;
                    Attr hrefProperty = propertyElement.getAttributeNode("href");
                    if (hrefProperty.getValue().contains("Timing_Properties.Period")) {
                        periodValue = ownedPropertyElement.getElementsByTagName("ownedValue").item(1).
                                getAttributes().getNamedItem("value").getNodeValue();
                        LOG.debug("period Value {} ", periodValue);
                    } else if (hrefProperty.getValue().contains("Timing_Properties.Compute_Execution_Time")) {
                        computeTime = ownedPropertyElement.getElementsByTagName("maximum").item(0).
                                getAttributes().getNamedItem("value").getNodeValue();
                        LOG.debug("computeTimeMax Value {} ", computeTime);
                    } else if (hrefProperty.getValue().contains("Timing_Properties.Deadline")) {
                        computeDeadline = ownedPropertyElement.getElementsByTagName("ownedValue").item(1).
                                getAttributes().getNamedItem("value").getNodeValue();
                        LOG.debug("computeDeadline Value {} ", computeDeadline);
                    } else if (hrefProperty.getValue().contains("Thread_Properties.Priority")) {
                        priority = ownedPropertyElement.getElementsByTagName("ownedValue").item(1).
                                getAttributes().getNamedItem("value").getNodeValue();
                        LOG.debug("priority Value {} ", priority);
                    }

                }
            }
            for (int j = 0; j < featureInstances.getLength(); j++) {
                Node featureInstance = featureInstances.item(j);

                Element featureElement = (Element) featureInstance;
                LOG.debug("Name of feature : {} ", featureElement.getAttribute("name"));
                DataPort dp;

                if (componentInstanceNested != null) {
                    dp = componentInstance.getDataPortByNameAndDirection(featureElement.getAttribute("name"),
                            featureElement.getAttribute("direction"));

                    if (dp != null) {
                        componentInstanceNested.getDataPort().add(dp);
                    } else {
                        componentInstance.getReverseFeatureInstances().remove(new DataPort(featureElement.getAttribute("name"),
                                featureElement.getAttribute("direction")));
                        componentInstance.getReverseFeatureInstances();//wroc do starego porzadku
                        dp = new DataPort(featureElement.getAttribute("name"),
                                featureElement.getAttribute("direction"));
                        componentInstanceNested.getDataPort().add(dp);
                    }
                } else {
                     dp = new DataPort(featureElement.getAttribute("name"),
                            featureElement.getAttribute("direction"));

                    componentInstance.getDataPort().add(dp);
                }
            }

            if (componentInstance.getCategory().equals(Category.BUS.getValue())) {
                busName = componentInstance.getName();
                bus = new DataPort("bus", "out");
                bus.setTokenValue(1);
                componentInstance.getDataPort().add(bus);
            }

            if (componentInstance.getCategory().equals(Category.BUS.getValue())
                || componentInstance.getCategory().equals(Category.PROCESSOR.getValue())
                || componentInstance.getCategory().equals(Category.MEMORY.getValue())) {
                DataPort hardware = new DataPort(actualComponent.getAttribute("name"), "in");
                componentInstance.getDataPort().add(hardware);

                Connection newConnection = new Connection("", hardware.getId(), componentInstance.getId());

                cache.addConnection(newConnection);
            }

            if (componentInstanceNested != null) {
                //czy nie mozna lepiej??
                LOG.debug("Preparing nested page");
                processingElement.getComponentInstancesNested().add(componentInstanceNested);
                componentInstanceNested.setPeriod(periodValue);
                cache.addElementToUniqueComponents(componentInstanceNested.getName());
                if (!"".equals(periodValue)) {
                    componentInstanceNested.setComponentInstancesNested(new ArrayList<>());

                    String additionalConnContext = TranslatorTools.generateUUID();

                    LOG.debug("Adding page: {}", componentInstanceNested.getName());
                    petriNetPager.addNewPage(additionalConnContext, componentInstanceNested.getId(), Boolean.TRUE, componentInstance.getId(), componentInstanceNested.getName(), componentInstance.getCategory().equals(Category.DEVICE.getValue()));

                    ComponentInstance periodTrans = createTransition("period", componentInstanceNested, priority);
                    ComponentInstance entryPointTrans = createTransition("entryPoint", componentInstanceNested, priority);
                    ComponentInstance receiveTrans = createTransition("receive", componentInstanceNested, priority);
                    ComponentInstance sendTrans = createTransition("send", componentInstanceNested, priority);
                    ComponentInstance completeTrans = createTransition("complete", componentInstanceNested, priority);
                    ComponentInstance activationTrans = createTransition("activation", componentInstanceNested, "0");
                    ComponentInstance deadlineTrans = createTransition("deadline", componentInstanceNested, "0");

                    completeTrans.setTime("@+" + computeTime);

                    deadlineTrans.setTime("@+" + computeDeadline);

                    DataPort cpu = createPlace("cpu", sendTrans);
                    DataPort period = createPlace("period", periodTrans);
                    DataPort clock = createPlace("clock", periodTrans);
                    DataPort working = createPlace("working", entryPointTrans);
                    DataPort dispatched = createPlace("dispatched", entryPointTrans);
                    DataPort compute = createPlace("compute", receiveTrans);
                    DataPort wait = createPlace("wait", sendTrans);
                    DataPort completed = createPlace("completed", completeTrans);
                    DataPort missedActivation = createPlace("missedActivation", activationTrans);
                    DataPort missedDeadline = createPlace("missedDeadline", deadlineTrans);

                    period.setTokenValue(1);
                    cpu.setTokenValue(1);

                    if (componentInstanceNested.getCategory().equals(Category.THREAD.getValue())) {
                        cpu.setIsCpuFusion(Boolean.TRUE);
                        cache.getPlaceFusions().add(cpu);
                    }

                    connectPlaceAndTransition(additionalConnContext, cpu, "in", entryPointTrans, true);
                    connectPlaceAndTransition(additionalConnContext, cpu, "in", completeTrans, true);
                    connectPlaceAndTransition(additionalConnContext, cpu, "out", sendTrans);
                    connectPlaceAndTransition(additionalConnContext, cpu, "in", receiveTrans);
                    connectPlaceAndTransition(additionalConnContext, period, "in", periodTrans, true, periodValue);
                    connectPlaceAndTransition(additionalConnContext, clock, "out", periodTrans);
                    connectPlaceAndTransition(additionalConnContext, clock, "in", activationTrans);
                    connectPlaceAndTransition(additionalConnContext, clock, "in", entryPointTrans);
                    connectPlaceAndTransition(additionalConnContext, working, "out", entryPointTrans);
                    connectPlaceAndTransition(additionalConnContext, working, "in", completeTrans);
                    connectPlaceAndTransition(additionalConnContext, working, "in", activationTrans);
                    connectPlaceAndTransition(additionalConnContext, working, "in", deadlineTrans);
                    connectPlaceAndTransition(additionalConnContext, dispatched, "out", entryPointTrans);
                    connectPlaceAndTransition(additionalConnContext, dispatched, "in", receiveTrans);
                    connectPlaceAndTransition(additionalConnContext, dispatched, "in", activationTrans);
                    connectPlaceAndTransition(additionalConnContext, dispatched, "in", deadlineTrans);
                    connectPlaceAndTransition(additionalConnContext, compute, "out", receiveTrans);
                    connectPlaceAndTransition(additionalConnContext, compute, "in", sendTrans);
                    connectPlaceAndTransition(additionalConnContext, wait, "out", sendTrans);
                    connectPlaceAndTransition(additionalConnContext, wait, "in", completeTrans);
                    connectPlaceAndTransition(additionalConnContext, completed, "out", completeTrans);
                    connectPlaceAndTransition(additionalConnContext, missedActivation, "out", activationTrans);
                    connectPlaceAndTransition(additionalConnContext, missedDeadline, "out", deadlineTrans);

                    for (DataPort dataPort :
                            componentInstanceNested.getDataPort()) {
                        DataPort copyDataPort = new DataPort(dataPort.getName(), dataPort.getDirection());
                        copyDataPort.setTimed(dataPort.getTimed());

                        if (copyDataPort.getDirection().equals("in")) {
                            receiveTrans.getDataPort().add(copyDataPort);
                            if (copyDataPort.getName().equals(busName)) {
                                copyDataPort.setName("bus");
                                copyDataPort.setDirection("bothdir");
                            }
                            connectPlaceAndTransition(additionalConnContext, copyDataPort, copyDataPort.getDirection(), receiveTrans);
                        } else {
                            sendTrans.getDataPort().add(copyDataPort);
                            connectPlaceAndTransition(additionalConnContext, copyDataPort, copyDataPort.getDirection(), sendTrans);
                        }

                        cache.getSOCKETS().add(new Socket(componentInstanceNested.getId(), copyDataPort.getId(), dataPort.getId(), dataPort.getDirection()));
                    }
                }
            }
            // zagniezdzone komponenenty
            NodeList nestedComponents = actualComponent.getElementsByTagName("componentInstance");
            if (nestedComponents.getLength() != 0) {
                LOG.debug("Adding nestedComponents : {} ", nestedComponents.item(0));
                searchElements(nestedComponents, componentInstance);
            } else if (componentInstance.getCategory().equals(Category.DEVICE.getValue())) {
                LOG.debug("Adding nestedDevice : {} ", component);
                if (!cache.isUniqueComponentsContain(componentInstance.getName())) {
                    cache.addElementToComponentInstances(componentInstance);
                }
                searchElement(component, componentInstance);
            } else {
                if (!cache.isUniqueComponentsContain(componentInstance.getName())) {
                    LOG.debug("Adding component : {} ", componentInstance.getName());
                    cache.addElementToComponentInstances(componentInstance);
                    cache.addElementToUniqueComponents(componentInstance.getName());
                }
            }

        }
    }

    public void searchConnections(NodeList connections) {
        for (int i = 0; i < connections.getLength(); i++) {
            Boolean threadConnection = false;
            Node connection = connections.item(i);
            LOG.debug("Current Element : {}", connection.getNodeName());
            Element actualConnection = (Element) connection;

            LOG.debug("Name of  connection : {} ", actualConnection.getAttribute("name"));
            NodeList connectionReferences = actualConnection.getElementsByTagName("connectionReference");
            String contextRaw = connectionReferences.item(0).getAttributes().getNamedItem("context").getNodeValue();

            String context = contextRaw.replaceAll("/0", "/").replaceAll("\\D+", " ").trim();
            String source = actualConnection.getAttribute("source").replaceAll("/0", "/").replaceAll("\\D+", " ").trim();
            String destination = actualConnection.getAttribute("destination").replaceAll("/0", "/").replaceAll("\\D+", " ").trim();

            ArrayList<Integer> destinationPath = TranslatorTools.preparePorts(destination);
            ArrayList<Integer> sourcePath = TranslatorTools.preparePorts(source);

            Connection newConnection;

            if (sourcePath.size() == 3 && destinationPath.size() == 3 && sourcePath.get(0) != destinationPath.get(0)) {
                newConnection = new Connection(context, source, destination.substring(0, destination.length() - 1));
                LOG.debug("connection : {}, {}, {} ", context, source, destination.substring(0, destination.length() - 1));
            } else {
                newConnection = new Connection(context, source, destination);
                LOG.debug("connection : {}, {}, {} ", context, source, destination);
            }

            if (destinationPath.get(0) != null && Category.PROCESS.getValue().equals(cache.getComponentInstanceByIndex(destinationPath.get(0)).getCategory()) &&
                    !Category.PROCESS.getValue().equals(cache.getComponentInstanceByIndex(sourcePath.get(0)).getCategory())) {
                String additionalConnContext = destinationPath.get(0).toString();
                String additionalConnSource = destination;
                String additionalConnDestination = destination.substring(0, destination.length() - 1);
                Connection additionalConnConnection = new Connection(additionalConnContext, additionalConnSource, additionalConnDestination);
                additionalConnConnection.setGenerate(Boolean.TRUE);
                additionalConnConnection.setSocketType("in");
                ConnectionNode connectionNode = getConnectionNode(destinationPath, null, null);
                petriNetPager.addNewPage(context, cache.getComponentInstanceByIndex(destinationPath.get(0)).getId(), Boolean.FALSE, null, connectionNode.getTransName(), false);
                cache.addConnection(additionalConnConnection);
                LOG.debug("connection : {}, {}, {} ", additionalConnContext, additionalConnSource, additionalConnDestination);
            }
            else if (sourcePath.get(0) != null && Category.PROCESS.getValue().equals(cache.getComponentInstanceByIndex(sourcePath.get(0)).getCategory()) &&
                    !Category.PROCESS.getValue().equals(cache.getComponentInstanceByIndex(destinationPath.get(0)).getCategory())) {
                String additionalConnContext = "";
                String additionalConnSource = source;
                String additionalConnDestination = destination;
                Connection additionalConnConnection = new Connection(additionalConnContext, additionalConnSource, additionalConnDestination);
                additionalConnConnection.setGenerate(Boolean.TRUE);
                additionalConnConnection.setSocketType("out");
                petriNetPager.addNewPage(context, cache.getComponentInstanceByIndex(sourcePath.get(0)).getId(), Boolean.FALSE, null, cache.getComponentInstanceByIndex(sourcePath.get(0)).getName(), false);
                cache.addConnection(additionalConnConnection);
                LOG.debug("connection : {}, {}, {} ", additionalConnContext, additionalConnSource, additionalConnDestination);
            }
            else if (sourcePath.get(0) != destinationPath.get(0) && sourcePath.get(0) != null && destinationPath.get(0) != null && Category.PROCESS.getValue().equals(cache.getComponentInstanceByIndex(sourcePath.get(0)).getCategory()) &&
                    Category.PROCESS.getValue().equals(cache.getComponentInstanceByIndex(destinationPath.get(0)).getCategory())) {
                String additionalConnContext2 = "";
                String additionalConnSource2 = source;
                String additionalConnDestination2 = destination;
                Connection additionalConnConnection2 = new Connection(additionalConnContext2, additionalConnSource2, additionalConnDestination2);
                additionalConnConnection2.setGenerate(Boolean.TRUE);
                additionalConnConnection2.setSocketType("out");
                cache.addConnection(additionalConnConnection2);
                petriNetPager.addNewPage(context, cache.getComponentInstanceByIndex(sourcePath.get(0)).getId(), Boolean.FALSE, null, cache.getComponentInstanceByIndex(sourcePath.get(0)).getName(), false);
                LOG.debug("connection : {}, {}, {} ", additionalConnContext2, additionalConnSource2, additionalConnDestination2);

                String additionalConnContext = destinationPath.get(0).toString();
                String additionalConnSource = destination;
                String additionalConnDestination = destination.substring(0, destination.length() - 1);
                Connection additionalConnConnection = new Connection(additionalConnContext, additionalConnSource, additionalConnDestination);
                additionalConnConnection.setGenerate(Boolean.TRUE);
                additionalConnConnection.setSocketType("in");
                cache.addConnection(additionalConnConnection);
                LOG.debug("connection : {}, {}, {} ", additionalConnContext, additionalConnSource, additionalConnDestination);

                threadConnection = true;
            }

            ConnectionNode destinationNode = getConnectionNode(destinationPath, null, null);
            ConnectionNode sourceNode = getConnectionNode(sourcePath, null, null);
            String socketId = new String();
            String portId = new String();

            if (destinationNode.getPlaceId() != null && sourceNode.getPlaceId() != null && !threadConnection) {
                for (Socket socket : cache.getSOCKETS()) {
                    if (destinationNode.getPlaceId().equals(socket.getSocketId())) {
                        portId = socket.getPortId();
                    } else if (sourceNode.getPlaceId().equals(socket.getSocketId())) {
                        socketId = socket.getSocketId();
                    }
                }
            }

            if (!portId.isEmpty() && !socketId.isEmpty()) {
                for (Socket socket : cache.getSOCKETS()) {
                    if (portId.equals(socket.getPortId()) && !cache.getComponentInstanceById(socket.getComponentId()).getCategory().equals(Category.DEVICE.getValue())) {
                        socket.setSocketId(socketId);
                    }
                }
            }

            if (sourceNode.getCategory().equals(Category.BUS.getValue()) && !portId.isEmpty()) {
                for (Socket socket : cache.getSOCKETS()) {
                    if (destinationNode.getPlaceId().equals(socket.getSocketId())) {
                        socket.setSocketId(sourceNode.getPlaceId());
                    }
                }
            }

            cache.addConnection(newConnection);
        }
        cache.sortConnections();

    }

    public ConnectionNode getConnectionNode(List<Integer> path, ComponentInstance actualComponentInstance, ComponentInstance headComponent) {

        for (int j = 0; j < path.size(); ++j) {
            ComponentInstance processingComponent = actualComponentInstance != null ?
                    actualComponentInstance : cache.getComponentInstanceByIndex(path.get(j));
            if (j == path.size() - 1 && path.size() == 1) {
                return new ConnectionNode(processingComponent.getId(), processingComponent.getDataPort().get(path.get(j)).getId(), processingComponent.getCategory(), null, null,
                        processingComponent.getPeriod(), processingComponent.getPos_X(), processingComponent.getPos_Y(), processingComponent.getName());
            } else if (j == path.size() - 1) {
                return new ConnectionNode(processingComponent.getId(), null, processingComponent.getCategory(), null, null,
                        processingComponent.getPeriod(), processingComponent.getPos_X(), processingComponent.getPos_Y(), processingComponent.getName());
            } else if (j == path.size() - 2) {
                String headComponentId = headComponent != null ? headComponent.getId() : null;
                String headCategory = headComponent != null ? headComponent.getCategory() : null;

                return new ConnectionNode(processingComponent.getId(), processingComponent.getDataPort().get(path.get(j + 1)).getId(),
                        processingComponent.getCategory(), headComponentId, headCategory, processingComponent.getPeriod(),
                        processingComponent.getPos_X(), processingComponent.getPos_Y(), processingComponent.getName());
            } else {
                return getConnectionNode(path.subList(j + 1, path.size()), processingComponent.getComponentInstancesNested().get(path.get(j + 1)),
                        processingComponent);
            }
        }
        return null;
    }

    public void searchSystemName(Element systemInstance) {
        cache.setSystemName(systemInstance.getAttribute("name"));
    }

    private DataPort createPlace(String name, ComponentInstance componentInstance) {
        DataPort place = new DataPort(name, "in");
        componentInstance.getDataPort().add(place);

        return place;
    }

    private ComponentInstance createTransition(String name, ComponentInstance componentInstanceNested, String priority) {
        ComponentInstance transaction = new ComponentInstance(name, Category.GENERATED_TRANS.getValue());

        if (!priority.isEmpty()) {
            transaction.setPriority(priority);
        }

        componentInstanceNested.getComponentInstancesNested().add(transaction);

        return transaction;
    }

    private void connectPlaceAndTransition(String context, DataPort place, String placeDirection, ComponentInstance transition, Boolean bothDir, String timeValue) {
        Connection newConnection = new Connection(context, place.getId(), transition.getId());
        newConnection.setSocketType(placeDirection);

        cache.addConnection(newConnection);

        if (bothDir) {
            String oppositeDirection = "in".equals(placeDirection) ? "out" : "in";
            Connection newConnection2 = new Connection(context, place.getId(), transition.getId());
            newConnection2.setSocketType(oppositeDirection);
            cache.addConnection(newConnection2);

            if (!timeValue.isEmpty()) {
                newConnection2.setPeriodArc("1@+" + timeValue);
            }
        }
    }

    private void connectPlaceAndTransition(String context, DataPort place, String placeDirection, ComponentInstance transition) {
        connectPlaceAndTransition(context, place, placeDirection, transition, false, "");
    }

    private void connectPlaceAndTransition(String context, DataPort place, String placeDirection, ComponentInstance transition, Boolean bothDir) {
        connectPlaceAndTransition(context, place, placeDirection, transition, bothDir, "");
    }
}
