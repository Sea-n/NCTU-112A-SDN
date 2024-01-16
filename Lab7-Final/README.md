# Procedure to Setup Demo Environment

## Start ONOS

```
$ ok clean
```

## Install DHCP Server

```
$ sudo ./install_dhcp_server.sh
```

## Build topology

```
$ sudo ./build_topo.sh
```

## Fill in Quagga MAC

Update `quagga-mac` in `config.json`.

## Upload json config

```
$ onos-netcfg localhost config.json
```

## Install your vRouter APP

Install your vRouter APP with `onos-app`.

## Install other APPs

```
$ make install_apps
```

## Run DHCP Server

```
$ make dhcp_server
```
