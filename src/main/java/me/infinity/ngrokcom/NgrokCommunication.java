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

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.URL;
import java.net.URLConnection;

public final class NgrokCommunication extends JavaPlugin implements EventListener {

    private JDA client;
    private NgrokClient ngrokClient;
    private String publicIp;
    

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
        update(publicIp);
        }

        this.getLogger().info("Listening server on port " + ngrokPort + ", IP: " + publicIp);
    }

    @Override
    public void onDisable() {
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
    	String hostname = this.getConfig().getString("DYNU_SETTINGS.HOSTNAME");
    	String password = this.getConfig().getString("DYNU_SETTINGS.PASSWORD");
        try {
            // Parse the public IP address
            String[] parts = publicIp.split(":");
            String host = parts[0];

            // Convert the hostname to IP address
            InetAddress ipAddress = InetAddress.getByName(host);
            String ip = ipAddress.getHostAddress();
            
            URL url = new URL("https://api.dynu.com/nic/update?hostname=" + hostname + "&myip=" + ip + "&password=" + password);
            URLConnection conn = url.openConnection();
            conn.connect();

        } catch (Exception e) {
            this.getLogger().severe(e.getMessage());
        }
        return false;
    }
}
