all:
	docker-compose up -d --build
	ovs-vsctl add-br R123br
	ovs-docker add-port R123br R123-br R1 --ipaddress=10.20.0.2/16
	ovs-docker add-port R123br R123-br R2 --ipaddress=10.20.0.3/16
	ovs-docker add-port R123br R123-br R3 --ipaddress=10.20.0.4/16

clean:
	docker-compose down
	ovs-vsctl del-br R123br

onos-app:
	onos-app localhost activate org.onosproject.openflow
	onos-app localhost activate org.onosproject.fwd
	onos-app localhost activate org.onosproject.fpm

show-route:
	@for h in R1 R2 R3 R4 R5 h1 h2 h3 h4; do \
		echo "### $$h ###"; \
		docker exec $$h ip route; \
		echo; \
	done

show-ip:
	@for h in R1 R2 R3 R4 R5 h1 h2 h3 h4; do \
		echo "### $$h ###"; \
		docker exec $$h ip addr; \
		echo; \
	done

pingall:
	@for h in h1 h2 h3 h4; do \
		echo "### $$h ###"; \
		for target in 18 19 22 24; do \
			docker exec $$h ping -c1 -W1 10.$$target.0.3 | grep ttl; \
		done; \
	done
