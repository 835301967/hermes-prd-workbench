# Preview residual security review

Reviewed 2026-10-13. Status: awaiting the author’s specific risk disposition; publication is not approved by this document alone.

The first preview distributes audited source, checksums and dependency inventories, with fresh installation only and support limited to macOS arm64 + Docker Desktop Linux containers. Deploy only locally or on a trusted network; it is not a public SaaS release. No prebuilt images, JARs, model credentials or internal history are distributed.

| Residual | Evidence and current exposure | Proposed disposition |
| --- | --- | --- |
| Hermes zlib CVE-2026-85091 | Docker Scout marks Debian1.2.13 High. Debian still lists it as vulnerable despite the upstream description’s newer range; discussion remains unresolved. No product gzprintf/gzvprintf path was found. Python gzip/HTTP decompression uses zlib inflate/deflate interfaces, not those formatted gz write calls. The package is not certified patched. | Accept only for the restricted preview pending a distribution/upstream fix; retain the raw finding and scope dispute. |
| Hermes OpenSSL CVE-2026-35189 | Upstream rates Low: a malicious peer’s certificate during normal TLS may allocate hundreds of MiB and cause denial of service. Model HTTPS uses Python SSL, so this is reachable. Current Debian Bookworm3.0.22 has no packaged fix; a checked Trixie candidate3.5.7 also precedes the required3.5.9, so changing distro alone does not close it. | Use only an operator-selected trusted model endpoint; accept this availability residual for the preview pending packaged fixes. |
| Backend Expat CVE-2026-77214 / CVE-2026-102633 | Scanner rates High for libexpat2.8.5. The fixed2.9.0 is not yet in the configured Alpine3.24 repository (checked package policy). It is a fontconfig dependency used with image-owned configuration/fonts; uploaded Office/XML processing uses Java POI/JAXP, not Expat. No product path that loads user-controlled fontconfig XML was found. | Closed after removing unused fontconfig/fonts/Expat: rebuilt runtime scan has no Expat finding; both real XLSX exports and normal Chinese DOCX/XLSX imports pass without fontconfig. The original raw scans are retained. |

Sources: [Debian zlib status](https://security-tracker.debian.org/tracker/CVE-2026-85091), [upstream range discussion](https://github.com/madler/zlib/issues/1310), [OpenSSL advisory](https://openssl-library.org/news/secadv/20260929.txt), [Debian TLS status](https://security-tracker.debian.org/tracker/CVE-2026-35189), [Alpine security database](https://security.alpinelinux.org/).

Other scan findings are assessed per feature, not blanket ignored: Spring XsltView/wildcard view resolution and SSE view fragments are absent; upstream rates those Medium/Low while Scout labels Critical. Hermes compression, config-writing CLI and Feishu entrypoints are disabled or unused. util-linux privilege/mount features, Perl POD/regex processing, DTLS/CMP and local setuid loader paths have no product entry under the checked non-root/no-new-privileges configuration. These dispositions mean the current deployment lacks the stated trigger; they do not claim upstream source is patched.

MySQL’s final merged filesystem scan identifies141 packages and0 findings. The original image and derived image’s retained historical layers still report removed gosu/admin components; preserve those reports separately. Removing files from the final filesystem does not erase old layers. Future binary/image distribution requires a new all-layer license and security review.

Final seven-mode, upload boundary, restart/restore, browser, source/archive/layer sensitive scan and CI evidence remain separate release gates. Missing gates cannot be waived by accepting these security residuals.
