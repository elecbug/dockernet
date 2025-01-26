setup:
	sudo apt install openjdk-21-jdk -y || true
	sudo apt install docker.io -y || true
	sudo apt install docker-compose -y || true

build:
	javac -source 21 -target 21 src/elecbug/App.java -d bin
	sudo docker build . -t dockernet

up:
	sudo docker-compose down || true
	sudo rm -rf ./data || true
	sudo docker-compose up

down:
	sudo docker-compose down
	sudo rm -rf ./data