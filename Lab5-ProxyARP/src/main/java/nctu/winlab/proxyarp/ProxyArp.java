/*
 * Author: Sean Wei <me@sean.taipei>
 */
package nctu.winlab.proxyarp;

import com.google.common.collect.Maps;
import java.nio.ByteBuffer;
import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
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
public class ProxyArp {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ApplicationId appId;

    protected Map<DeviceId, Map<Ip4Address, MacAddress>> arpTables = Maps.newConcurrentMap();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    private SeanPacketProcessor processor = new SeanPacketProcessor();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.ProxyArp");
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

            /* Layer 1 */
            ConnectPoint cp = pkt.receivedFrom();
            arpTables.putIfAbsent(cp.deviceId(), Maps.newConcurrentMap());
            Map<Ip4Address, MacAddress> arpTable = arpTables.get(cp.deviceId());

            /* Layer 2 */
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            if (ethPkt.getEtherType() != Ethernet.TYPE_ARP) {
                return;
            }

            ARP arpPkt = (ARP) ethPkt.getPayload();
            Ip4Address srcIp = Ip4Address.valueOf(arpPkt.getSenderProtocolAddress());
            Ip4Address dstIp = Ip4Address.valueOf(arpPkt.getTargetProtocolAddress());

            if (arpTable.get(srcIp) == null) {
                arpTable.put(srcIp, srcMac);
            }

            if (arpPkt.getOpCode() == ARP.OP_REQUEST) {
                MacAddress resMac = arpTable.get(dstIp);
                if (resMac == null) {
                    log.info("TABLE MISS. Send request to edge ports");
                    context.treatmentBuilder().setOutput(PortNumber.FLOOD);
                    context.send();
                } else {
                    log.info("TABLE HIT.  Requested MAC = " + resMac);
                    /* Made a ARP Reply based on resMac */
                    Ethernet arpReplyEth = ARP.buildArpReply(dstIp, resMac, ethPkt);
                    ByteBuffer arpReplyBuf = ByteBuffer.wrap(arpReplyEth.serialize());
                    TrafficTreatment treatment = context.treatmentBuilder().setOutput(cp.port()).build();
                    OutboundPacket arpReplyPkt = new DefaultOutboundPacket(cp.deviceId(), treatment, arpReplyBuf);
                    packetService.emit(arpReplyPkt);
                }
                // log.info("ARP Req: " + srcIp + " (" + srcMac + ") -> " + dstIp + " (" + dstMac + ")");
            } else if (arpPkt.getOpCode() == ARP.OP_REPLY) {
                log.info("RECV REPLY. Requested MAC = " + srcMac);
                // log.info("ARP Reply: " + srcIp + " (" + srcMac + ") -> " + dstIp + " (" + dstMac + ")");
            } else {
                log.info("ARP Op" + arpPkt.getOpCode() + ": "
                        + srcIp + " (" + srcMac + ") -> " + dstIp + " (" + dstMac + ")");
            }
        }
    }
}
