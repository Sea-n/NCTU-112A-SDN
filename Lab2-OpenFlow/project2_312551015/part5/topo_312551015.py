from mininet.topo import Topo

class Project2_Topo_312551015(Topo):
    def __init__(self):
        Topo.__init__(self)

        s1 = self.addSwitch('s1')
        h1 = self.addHost('h1', ip='192.168.130.1/27', mac='00:00:00:00:00:01')
        h2 = self.addHost('h2', ip='192.168.130.2/27', mac='00:00:00:00:00:02')
        h3 = self.addHost('h3', ip='192.168.130.3/27', mac='00:00:00:00:00:03')

        self.addLink(s1, h1)
        self.addLink(s1, h2)
        self.addLink(s1, h3)

topos = {'topo_312551015': Project2_Topo_312551015}
