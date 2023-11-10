package me.infinity.ngrokcom;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.protocol.CreateTunnel;
import com.github.alexdlaird.ngrok.protocol.Proto;
import com.github.alexdlaird.ngrok.protocol.Tunnel;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.spec.MessageCreateSpec;
import org.bukkit.plugin.java.JavaPlugin;

public final class NgrokCommunication extends JavaPlugin {

    private GatewayDiscordClient client;

    @Override
    public void onEnable() {
        this.client = DiscordClientBuilder.create("TOKEN")
                .build()
                .login()
                .block();

        final NgrokClient ngrokClient = new NgrokClient.Builder().build();
        final CreateTunnel sshCreateTunnel = new CreateTunnel.Builder()
                .withProto(Proto.TCP)
                .withAddr(25565)
                .build();
        final Tunnel sshTunnel = ngrokClient.connect(sshCreateTunnel);

        client.getChannelById(Snowflake.of(1172534695813189662L))
                .ofType(GuildMessageChannel.class)
                .flatMap(guildMessageChannel -> guildMessageChannel.createMessage(MessageCreateSpec.builder()
                        .content("* Current connection address: " + sshTunnel.getPublicUrl())
                        .build()
                )).subscribe();
    }

    @Override
    public void onDisable() {
        this.client.logout();
    }
}
