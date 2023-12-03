all:
	ovs-vsctl add-br R123br
	docker-compose up -d --build

clean:
	docker-compose down
	ovs-vsctl del-br R123br

oa:
	onos-app localhost activate org.onosproject.openflow
	onos-app localhost activate org.onosproject.fwd
	onos-app localhost activate org.onosproject.fpm

showroute:
	@for h in R1 R2 R3 R4 R5; do echo "### $$h ###"; docker exec $$h ip ro; done

pingall:
	@for h in h1 h2 h3 h4; do \
		echo "### $$h ###"; \
		for target in 18 19 22 24; do \
			docker exec $$h ping -c1 -W1 10.$$target.0.3 | grep ttl; \
		done; \
	done
