package nycu.sdnfv.vrouter;

import java.util.List;
import org.onlab.packet.MacAddress;
import org.onlab.packet.IpAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.ConnectPoint;

public class RouterConfig extends Config<ApplicationId> {

  public ConnectPoint quagga() {
    return ConnectPoint.deviceConnectPoint(get("quagga", null));
  }

  public MacAddress quaggaMac() {
    return MacAddress.valueOf(get("quagga-mac", null));
  }

  public IpAddress vIP() {
    return IpAddress.valueOf(get("virtual-ip", null));
  }

  public MacAddress vMac() {
    return MacAddress.valueOf(get("virtual-mac", null));
  }

  public List<IpAddress> peers() {
      return getList("peers", IpAddress::valueOf, null);
  }
}
