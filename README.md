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

# External Services
## UI (Supports normal conversations, files (txt & pdf) and url, embeddings is work in progress.
- ```https://realtimeembeddingui.app.cloud.cbh.kth.se/```
Save the UUID in options if you want the conversations to be used between different browsers / units.

## Ollama server service
- ```https://ollamaserverinfo.app.cloud.cbh.kth.se/```
Can pull new models, delete existing ones, see the ollama server status and more.


15. Run:
```java -jar target/LangBotChain-0.0.1-SNAPSHOT.jar```
