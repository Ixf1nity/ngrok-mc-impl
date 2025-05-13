## 🚀 About

> This plugin enables seamless integration of NGROK into Minecraft servers. It provides an efficient method to tunnel your server, making it publicly accessible without requiring direct port forwarding. With NGROK, you can easily share your server with friends or a wider audience while maintaining security and simplicity.

## 📥 Installation Steps

1. **Download the Plugin**
   - Get the latest `ngrokcom-X.Y-SNAPSHOT.jar ` from [GitHub Releases](https://github.com/Ixf1nity/ngrok-mc-impl/releases)

2. **Locate Plugins Folder**
   ```bash
   /path/to/your/server/plugins/

3. **Install the Plugin**

   - Copy the downloaded `ngrokcom-X.Y-SNAPSHOT.jar ` into the plugins folder

4. **First-Time Setup**

   - Start your server
   - Wait for plugin files to generate (check console for confirmation)
   - Stop the server

5. **Configure Plugin**
   ```bash
   /plugins/NgrokCommunication/config.yml
   ```
   Edit the below configurations
```yaml
  # Ngrok configuration & credentials
NGROK_SETTINGS:
  AUTH_TOKEN: ""
  REGION: "US" # IN, AP, AU, EU, JP, SA, US
  # The region in which NGROK should host your tunnel. Use the closest to you for good ping.
  PORT: 0 # Set to 0 to use the default port of the server.

# Discord IP update messages configuration.
DISCORD_UPDATES:
  ENABLED: false
  BOT_TOKEN: ""
  UPDATE_CHANNEL_ID: ""
  UPDATE_MESSAGE: "**Updated Server IP** : %server_ip%"
  # DO NOT TOUCH, THIS IS MODIFIED BY THE PLUGIN.
  UPDATE_MESSAGE_ID: 0
  
# Dynu configuration & credentials
DYNU_SETTINGS:
  ENABLED: false
  DOMAIN: ""
  CLIENT_ID: ""
  SECRET: ""
```
6. **Finalize Setup**
   - Restart your server
   - Check console for successful connection messages

## 🎉 Ready to Go!

Once NGROK is set up and the Minecraft server is running:
>  A public IP will be generated and displayed in the server console.
    Players can use this IP to connect to your Minecraft server, bypassing traditional networking limitations.
    The plugin also supports Discord integration, providing server status updates or notifications directly to your Discord server.

## DynuDNS: Static Domains for Dynamic Ngrok IPs

## Core Functionality
DynuDNS provides permanent domains that automatically update to point at changing Ngrok IP addresses.

#### Technical Flow
1. **Ngrok Assignment**  
   Generates temporary address:  
   `tcp://0.tcp.ngrok.io:12345` (IP: `52.31.140.23`)

2. **DynuDNS Binding**  
   Maintains static domain:  
   `mc.example.dynu.com` → `52.31.140.23`

3. **Player Access**  
   Connect via:  
   `mc.example.dynu.com`

### Key Advantages
- **Domain Stability**  
  Single memorable address for players
- **Automatic Updates**  
  IP changes handled without player intervention
- **Zero Cost**  
  Free dynamic DNS service tier available

### Installation
1. DynuDNS account
2. Register domain of your choice (e.g., `example.gleeze.com`) in Dynamic DNS Service

![image](https://github.com/user-attachments/assets/c75c9984-dc38-4f26-8e8d-0795aaed1e03)


3. Add DNS Record, set node name as `_minecraft._tcp`; and type to `SRV`; and target to same as the `address of your choice`. __Do not touch anything except these parameters, and save it.__
   
![image](https://github.com/user-attachments/assets/414e65f9-296a-4dbd-be0b-8be138b44156)

4. Edit config.yml of NgrokCommunication plugin, obtain client id & secret from your Dynu account.
```yaml
DYNU_SETTINGS:
  ENABLED: true
  DOMAIN: "example.gleeze.com"
  CLIENT_ID: "your_client_id"
  SECRET: "your_secret"
```
5. 🎉 Enjoy your static address! (i.e., `example.gleeze.com`)




