from mininet.topo import Topo

class Project3_Topo_312551015(Topo):
    def __init__(self):
        Topo.__init__(self)

        s1 = self.addSwitch('s1')
        s2 = self.addSwitch('s2')
        s3 = self.addSwitch('s3')
        s4 = self.addSwitch('s4')
        s5 = self.addSwitch('s5')
        s6 = self.addSwitch('s6')
        s7 = self.addSwitch('s7')
        s8 = self.addSwitch('s8')
        s9 = self.addSwitch('s9')

        h1 = self.addHost('h1')
        h2 = self.addHost('h2')
        h3 = self.addHost('h3')
        h4 = self.addHost('h4')

        self.addLink(s1, h1)
        self.addLink(s3, h2)
        self.addLink(s7, h3)
        self.addLink(s9, h4)

        self.addLink(s1, s2)
        self.addLink(s2, s3)
        self.addLink(s2, s5)
        self.addLink(s4, s5)
        self.addLink(s5, s6)
        self.addLink(s5, s8)
        self.addLink(s7, s8)
        self.addLink(s8, s9)

topos = {'topo_312551015': Project3_Topo_312551015}
