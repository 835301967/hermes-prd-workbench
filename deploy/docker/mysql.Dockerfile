# Keep the official database server and initialization contract; omit unused admin tools.
FROM mysql:8.4.11@sha256:24989179f58856db763f418010c1169fecc944e3ec1ea7e921572d83eb070f70
RUN microdnf remove -y mysql-shell && microdnf clean all \
    && rm /usr/local/bin/gosu \
    && test -x /usr/sbin/mysqld && test -x /usr/bin/mysql && test -x /usr/bin/mysqldump
# The upstream entrypoint supports non-root initialization. New volumes inherit image ownership.
USER mysql
