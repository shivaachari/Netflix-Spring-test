#!/usr/bin/env bash

./gradlew build --daemon
docker build -t netflixspring/sample-eureka:latest .
