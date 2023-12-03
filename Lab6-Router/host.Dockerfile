FROM ubuntu:22.04

RUN apt update
RUN apt install -y net-tools iproute2 iputils-ping traceroute


CMD ["sleep","infinity"]
