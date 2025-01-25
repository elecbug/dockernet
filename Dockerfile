# FROM ubuntu:22.04 AS builder

# RUN apt-get update && apt-get install -y openjdk-8-jdk
# RUN mkdir /app
# WORKDIR /app
# COPY java/* /app

# RUN javac -source 8 -target 8 main.java -d .

FROM ubuntu:22.04

RUN apt-get update
RUN apt install openjdk-21-jre -y

ENV DEVICE=null

RUN mkdir /app
COPY bin/dockernet /app/dockernet
WORKDIR /app

CMD java dockernet.Program $DEVICE
