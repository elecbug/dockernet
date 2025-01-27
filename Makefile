setup:
	sudo apt install openjdk-21-jdk -y || true
	sudo apt install docker.io -y || true
	sudo apt install docker-compose -y || true

build:
	javac ./src/App.java -d ./bin -cp ./src
	sudo docker build . -t dockernet

up:
	sudo docker-compose down || true
	sudo rm -rf ./data || true
	sudo docker-compose up

down:
	sudo docker-compose down
	sudo rm -rf ./data

route:
	curl -X POST "http://localhost:8080/send-packet" -d "destination=10.10.2.2&payload=Hello"
