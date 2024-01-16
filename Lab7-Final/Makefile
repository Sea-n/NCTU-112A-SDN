all:
	./build_topo.sh
	onos-app localhost activate org.onosproject.openflow
	onos-app localhost install! target/bridge-1.0-SNAPSHOT.oar
	onos-app localhost install! target/unicastdhcp-1.0-SNAPSHOT.oar
	onos-app localhost install! target/proxyarp-1.0-SNAPSHOT.oar
	onos-app localhost install! vrouter/target/vrouter-1.0-SNAPSHOT.oar
	onos-netcfg localhost config.json
	sudo /usr/sbin/dhcpd 4 -pf /run/dhcp-server-dhcpd.pid -cf ./dhcp/dhcpd.conf vethdhcpovs2
	docker exec h07 dhclient -v

clean:
	for app in vrouter proxyarp unicastdhcp bridge; do \
		onos-app localhost deactivate nycu.sdnfv.$$app; \
		onos-app localhost uninstall nycu.sdnfv.$$app; \
	done || true
	pkill dhcpd || true
	./clean_topo.sh || true

pingall:
	@for i in 1 2; do \
		for h in h01 h02 h03 h04 h05 h06 h07; do \
			echo "### $$h ###"; \
			for target in 0.1 0.2 1.2 2.2 3.2 4.2 0.100; do \
				docker exec $$h ping -c1 -W1 10.30.$$target | grep ttl; \
			done; \
		done; \
	done
