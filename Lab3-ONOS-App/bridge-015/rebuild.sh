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
	onos-app localhost deactivate nctu.winlab.bridge
	onos-app localhost uninstall nctu.winlab.bridge
	onos-app localhost install! target/bridge-015-1.0-SNAPSHOT.oar
}

main "$@"

