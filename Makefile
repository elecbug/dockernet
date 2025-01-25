setup:
	sudo apt install openjdk-21-jdk -y || true
	sudo apt install docker.io -y || true
	sudo apt install docker-compose -y || true

build:
	javac -source 21 -target 21 dockernet/Program.java -d bin
	sudo docker build . -t dockernet

up:
	sudo docker-compose down || true
	sudo docker-compose up

down:
	sudo docker-compose down