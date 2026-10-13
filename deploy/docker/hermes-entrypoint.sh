#!/usr/bin/env bash
set -euo pipefail
export API_SERVER_ENABLED=true API_SERVER_HOST=0.0.0.0 API_SERVER_PORT=8643 API_SERVER_MODEL_NAME=prd-workbench
export API_SERVER_KEY="$(tr -d '\r\n' < /run/secrets/hermes-api-key)"
export PRD_PLUGIN_KEY="$(tr -d '\r\n' < /run/secrets/plugin-key)"
export PRD_MODEL_API_KEY="$(tr -d '\r\n' < /run/secrets/model-api-key)"
case "${PRD_MODEL_PROVIDER:-custom:workbench}" in
  openai) export OPENAI_API_KEY="${PRD_MODEL_API_KEY}" ;;
  openrouter) export OPENROUTER_API_KEY="${PRD_MODEL_API_KEY}" ;;
esac
python /opt/workbench/scripts/configure-profile.py /opt/workbench "${HERMES_HOME}/profiles/prd-workbench"
exec hermes -p prd-workbench gateway run
