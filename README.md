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
