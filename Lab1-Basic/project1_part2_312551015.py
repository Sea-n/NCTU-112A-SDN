from mininet.topo import Topo

class Project1_Topo_312551015(Topo):
    def __init__(self):
        Topo.__init__(self)

        s1 = self.addSwitch('S1')
        s2 = self.addSwitch('S2')
        s3 = self.addSwitch('S3')
        s4 = self.addSwitch('S4')
        s5 = self.addSwitch('S5')

        h1 = self.addHost('h1')
        h2 = self.addHost('h2')
        h3 = self.addHost('h3')
        h4 = self.addHost('h4')
        h5 = self.addHost('h5')

        self.addLink(s1, h1)
        self.addLink(s2, h2)
        self.addLink(s3, h3)
        self.addLink(s4, h4)
        self.addLink(s5, h5)

        self.addLink(s1, s2)
        self.addLink(s2, s3)
        self.addLink(s2, s4)
        self.addLink(s2, s5)

topos = {'topo_part2_312551015': Project1_Topo_312551015}
