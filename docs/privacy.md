# Privacy and data

Uploaded material is persisted in the project database and generated local upload paths. Hermes receives normalized source text and sends necessary content to the model service configured by the deployer. The provider may retain data according to its own policy. Choose an appropriate provider before uploading private information.

There is no third-party analytics service. Browser usage collection is off by default and requires both PRD_TELEMETRY_ENABLED/VITE_TELEMETRY_ENABLED to be enabled. It stores limited event identifiers/counts, account identifiers and timestamps in the local database; arbitrary metadata and raw client/exception messages are discarded. Events older than the configured 1–365-day retention period are removed by a daily in-process job when collection is enabled. Turning collection off does not delete old rows; administrators should apply an explicit reviewed retention action if erasure is needed.

Run history, drafts, uploaded materials, review decisions and update notes are persistent operational data, separate from optional telemetry. Backups and Hermes private runtime files may contain source text, complete conversations or model responses; they are private deployment state and excluded from source packages and build contexts. Do not attach them to public issues.

Session tokens are held by the browser. Generated HTML is isolated from that browser origin in the workbench and new-tab wrappers. Exported raw HTML/ZIP files are separate untrusted artifacts; opening them outside the workbench bypasses workbench preview protections.

No automated global erasure or legal compliance certification is claimed. Deletion/retention requests require the deployer to account for database, uploads, backups, Hermes state and provider retention together.
