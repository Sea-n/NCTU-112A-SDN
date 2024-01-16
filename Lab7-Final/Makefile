all:
	./build_topo.sh
	onos-app localhost activate org.onosproject.openflow
	onos-app localhost install! target/bridge-1.0-SNAPSHOT.oar
	onos-app localhost install! target/unicastdhcp-1.0-SNAPSHOT.oar
	onos-app localhost install! target/proxyarp-1.0-SNAPSHOT.oar
	onos-app localhost install! vrouter/target/vrouter-1.0-SNAPSHOT.oar
	onos-netcfg localhost config.json
	./dhcp_start.sh
	docker exec h06 dhclient -v

clean:
	for app in vrouter proxyarp unicastdhcp bridge; do \
		onos-app localhost deactivate nycu.sdnfv.$$app; \
		onos-app localhost uninstall nycu.sdnfv.$$app; \
	done
	pkill dhcpd
	./clean_topo.sh || true

pingall:
	@for i in 1 2; do \
		for h in h01 h02 h03 h04 h05 h06; do \
			echo "### $$h ###"; \
			for target in 50.1 50.2 51.2 52.2 53.2 50.100; do \
				docker exec $$h ping -c1 -W1 192.168.$$target | grep ttl; \
			done; \
		done; \
	done

# demo
install_apps:
	onos-app localhost install! apps/proxyarp-1.0-SNAPSHOT.oar
	onos-app localhost install! apps/bridge-1.0-SNAPSHOT.oar
	onos-app localhost install! apps/unicastdhcp-1.0-SNAPSHOT.oar

dhcp_server:
	sudo /usr/sbin/dhcpd 4 -pf /run/dhcp-server-dhcpd.pid -cf ./dhcp/dhcpd.conf vethdhcpovs2
