package nycu.sdnfv.vrouter;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.Host;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.routeservice.ResolvedRoute;
import org.onosproject.routeservice.RouteService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class VRouter {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final RouterConfigListener cfgListener = new RouterConfigListener();

    private final ConfigFactory<ApplicationId, RouterConfig> factory = new ConfigFactory<ApplicationId, RouterConfig>(
        APP_SUBJECT_FACTORY, RouterConfig.class, "router") {
        @Override
        public RouterConfig createConfig() {
            return new RouterConfig();
        }
    };

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected RouteService routeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected InterfaceService intfService;

    private ApplicationId appId;

    private PacketProcessor processor = new VRouterProcessor();

    private ConnectPoint quagga;
    private MacAddress quaggaMac;
    private IpAddress vIP;
    private MacAddress vMac;
    private List<IpAddress> peers;

    private List<MacAddress> installedMacs = new ArrayList<>();
    private List<Intent> installedIntents = new ArrayList<>();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nycu.sdnfv.vrouter");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);

        packetService.addProcessor(processor, PacketProcessor.director(6));
        requestPackets();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);
        packetService.removeProcessor(processor);
        cancelPackets();

        for (Intent intent : installedIntents) {
            intentService.withdraw(intent);
        }

        log.info("Stopped");
    }

    private void requestPackets() {
        packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
                PacketPriority.REACTIVE, appId);
        packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(),
                PacketPriority.REACTIVE, appId);
    }

    private void cancelPackets() {
        packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
                PacketPriority.REACTIVE, appId);
        packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(),
                PacketPriority.REACTIVE, appId);
    }

    private class VRouterProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            ConnectPoint cp = pkt.receivedFrom();

            if (ethPkt == null) {
                return;
            }

            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();
            log.info(srcMac + " => " + dstMac);

            if (ethPkt.getEtherType() != Ethernet.TYPE_IPV4) {
                return;
            }

            IPv4 ipPkt = (IPv4) ethPkt.getPayload();
            IpAddress srcIp = IpAddress.valueOf(ipPkt.getSourceAddress());
            IpAddress dstIp = IpAddress.valueOf(ipPkt.getDestinationAddress());
            log.info(srcIp + " (" + srcMac + ") => " + dstIp + " (" + dstMac + ")");

            MacAddress hostMac = hostService.getHostsByIp(dstIp).stream()
                .map(Host::mac).findFirst().orElse(null);
            ConnectPoint hostCp = hostService.getHostsByIp(dstIp).stream()
                .map(Host::location).findFirst().orElse(null);

            if (hostMac == null) {
                log.info("hostMac = null");
            } else {
                log.info("hostMac = " + hostMac);

                TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPDst(dstIp.toIpPrefix())
                    .build();

                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setEthSrc(vMac)
                    .setEthDst(hostMac)
                    .build();

                PointToPointIntent intent = PointToPointIntent.builder()
                    .appId(appId)
                    .selector(selector)
                    .treatment(treatment)
                    .filteredIngressPoint(new FilteredConnectPoint(cp))
                    .filteredEgressPoint(new FilteredConnectPoint(hostCp))
                    .build();

                intentService.submit(intent);
                installedIntents.add(intent);

                context.block();
                return;
            }

            Optional<ResolvedRoute> route = routeService.longestPrefixLookup(dstIp);
            if (route.isPresent()) {
                log.info("route not found");
                return;
            }

            MacAddress nextHopMac = hostService.getHostsByIp(route.get().nextHop().getIp4Address())
                .stream().map(Host::mac).findFirst().orElse(null);
            ConnectPoint egressPoint = intfService.getMatchingInterface(
                route.get().nextHop().getIp4Address()).connectPoint();
            log.info("nextHop = " + route.get().nextHop() + " (" + nextHopMac + ")");

            if (nextHopMac == null) {
                return;
            }

            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setEthSrc(quaggaMac)
                .setEthDst(nextHopMac)
                .build();

            TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(srcIp.toIpPrefix())
                .matchIPDst(dstIp.toIpPrefix())
                .build();

            PointToPointIntent intent = PointToPointIntent.builder()
                .appId(appId)
                .selector(selector)
                .treatment(treatment)
                .filteredIngressPoint(new FilteredConnectPoint(cp))
                .filteredEgressPoint(new FilteredConnectPoint(egressPoint))
                .build();

            intentService.submit(intent);
            installedIntents.add(intent);

            log.info(quaggaMac + " => " + nextHopMac + ", intent = " + intent);
            context.block();
        }
    }

    private class RouterConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if (event.type() != CONFIG_ADDED && event.type() != CONFIG_UPDATED) {
                return;
            }
            if (!event.configClass().equals(RouterConfig.class)) {
                return;
            }

            RouterConfig config = cfgService.getConfig(appId, RouterConfig.class);
            if (config == null) {
                return;
            }

            quagga = config.quagga();
            quaggaMac = config.quaggaMac();
            vIP = config.vIP();
            vMac = config.vMac();
            peers = config.peers();

            for (Intent intent : installedIntents) {
                intentService.withdraw(intent);
            }

            for (IpAddress peerAddr : peers) {
                ConnectPoint interfaceCp = intfService.getMatchingInterface(peerAddr).connectPoint();
                if (interfaceCp == null) {
                    continue;
                }

                IpAddress interfaceIp = intfService.getInterfacesByPort(interfaceCp).stream()
                    .map(Interface::ipAddressesList)
                    .flatMap(List::stream)
                    .findFirst()
                    .orElse(null)
                    .ipAddress();

                // Outgoing
                TrafficSelector.Builder outgoingSelector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPDst(IpPrefix.valueOf(peerAddr, 32));

                PointToPointIntent outgoingIntent = PointToPointIntent.builder()
                    .appId(appId)
                    .selector(outgoingSelector.build())
                    .filteredEgressPoint(new FilteredConnectPoint(interfaceCp))
                    .filteredIngressPoint(new FilteredConnectPoint(quagga))
                    .build();

                // Incoming
                TrafficSelector.Builder incomingSelector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPDst(IpPrefix.valueOf(interfaceIp, 32));

                PointToPointIntent incomingIntent = PointToPointIntent.builder()
                    .appId(appId)
                    .selector(incomingSelector.build())
                    .filteredEgressPoint(new FilteredConnectPoint(quagga))
                    .filteredIngressPoint(new FilteredConnectPoint(interfaceCp))
                    .build();

                intentService.submit(outgoingIntent);
                installedIntents.add(outgoingIntent);
                intentService.submit(incomingIntent);
                installedIntents.add(incomingIntent);
            }
        }
    }
}
