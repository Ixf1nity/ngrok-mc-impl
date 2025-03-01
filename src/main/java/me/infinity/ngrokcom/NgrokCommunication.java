package me.infinity.ngrokcom;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.conf.JavaNgrokConfig;
import com.github.alexdlaird.ngrok.installer.NgrokInstaller;
import com.github.alexdlaird.ngrok.installer.NgrokVersion;
import com.github.alexdlaird.ngrok.protocol.CreateTunnel;
import com.github.alexdlaird.ngrok.protocol.Proto;
import com.github.alexdlaird.ngrok.protocol.Region;
import com.github.alexdlaird.ngrok.protocol.Tunnel;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public final class NgrokCommunication extends JavaPlugin implements EventListener {

    private JDA client;
    private NgrokClient ngrokClient;
    private String publicIp;
    private String dynuClientId;
    private String dynuSecret;
    private String dynuToken;
    private String domain;
    private String domainId;
    private String dnsesPort;
    
    private boolean discordModule;
    private boolean discordModuleStatus = false;
    private boolean dynu;
    private int ngrokPort = 25565; // Default Minecraft server port
    
    @Override
    public void onEnable() {

        Logger.getLogger(String.valueOf(com.github.alexdlaird.ngrok.process.NgrokProcess.class)).setLevel(Level.OFF);

        this.saveDefaultConfig();
        this.discordModule = this.getConfig().getBoolean("DISCORD_UPDATES.ENABLED");
        this.dynu = this.getConfig().getBoolean("DYNU_SETTINGS.ENABLED");
        String ngrokAuthToken = this.getConfig().getString("NGROK_SETTINGS.AUTH_TOKEN");
        int port = this.getConfig().getInt("NGROK_SETTINGS.PORT");
        
        if (port == 0) {
			ngrokPort = getServer().getPort();
		} else {
			ngrokPort = port;
		}
        
        if (ngrokAuthToken == null || ngrokAuthToken.isEmpty()) {
            this.getLogger().warning("Ngrok authentication token is missing in the config. Shutting down...");
            this.setEnabled(false);
            return;
        }

        if (discordModule) {
            String botToken = this.getConfig().getString("DISCORD_UPDATES.BOT_TOKEN");
            if (botToken == null || botToken.isEmpty()) {
                this.getLogger().warning("Bot token is missing in the config. Shutting down...");
                this.setEnabled(false);
                return;
            }

            this.client = JDABuilder.createDefault(botToken)
                    .setStatus(OnlineStatus.DO_NOT_DISTURB)
                    .enableIntents(Arrays.asList(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES))
                    .build();

            this.client.addEventListener(this);

            try {
                this.client.awaitReady();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        final JavaNgrokConfig javaNgrokConfig = new JavaNgrokConfig.Builder()
                .withNgrokVersion(NgrokVersion.V3)
                .withRegion(Region.valueOf(Objects.requireNonNull(this.getConfig().getString("NGROK_SETTINGS.REGION")).toUpperCase()))
                .build();

        this.ngrokClient = new NgrokClient.Builder()
                .withNgrokInstaller(new NgrokInstaller())
                .withJavaNgrokConfig(javaNgrokConfig)
                .build();

        this.ngrokClient.getNgrokProcess().setAuthToken(this.getConfig().getString("NGROK_SETTINGS.AUTH_TOKEN"));

        final CreateTunnel createTunnel = new CreateTunnel.Builder()
                .withProto(Proto.TCP)
                .withAddr(ngrokPort) // Use the configured Ngrok port
                .build();

        final Tunnel tunnel = ngrokClient.connect(createTunnel);
        this.publicIp = tunnel.getPublicUrl().replace("tcp://", "");

        if (discordModuleStatus) {
            String updateMessage = this.getConfig().getString("DISCORD_UPDATES.UPDATE_MESSAGE");
            if (updateMessage != null && !updateMessage.isEmpty()) {
                TextChannel messageChannel = client.getTextChannelById(Objects.requireNonNull(this.getConfig().getString("DISCORD_UPDATES.UPDATE_CHANNEL_ID")));
                if (messageChannel != null) {
                    long updateMessageId = this.getConfig().getLong("DISCORD_UPDATES.UPDATE_MESSAGE_ID");
                    if (updateMessageId == 0) {
                        CompletableFuture<Message> message = messageChannel.sendMessage(MessageCreateData.fromContent(updateMessage.replace("%server_ip%", publicIp))).submit();
                        try {
                            this.getConfig().set("DISCORD_UPDATES.UPDATE_MESSAGE_ID", message.get().getIdLong());
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        messageChannel.editMessageById(updateMessageId, MessageEditData.fromContent(updateMessage.replace("%server_ip%", publicIp))).queue();
                    }
                } else {
                    this.getLogger().warning("IP update channel is null. Update message not sent.");
                }
            } else {
                this.getLogger().warning("IP update message is missing in the config. Update message not sent.");
            }
        }
        
        if (dynu) {
            this.dynuClientId = this.getConfig().getString("DYNU_SETTINGS.CLIENT_ID");
            this.dynuSecret = this.getConfig().getString("DYNU_SETTINGS.SECRET");
            this.domain = this.getConfig().getString("DYNU_SETTINGS.DOMAIN");
            
            this.getLogger().info("Dynu settings: " + dynuClientId + " " + dynuSecret + " " + domain);
            
            update(publicIp);
        }

        this.getLogger().info("Listening server on port " + ngrokPort + ", IP: " + publicIp);
    }

    @Override
    public void onDisable() {
    	System.setErr(null);
        try {
            if (ngrokClient != null && publicIp != null) {
                this.ngrokClient.disconnect(publicIp);
                this.ngrokClient.kill();
            }
            if (discordModuleStatus) {
                this.client.shutdown();
            }
            this.saveConfig();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onEvent(@NotNull GenericEvent genericEvent) {
        if (genericEvent instanceof ReadyEvent) {
            this.discordModuleStatus = true;
        }
    }
    
	public boolean update(String publicIp) {
    	String hostname = domain;
    	String password = this.getConfig().getString("DYNU_SETTINGS.PASSWORD");
        try {
            // Parse the public IP address
            String[] parts = publicIp.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            // Convert the hostname to IP address
            InetAddress ipAddress = InetAddress.getByName(host);
            String ip = ipAddress.getHostAddress();
            
            URL url = new URL("https://api.dynu.com/nic/update?hostname=" + hostname + "&myip=" + ip + "&password=" + password);
            URLConnection conn = url.openConnection();
            conn.connect();
            
            // Combine Client ID and Secret
            String dynuFull = dynuClientId + ":" + dynuSecret;

            // Retrieve Dynu Token
            dynuToken = getToken(dynuFull);

            // Get Domain Name + ID
            getDomainInfo();

            // Get DNS Records of Domain ID
            getDNSRecords();
            
            // Update DNS Record
            updateDNS(port);

        } catch (Exception e) {
            this.getLogger().severe(e.getMessage());
        }
        return false;
    }
	
    private String getToken(String dynuFull) {
        try {
        	this.getLogger().info("Getting token...");
            URL url = new URL("https://api.dynu.com/v2/oauth2/token");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString(dynuFull.getBytes(StandardCharsets.UTF_8)));

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String response = reader.readLine();
            reader.close();

            return response.split("\"")[3];
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void getDomainInfo() {
        try {
        	this.getLogger().info("Getting Domain Info...");
            URL url = new URL("https://api.dynu.com/v2/dns");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + dynuToken);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String response = reader.readLine();
            reader.close();

            String[] parts = response.split("\"");
            domainId = parts[6].replace(":", "").replace(",", ""); // Removing ":" and ","
            domain = parts[9];
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getDNSRecords() {
        try {
        	this.getLogger().info("Getting DNS Records... " + dynuToken + " " + domainId);
            URL url = new URL("https://api.dynu.com/v2/dns/" + domainId + "/record");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + dynuToken);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String response = reader.readLine();
            reader.close();

            String[] records = response.split("\"");
            for (int i = 0; i < records.length; i++) {
                if (records[i].equals("recordType") && records[i + 2].equals("SRV")) {
                    dnsesPort = records[i - 15].replaceAll("[^0-9]", "");
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateDNS(int port) {
        try {
        	this.getLogger().info("Updating DNS... " + dnsesPort);
            URL url = new URL("https://api.dynu.com/v2/dns/" + domainId + "/record/" + dnsesPort);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + dynuToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String data = "{\"nodeName\":\"_minecraft._tcp\",\"recordType\":\"SRV\",\"ttl\":120,\"state\":true,\"group\":\"\",\"host\":\"" + domain + "\",\"priority\":10,\"weight\":5,\"port\":" + port + "}";
            conn.getOutputStream().write(data.getBytes());

            conn.getInputStream();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
