# Tutorial - Get Started

To run the application, ensure the following prerequisites are met:

- Docker is running on your system.
- A Working JVM (Java Virtual Machine) version 17 or higher.
- A GPU with at least 8GB of VRAM.
- RAM memory exceeding 16GB.

## NVIDIA GPU drivers / NVIDIA Container Toolkit

1. Configure the repository:

```bash
curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey \
    | sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg
curl -s -L https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list \
    | sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' \
    | sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list
sudo apt-get update 
```

2. Install the NVIDIA Container Toolkit packages

```bash
sudo apt-get install -y nvidia-container-toolkit
```

## CBH Cloud Configuration

1. Start a VM with a GPU.
2. Connect to the VM using:
```ssh -i "[KEY]" root@vm.cloud.cbh.kth.se -p [PORT]```
3. Download Docker:
```curl sSL https://get.docker.com/ | sudo sh```
4. Download Nvidia Toolkit on the VM (see NVIDIA GPU drivers / NVIDIA Container Toolkit for command)
5. Update the system:
```apt update && apt upgrade -y && apt autoremove -y```
6. Run:
```sudo nvidia-ctk runtime configure --runtime=docker```
7. Run:
```sudo systemctl restart docker```
8. Download Nividia Drivers on the VM:
```apt install nvidia-driver-XXX-server nvidia-utils-XXX-server -y```
(replace XXX with the latest Linux version, see https://www.nvidia.com/en-us/drivers/unix/ for version)
9. Reboot the VM (can take a while to reconnect to the VM).
10. Run:
```nvidia-smi``` in VM to see if GPU is enabled, otherwise redo step 5,6 & 7.
11. Run:
```docker run -d --gpus=all -v ollama:/root/.ollama -p 11434:11434 --name ollama ollama/ollama```
12. Run:
```git clone https://github.com/SamuelTegsten/RealTimeEmbedding.git```
13. Run:
```sudo apt update```
14. Run:
```sudo apt install default-jdk maven```
15. Run:
```cd RealTimeEmbedding/```
14. Run:
```mvn clean compile install```
15. Run:
```java -jar target/LangBotChain-0.0.1-SNAPSHOT.jar```

Remember to reaquire the GPU after the duration is up, the program will terminate without a GPU.

## Neo4j Cloud Configuration
1. Run:
```wget -O - https://debian.neo4j.com/neotechnology.gpg.key | sudo apt-key add -
echo 'deb https://debian.neo4j.com stable 5' | sudo tee -a /etc/apt/sources.list.d/neo4j.list
sudo apt-get update```
2. Run: sudo apt-get install neo4j-enterprise=1:[Latest Version]
2.1. (Optional step , if you do not accept the enterprise conditions via the terminal):
echo "neo4j-enterprise neo4j/question select I ACCEPT" | sudo debconf-set-selections
echo "neo4j-enterprise neo4j/license note" | sudo debconf-set-selections
3. Run: 
systemctl start neo4j
4. Run: 
Cypher-shell
5. Enter neo4j for username and password.
6. Change the password after the prompt to do so.
7. Access via bolt://localhost:7687
8. Or through the browser at http://localhost:7474

# External Services
## UI (Supports normal conversations, files (txt & pdf) and url, embeddings is work in progress.
- ```https://realtimeembeddingui.app.cloud.cbh.kth.se/``` 
- Save the UUID in options if you want the conversations to be used between different browsers / units.

## Ollama server service
- ```https://ollamaserverinfo.app.cloud.cbh.kth.se/```
- Can pull new models, delete existing ones, see the ollama server status and more.
