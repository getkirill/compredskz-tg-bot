#!/bin/bash
pushd $(dirname $0)
git pull && && docker compose up -d --no-recreate
popd
