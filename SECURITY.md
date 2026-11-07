# Security Policy

EcoWi stores EcoFlow account credentials on the phone and connects to EcoFlow's cloud, so security problems matter even for a small project.

## Supported versions

Only the latest code on the `master` branch (and the latest release, once releases exist) receives security fixes.

## Reporting a vulnerability

**Please don't open a public issue for security problems.**

Report them privately through GitHub's private vulnerability reporting: open the repository's **Security** tab → **Report a vulnerability**, or go to <https://github.com/FranciscoFrank/EcoWi/security/advisories/new>.

Please include:

- what the problem is and what an attacker could do with it;
- steps to reproduce, or a proof of concept;
- the EcoWi version or commit, phone model and Android / One UI version.

**Never include real credentials, tokens, MQTT passwords or full serial numbers** in a report. Use redacted or fake values.

You should get a first reply within about a week. Once a fix is ready, it will be published together with an advisory that credits you, unless you prefer to stay anonymous.

## Scope

In scope:

- how EcoWi stores and handles credentials, sessions and MQTT certificates (`data/SecureStore.kt`, `data/Settings.kt`);
- anything that could leak account data, credentials or station data to other apps or third parties;
- exported Android components (receivers, services, activities) that could be abused by other apps;
- parsing of data received from the network (`core/ecoflow/`, `core/proto/`, `data/`).

Out of scope:

- vulnerabilities in EcoFlow's own servers, apps or firmware. Report those to EcoFlow directly;
- issues that need a rooted or already compromised device;
- the fact that EcoWi uses unofficial EcoFlow interfaces (see the [Disclaimer](README.md#disclaimer)).
