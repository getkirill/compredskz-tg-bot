#!/bin/bash

docker compose exec db psql -U compredskz -d compredskz -c "$1"