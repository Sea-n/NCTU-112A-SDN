/*
 * Author: Sean Wei <me@sean.taipei>
 */
package sean.nctu.bridge;

import com.google.common.collect.Maps;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Component(immediate = true)
public class LearningBridge {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ApplicationId appId;

    protected Map<DeviceId, Map<MacAddress, PortNumber>> macTables = Maps.newConcurrentMap();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    private SeanPacketProcessor processor = new SeanPacketProcessor();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("sean.nctu.bridge");
        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
    }

    @Modified
    public void modified(ComponentContext context) {
        requestIntercepts();
    }

    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        packetService.requestPackets(selector.matchEthType(Ethernet.TYPE_IPV4).build(),
                PacketPriority.REACTIVE, appId);
        packetService.requestPackets(selector.matchEthType(Ethernet.TYPE_ARP).build(),
                PacketPriority.REACTIVE, appId);
        // TODO: Ethernet.TYPE_IPV6
    }


    private class SeanPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            ConnectPoint cp = pkt.receivedFrom();

            macTables.putIfAbsent(cp.deviceId(), Maps.newConcurrentMap());
            Map<MacAddress, PortNumber> macTable = macTables.get(cp.deviceId());

            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            if (macTable.get(srcMac) == null) {
                log.info("Add an entry to the port table of `" + cp.deviceId() + "`. " +
                         "MAC address: `" + srcMac + "` => Port: `" + cp.port() + "`.");
                macTable.put(srcMac, cp.port());
            }

            PortNumber outPort = macTable.get(dstMac);
            if (outPort != null) {
                log.info("MAC address `" + dstMac + "` is matched on `" + cp.deviceId() + "`. Install a flow rule.");

                context.treatmentBuilder().setOutput(outPort);
                TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthSrc(srcMac).matchEthDst(dstMac).build();
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(outPort).build();
                if (false) {
                    FlowRule fr = DefaultFlowRule.builder()
                        .fromApp(appId)
                        .forDevice(cp.deviceId())
                        .withSelector(selector)
                        .withTreatment(treatment)
                        .withPriority(30)
                        .makeTemporary(30)
                        .build();

                    flowRuleService.applyFlowRules(fr);
                } else {
                    ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                        .fromApp(appId)
                        .withSelector(selector)
                        .withTreatment(treatment)
                        .withPriority(30)
                        .makeTemporary(30)
                        .withFlag(ForwardingObjective.Flag.VERSATILE)
                        .add();

                    flowObjectiveService.forward(cp.deviceId(), forwardingObjective);
                }

                context.send();
            } else {
                log.info("MAC address `" + dstMac + "` is missed on `" + cp.deviceId() + "`. Flood the packet.");

                context.treatmentBuilder().setOutput(PortNumber.FLOOD);
                context.send();
            }
        }
    }
}
