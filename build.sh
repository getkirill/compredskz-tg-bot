#!/bin/bash
pushd $(dirname $0)
git pull && ./gradlew shadowJar && docker compose up -d --no-recreate --build
popd
