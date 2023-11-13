#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")" || exit 1

if [[ "${1-}" =~ ^-*h(elp)?$ ]]; then
    cat <<-EOF
	Usage: $0
	EOF
    exit
fi


main() {
	mvn clean install -DskipTests
	onos-app localhost deactivate nctu.winlab.unicastdhcp
	onos-app localhost uninstall nctu.winlab.unicastdhcp
	onos-app localhost install! target/unicastdhcp-1.0-SNAPSHOT.oar
}

main "$@"
