/*
 * Copyright 2020-present Open Networking Foundation
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
 * limitations under the License.
 */
package nctu.winlab.unicastdhcp;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import java.util.ArrayList;
import java.util.List;

import org.onlab.packet.DHCP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onlab.packet.VlanId;
import org.onlab.packet.DHCP.MsgType;
import org.onlab.packet.dhcp.DhcpOption;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.HostId;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.onlab.packet.DHCP.DHCPOptionCode.OptionCode_DHCPServerIp;
import static org.onlab.packet.DHCP.DHCPOptionCode.OptionCode_MessageType;
import static org.onlab.packet.DHCP.DHCPOptionCode.OptionCode_RequestedIP;

/** Sample Network Configuration Service Application. **/
@Component(immediate = true)
public class AppComponent {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final SrvConfigListener cfgListener = new SrvConfigListener();
  private final ConfigFactory<ApplicationId, SrvConfig> factory = new ConfigFactory<ApplicationId, SrvConfig>(
      APP_SUBJECT_FACTORY, SrvConfig.class, "UnicastDhcpConfig") {
    @Override
    public SrvConfig createConfig() {
      return new SrvConfig();
    }
  };

  private ApplicationId appId;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected NetworkConfigRegistry cfgService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected PacketService packetService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected IntentService intentService;

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected CoreService coreService;

  private PacketProcessor processor = new DhcpPacketProcessor();
  private ConnectPoint dhcpServer;
  private List<MacAddress> installedMacs = new ArrayList<>();
  private List<Intent> installedIntents = new ArrayList<>();

  @Activate
  protected void activate() {
    appId = coreService.registerApplication("nctu.winlab.unicastdhcp");

    cfgService.addListener(cfgListener);
    cfgService.registerConfigFactory(factory);

    packetService.addProcessor(processor, PacketProcessor.director(1));
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
    TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
      .matchEthType(Ethernet.TYPE_IPV4);
    packetService.requestPackets(selector.build(), PacketPriority.CONTROL, appId);
  }

  private void cancelPackets() {
    TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
      .matchEthType(Ethernet.TYPE_IPV4);
    packetService.cancelPackets(selector.build(), PacketPriority.CONTROL, appId);
  }

  private class SrvConfigListener implements NetworkConfigListener {
    @Override
    public void event(NetworkConfigEvent event) {
      if (!(event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)) {
        return;
      }
      if (!event.configClass().equals(SrvConfig.class)) {
        return;
      }

      SrvConfig config = cfgService.getConfig(appId, SrvConfig.class);
      if (config != null) {
        dhcpServer = config.cp();
        log.info("DHCP server is connected to `{}`, port `{}`",
            dhcpServer.deviceId(), dhcpServer.port());
      }
    }
  }


  /*
   * Ref: https://github.com/opennetworkinglab/onos/blob/master/
   * apps/dhcp/app/src/main/java/org/onosproject/dhcp/impl/DhcpManager.java#L615
   */
  private class DhcpPacketProcessor implements PacketProcessor {

    /**
     * Processes the DHCP Payload and initiates a reply to the client.
     *
     * @param context context of the incoming message
     * @param dhcpPayload the extracted DHCP payload
     */
    private void processDhcpPacket(PacketContext context, DHCP dhcpPayload) {
      if (dhcpPayload == null) {
        log.debug("DHCP packet without payload, do nothing");
        return;
      }

      Ethernet packet = context.inPacket().parsed();
      DHCP.MsgType incomingPacketType = null;
      boolean flagIfRequestedIP = false;
      boolean flagIfServerIP = false;
      Ip4Address requestedIP = Ip4Address.valueOf("0.0.0.0");
      Ip4Address serverIP = Ip4Address.valueOf("0.0.0.0");

      for (DhcpOption option : dhcpPayload.getOptions()) {
        if (option.getCode() == OptionCode_MessageType.getValue()) {
          byte[] data = option.getData();
          incomingPacketType = DHCP.MsgType.getType(data[0]);
        }
        if (option.getCode() == OptionCode_RequestedIP.getValue()) {
          byte[] data = option.getData();
          requestedIP = Ip4Address.valueOf(data);
          flagIfRequestedIP = true;
        }
        if (option.getCode() == OptionCode_DHCPServerIp.getValue()) {
          byte[] data = option.getData();
          serverIP = Ip4Address.valueOf(data);
          flagIfServerIP = true;
        }
      }

      if (incomingPacketType == null) {
        log.debug("No incoming packet type specified, ignore it");
        return;
      }

      DHCP.MsgType outgoingPacketType;
      MacAddress clientMac = new MacAddress(dhcpPayload.getClientHardwareAddress());
      VlanId vlanId = VlanId.vlanId(packet.getVlanID());
      HostId hostId = HostId.hostId(clientMac, vlanId);

      /* New code */

      if (incomingPacketType != MsgType.DHCPDISCOVER && incomingPacketType != MsgType.DHCPREQUEST) {
        return;
      }

      if (installedMacs.contains(packet.getSourceMAC())) {
        log.info("Ignoring in-packets for already installed intents.");
        return;
      }
      installedMacs.add(packet.getSourceMAC());

      TrafficSelector.Builder clientSelector = DefaultTrafficSelector.builder()
        .matchEthType(Ethernet.TYPE_IPV4)
        .matchIPProtocol(IPv4.PROTOCOL_UDP)
        .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
        .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT));

      TrafficSelector.Builder serverSelector = DefaultTrafficSelector.builder()
        .matchEthType(Ethernet.TYPE_IPV4)
        .matchIPProtocol(IPv4.PROTOCOL_UDP)
        .matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
        .matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));

      ConnectPoint cp = context.inPacket().receivedFrom();

      /* clientIntent: cp -> dhcpServer */
      PointToPointIntent clientIntent = PointToPointIntent.builder()
        .appId(appId)
        .priority(42)
        .selector(clientSelector.build())
        .filteredIngressPoint(new FilteredConnectPoint(cp))
        .filteredEgressPoint(new FilteredConnectPoint(dhcpServer))
        .build();
      intentService.submit(clientIntent);

      /* serverIntent: dhcpServer -> cp */
      PointToPointIntent serverIntent = PointToPointIntent.builder()
        .appId(appId)
        .priority(42)
        .selector(serverSelector.build())
        .filteredIngressPoint(new FilteredConnectPoint(dhcpServer))
        .filteredEgressPoint(new FilteredConnectPoint(cp))
        .build();
      intentService.submit(serverIntent);

      installedIntents.add(clientIntent);
      log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",  // cp -> dhcpServer
          cp.deviceId(), cp.port(), dhcpServer.deviceId(), dhcpServer.port());

      installedIntents.add(serverIntent);
      log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",  // dhcpServer -> cp
          dhcpServer.deviceId(), dhcpServer.port(), cp.deviceId(), cp.port());
    }

    @Override
    public void process(PacketContext context) {
      Ethernet packet = context.inPacket().parsed();
      if (packet == null) {
        return;
      }

      if (packet.getEtherType() == Ethernet.TYPE_IPV4) {
        IPv4 ipv4Packet = (IPv4) packet.getPayload();

        if (ipv4Packet.getProtocol() == IPv4.PROTOCOL_UDP) {
          UDP udpPacket = (UDP) ipv4Packet.getPayload();

          if (udpPacket.getDestinationPort() == UDP.DHCP_SERVER_PORT &&
              udpPacket.getSourcePort() == UDP.DHCP_CLIENT_PORT) {
            // This is meant for the dhcp server so process the packet here.

            DHCP dhcpPayload = (DHCP) udpPacket.getPayload();
            processDhcpPacket(context, dhcpPayload);
          }
        }
      }
    }
  }
}
