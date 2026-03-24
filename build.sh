#!/bin/bash
pushd $(dirname $0)
git pull && ./gradlew shadowJar && docker compose build && docker compose down && docker compose up -d
popd
