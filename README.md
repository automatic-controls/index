# ACES Repository Index

## Status Indicators

- ✔ Development Completed
- 🛠 Currently Under Development
- 🔥 Development Expected Soon
- ❄ Development Paused

## Active Projects

### [ACES Equipment Builder](https://github.com/automatic-controls/aces-equipment-builder)
Desktop application that interfaces with *WebCTRL* to generate *EIKON* scripts using custom *.logicsymbol* libraries. Each library can be set to synchronize with a shared network drive. Additional capabilities include maintaining a shared set of favorite *.logicsymbol* and *.logic-script* files.

- ✔ v2.0.2
- ❄ Post to *ALCshare*

### [VSCode For ACES EB](https://github.com/automatic-controls/vscode-aces-equipment-builder)
Extension for [*Visual Studio Code*](https://code.visualstudio.com/) that provides language support for *ACES EB* configuration files.

- ✔ v1.0.1

### WebCTRL Centralizer
Database application that runs as a *Windows* service and a *WebCTRL* add-on that communicates with the database. Synchronizes operator credentials and/or files across all connected *WebCTRL* servers. Additional capabilities include automatic file retrieval and scheduled script execution.

- ❄ v0.1.0-beta
- 🔥 Migrate to *GitHub*

### ACES Commissioner
*WebCTRL* add-on that automates evaluation and enforcement of *ACES* commissioning standards on selected portions of the geographic tree.

- ✔ v1.0.0
- 🔥 Migrate to *GitHub*

### Airflow Parameter Updater
*WebCTRL* add-on that imports and exports *.csv* data related to airflow microblocks in selected portions of the geographic tree.

- ✔ v1.0.0
- ❄ Post to *ALCshare*
- 🔥 Migrate to *GitHub*

### GeoXML Exporter
*WebCTRL* add-on that exports geographic tree *.xml* data used by *Inkscape* to generate *SVG* graphics. Supports an optional regular expression to modify display names.

- ✔ v1.0.0
- ❄ Post to *ALCshare*
- 🔥 Migrate to *GitHub*

### WebCTRL Certificate Manager
Uses [*Let's Encrypt*](https://letsencrypt.org/) with [*CertBot*](https://certbot.eff.org/) to automate *WebCTRL* certificate renewal.

- ✔ v1.0.0
- ❄ Post to *ALCshare*
- 🔥 Migrate to *GitHub*

## Future Projects

### WebCTRL Scripts
*WebCTRL* add-on that provides scripting capabilities to query and modify nodes on the geographic and network tree. Scripts may be synchronized across multiple servers using [*WebCTRL Centralizer*](#webctrl-centralizer). Intended to replace [*ACES Commissioner*](#aces-commissioner).

### Graphics Provider
*WebCTRL* add-on that dynamically generates graphics pages using *HTML* template files with extended syntax to query and modify nodes on the geographic tree. Templates may be synchronized across multiple servers using [*WebCTRL Centralizer*](#webctrl-centralizer).

### PID Tuner
*WebCTRL* add-on that evaluates PID performance and auto-tunes parameters. PID evaluation can be scheduled to recur at regular intervals. When performance ratings drop below acceptable standards, parameters may be auto-tuned.

## Inactive Projects
