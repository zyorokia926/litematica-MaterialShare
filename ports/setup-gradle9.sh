#!/bin/bash
# Installs Gradle 9.2.0 into /opt/gradle9 (Minecraft 1.20.5 and later need Loom 1.14, which needs Gradle 9.2). Run once on a new machine.
set -e
mkdir -p /opt/gradle9 && cd /opt/gradle9
curl -sSL -o g.zip https://services.gradle.org/distributions/gradle-9.2.0-bin.zip
unzip -q -o g.zip && rm g.zip
/opt/gradle9/gradle-9.2.0/bin/gradle --version | grep Gradle
