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
        int ngrokPort = this.getConfig().getInt("NGROK_PORT", 25565); // Default to Minecraft port if not specified

        this.client = DiscordClientBuilder.create(Objects.requireNonNull(this.getConfig().getString("BOT_TOKEN")))
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

        client.getChannelById(Snowflake.of(Objects.requireNonNull(this.getConfig().getString("IP_UPDATE_CHANNEL_ID"))))
                .ofType(GuildMessageChannel.class)
                .flatMap(guildMessageChannel -> guildMessageChannel.createMessage(MessageCreateSpec.builder()
                        .content(Objects.requireNonNull(this.getConfig().getString("IP_UPDATE_MESSAGE"))
                                .replace("%server_ip%", publicIp))
                        .build()
                )).subscribe();

        this.getLogger().info("Listening server on port " + ngrokPort + ", IP: " + publicIp);
    }

    @Override
    public void onDisable() {
        try {
            if (ngrokClient != null && publicIp != null) {
                this.ngrokClient.disconnect(publicIp);
                this.ngrokClient.kill();
                // this.ngrokClient.closeResources(); // Close any resources
            }
        } catch (Exception ignored) {
            // Suppress the exception
        }

        try {
            if (this.client != null) {
                this.client.logout().block();
            }
        } catch (Exception ignored) {
            // Suppress the exception
        }
    }
}
