FROM node:22.23.1-bookworm-slim AS node
FROM python:3.11.15-slim-bookworm@sha256:d29f48a31a8b408ed19272ca1e7b10ebae13b240a27e862d3d4217c528e2e0c3 AS build
ARG HERMES_COMMIT=6cb88a08748ca43f61db227e9c81e6834c407710
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/* && pip install --no-cache-dir uv==0.13.0
WORKDIR /opt/hermes-agent
RUN git init && git remote add origin https://github.com/NousResearch/hermes-agent.git \
    && git fetch --depth 1 origin "$HERMES_COMMIT" && git checkout --detach FETCH_HEAD \
    && test "$(git rev-parse HEAD)" = "$HERMES_COMMIT"
COPY integrations/hermes-runtime /opt/workbench-runtime
RUN uv venv --python /usr/local/bin/python .venv \
    && uv pip sync --python .venv/bin/python --require-hashes --only-binary :all: /opt/workbench-runtime/requirements.lock.txt \
    && uv pip install --python .venv/bin/python --no-deps --build-constraints /opt/workbench-runtime/build-constraints.txt . \
    && rm -rf .git

FROM python:3.11.15-slim-bookworm@sha256:d29f48a31a8b408ed19272ca1e7b10ebae13b240a27e862d3d4217c528e2e0c3
RUN apt-get update && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/* \
    && python -m pip uninstall -y pip setuptools wheel \
    && groupadd --gid 10002 hermes && useradd --uid 10002 --gid 10002 --create-home hermes \
    && mkdir -p /data/hermes && chown -R hermes:hermes /data
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=build /opt/hermes-agent /opt/hermes-agent
WORKDIR /opt/hermes-agent
COPY integrations /opt/workbench/integrations
COPY scripts/configure-profile.py /opt/workbench/scripts/configure-profile.py
COPY deploy/docker/hermes-entrypoint.sh /opt/workbench/hermes-entrypoint.sh
RUN find /opt/workbench -type d -exec chmod 755 {} + \
    && find /opt/workbench -type f -exec chmod 644 {} +
ENV PATH="/opt/hermes-agent/.venv/bin:$PATH" HERMES_HOME=/data/hermes
USER 10002:10002
ENTRYPOINT ["bash", "/opt/workbench/hermes-entrypoint.sh"]
