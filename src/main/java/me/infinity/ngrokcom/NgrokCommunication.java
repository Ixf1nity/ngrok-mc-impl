package me.infinity.ngrokcom;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.conf.JavaNgrokConfig;
import com.github.alexdlaird.ngrok.protocol.CreateTunnel;
import com.github.alexdlaird.ngrok.protocol.Proto;
import com.github.alexdlaird.ngrok.protocol.Region;
import com.github.alexdlaird.ngrok.protocol.Tunnel;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.spec.MessageCreateSpec;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class NgrokCommunication extends JavaPlugin {

    private GatewayDiscordClient client;
    private NgrokClient ngrokClient;
    private String publicIp;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        int ngrokPort = this.getServer().getPort()

        String botToken = this.getConfig().getString("BOT_TOKEN");
        if (botToken == null || botToken.isEmpty()) {
            this.getLogger().warning("Bot token is missing in the config. Discord functionality disabled.");
            return;
        }

        this.client = DiscordClientBuilder.create(botToken)
                .build()
                .login()
                .block();

        final JavaNgrokConfig javaNgrokConfig = new JavaNgrokConfig.Builder()
                .withAuthToken(this.getConfig().getString("NGROK_AUTH_TOKEN"))
                .withRegion(Region.valueOf(Objects.requireNonNull(this.getConfig().getString("REGION")).toUpperCase()))
                .build();

        this.ngrokClient = new NgrokClient.Builder()
                .withJavaNgrokConfig(javaNgrokConfig)
                .build();

        final CreateTunnel createTunnel = new CreateTunnel.Builder()
                .withProto(Proto.TCP)
                .withAddr(ngrokPort) // Use the configured Ngrok port
                .build();

        final Tunnel tunnel = ngrokClient.connect(createTunnel);
        this.publicIp = tunnel.getPublicUrl().replace("tcp://", "");

        if (this.getConfig().getBoolean("SEND_UPDATE_MESSAGE")) {
            String updateMessage = this.getConfig().getString("IP_UPDATE_MESSAGE");
            if (updateMessage != null && !updateMessage.isEmpty()) {
                client.getChannelById(Snowflake.of(Objects.requireNonNull(this.getConfig().getString("IP_UPDATE_CHANNEL_ID"))))
                        .ofType(GuildMessageChannel.class)
                        .flatMap(guildMessageChannel -> guildMessageChannel.createMessage(MessageCreateSpec.builder()
                                .content(updateMessage.replace("%server_ip%", publicIp))
                                .build()
                        )).subscribe();
            } else {
                this.getLogger().warning("IP update message is missing in the config. Update message not sent.");
            }
        }

        this.getLogger().info("Listening server on port " + ngrokPort + ", IP: " + publicIp);
    }

    @Override
    public void onDisable() {
    	saveConfigSilently();
        try {
            if (ngrokClient != null && publicIp != null) {
                this.ngrokClient.disconnect(publicIp);
                this.ngrokClient.kill();
                saveConfigSilently();
            }
        } catch (Exception ignored) {
            // Suppress any exceptions during shutdown
        }

        try {
            if (this.client != null) {
                this.client.logout().block();
                saveConfigSilently();
            }
        } catch (Exception ignored) {
            // Suppress any exceptions during shutdown
        }
    }
    // Method to save the configuration without generating warnings
    private void saveConfigSilently() {
        try {
            // Temporarily store the original system output and error streams
            // to suppress warnings during configuration save
            System.setOut(null);
            System.setErr(null);

            // Save the default configuration
            saveDefaultConfig();

            // Restore the original output and error streams
            System.setOut(System.out);
            System.setErr(System.err);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
