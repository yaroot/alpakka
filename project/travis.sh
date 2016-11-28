#!/usr/bin/env bash

[[ "$TRAVIS_PULL_REQUEST" == "false"
&& "$TRAVIS_BRANCH" == "master"
&& "$TRAVIS_SECURE_ENV_VARS" == "true"
]]
on_the_master_branch=$?

[[ "$TRAVIS_TAG" != ""
&& "$TRAVIS_SECURE_ENV_VARS" == "true"
]]
on_a_tag=$?

if [[ $on_a_tag == 0 ]]; then
  TASKS="publish amqp/bintraySyncMavenCentral"
  mkdir ~/.bintray
  cat > ~/.bintray/.credentials <<EOF
realm = Bintray API Realm
host = api.bintray.com
user = $BINTRAY_USER
password = $BINTRAY_PASS
EOF

  mkdir -p ~/.sbt/0.13
  cat > ~/.sbt/0.13/sonatype.sbt <<EOF
credentials += Credentials("Sonatype Nexus Repository Manager",
  "oss.sonatype.org",
  "$SONATYPE_USER",
  "$SONATYPE_PASS")
EOF
else
  TASKS=test
fi

sbt ++$TRAVIS_SCALA_VERSION $TASKS
