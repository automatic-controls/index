# ACES Repository Index

WebCTRL is a trademark of Automated Logic Corporation.  Any other trademarks mentioned herein are the property of their respective owners.

## Status Indicators

- ✔ Development Completed
- 🛠 Currently Under Development
- 🔥 Development Expected Soon
- ❄ Development Paused

## Completed Projects

### [ACES Equipment Builder](https://github.com/automatic-controls/aces-equipment-builder)
Desktop application that interfaces with *WebCTRL* to generate *EIKON* scripts using custom *.logicsymbol* libraries. Each library can be set to synchronize with a shared network drive. Additional capabilities include maintaining a shared set of favorite *.logicsymbol* and *.logic-script* files.

- ✔ v2.0.4

### [VSCode For ACES EB](https://github.com/automatic-controls/vscode-aces-equipment-builder)
Extension for [*Visual Studio Code*](https://code.visualstudio.com/) that provides language support for *ACES EB* configuration files.

- ✔ v1.0.2

### [Certificate Manager for WebCTRL](https://github.com/automatic-controls/cert-manager-for-webctrl)
Batch script which provides commands to assist with the management of SSL certificates for *WebCTRL*.

- ✔ v1.0.0

### [Add-On Development Utility for WebCTRL](https://github.com/automatic-controls/addon-dev-script)
*Batch* script used to automate certain aspects of *WebCTRL* add-on development. Features include automated keystore management and dependency collection.

- ✔ v1.0.1

### [Geographic Tree XML Exporter](https://github.com/automatic-controls/geo-xml-export-addon)
*WebCTRL* add-on that exports structural geographic tree data as an *XML* file intended for use in *Inkscape* to create *SVG* graphics. An optional regular expression transform can be used to preprocess display names for *Inkscape* label-text.

- ✔ v1.0.0

### [Report FTP](https://github.com/automatic-controls/report-ftp-addon)
*WebCTRL* add-on that can be configured to send scheduled reports to a remote server using the FTP, FTPS, or SFTP protocols.

- ✔ v0.1.1-beta

## Active Projects

### [Centralizer for WebCTRL](https://github.com/automatic-controls/centralizer-for-webctrl)
Database application that runs as a *Windows* service and a *WebCTRL* add-on that communicates with the database. Synchronizes operator credentials and/or files across all connected *WebCTRL* servers. Additional capabilities include automatic file retrieval and scheduled script execution.

- 🛠 v0.1.0-beta

### [Airflow Damper Testing](https://github.com/automatic-controls/airflow-test-addon)
WebCTRL add-on that evaluates airflow responsiveness to damper commands according to design parameters.

- 🛠 v0.1.0-beta

### [Advanced Data Queries](https://github.com/automatic-controls/data-query-addon)
*WebCTRL* add-on that imports and exports *CSV* data according to queries defined in *JSON* files. Supports optional data validation and cleansing.

- ❄ v0.1.0-beta

## Future Projects

### PID Tuner
*WebCTRL* add-on that evaluates PID performance and auto-tunes parameters. PID evaluation can be scheduled to recur at regular intervals. When performance ratings drop below acceptable standards, parameters may be auto-tuned.

### Graphics Provider
*WebCTRL* add-on that dynamically generates graphics pages using *HTML* template files with extended syntax to query and modify nodes on the geographic tree.

## Untracked Projects

### [ACES Commissioner](untracked-projects/aces-commissioner)
*WebCTRL* add-on that helps to enforce various ACES commissioning standards. Will be replaced by [Advanced Data Queries](#advanced-data-queries).

### [Airflow Parameter Updater](untracked-projects/airflow-parameter-updater)
*WebCTRL* add-on that imports and exports airflow configuration parameters as CSV data. Will be replaced by [Advanced Data Queries](#advanced-data-queries).

### Patches for *ALC*'s *Inkscape* Extension
I've made various minor modifications to the *Python* extension *ALC* created for *Inkscape*. I don't know how *ALC* has licensed this extension, so I am reluctant to put the relevant files on *GitHub*.
