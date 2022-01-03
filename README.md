# ACES Repository Index

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

### [WebCTRL Add-On Development Utility](https://github.com/automatic-controls/webctrl-addon-dev)
*Batch* script used to automate certain aspects of *WebCTRL* add-on development. Features include automated keystore management and dependency collection.

- ✔ v1.0.0

### [Geographic Tree XML Exporter](https://github.com/automatic-controls/geo-xml-export-addon)
*WebCTRL* add-on that exports structural geographic tree data as an *XML* file intended for use in *Inkscape* to create *SVG* graphics. An optional regular expression transform can be used to preprocess display names for *Inkscape* label-text.

- ✔ v1.0.0

## Active Projects

### [Advanced Data Queries](https://github.com/automatic-controls/data-query-addon)
*WebCTRL* add-on that imports and exports *CSV* data according to queries defined in *JSON* files. Supports optional data validation and cleansing.

- ❄ v0.1.0-beta

### WebCTRL Centralizer
Database application that runs as a *Windows* service and a *WebCTRL* add-on that communicates with the database. Synchronizes operator credentials and/or files across all connected *WebCTRL* servers. Additional capabilities include automatic file retrieval and scheduled script execution.

- ❄ v0.1.0-beta
- 🔥 Migrate to *GitHub*

### WebCTRL Certificate Manager
Uses [*Let's Encrypt*](https://letsencrypt.org/) with [*CertBot*](https://certbot.eff.org/) to automate *WebCTRL* certificate renewal.

- 🛠 v1.0.0
- 🔥 Migrate to *GitHub*

## Future Projects

### Graphics Provider
*WebCTRL* add-on that dynamically generates graphics pages using *HTML* template files with extended syntax to query and modify nodes on the geographic tree.

### PID Tuner
*WebCTRL* add-on that evaluates PID performance and auto-tunes parameters. PID evaluation can be scheduled to recur at regular intervals. When performance ratings drop below acceptable standards, parameters may be auto-tuned.
