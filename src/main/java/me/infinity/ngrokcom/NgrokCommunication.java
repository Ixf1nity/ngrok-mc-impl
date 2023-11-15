package me.infinity.ngrokcom;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.conf.JavaNgrokConfig;
import com.github.alexdlaird.ngrok.installer.NgrokInstaller;
import com.github.alexdlaird.ngrok.installer.NgrokVersion;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NgrokCommunication extends JavaPlugin {

    private GatewayDiscordClient client;
    private NgrokClient ngrokClient;
    private String publicIp;

    private boolean discordModule;
    private boolean discordModuleStatus = false;

    @Override
    public void onEnable() {

        Logger.getLogger(String.valueOf(com.github.alexdlaird.ngrok.process.NgrokProcess.class)).setLevel(Level.OFF);

        this.saveDefaultConfig();
        int ngrokPort = this.getServer().getPort();
        this.discordModule = this.getConfig().getBoolean("DISCORD_UPDATES.ENABLED");

        if (discordModule) {
            String botToken = this.getConfig().getString("DISCORD_UPDATES.BOT_TOKEN");
            if (botToken == null || botToken.isEmpty()) {
                this.getLogger().warning("Bot token is missing in the config. Shutting down...");
                this.setEnabled(false);
                return;
            }

            this.client = DiscordClientBuilder.create(botToken)
                .build()
                .login()
                .block();

            if (this.client != null) this.discordModuleStatus = true;
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
                client.getChannelById(Snowflake.of(Objects.requireNonNull(this.getConfig().getString("DISCORD_UPDATES.UPDATE_CHANNEL_ID"))))
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
        try {
            if (ngrokClient != null && publicIp != null) {
                this.ngrokClient.disconnect(publicIp);
                this.ngrokClient.kill();
            }
            if (discordModuleStatus) {
                if (this.client != null) {
                    this.client.logout().block();
                }
            }
        } catch (Exception ignored) {}
    }
}
