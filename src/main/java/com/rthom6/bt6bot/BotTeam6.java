package com.rthom6.bt6bot;

import com.rthom6.bt6bot.Commands.Command;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import com.sedmelluq.discord.lavaplayer.player.*;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;

import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.voice.AudioProvider;
import discord4j.voice.VoiceConnection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public class BotTeam6 {
    private static final Map<String, Command> commands = new HashMap<>();


    static {
        commands.put("ping", event -> event.getMessage()
                .getChannel()
                .flatMap(channel -> channel.createMessage("Pong!"))
                .then());
    }

    public static void main(String[] args) {
        final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);

        AudioSourceManagers.registerRemoteSources(playerManager);

        final AudioPlayer player = playerManager.createPlayer();

        AudioProvider provider = new LavaPlayerAudioProvider(player);

        commands.put("join", event -> Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .then());

        final TrackScheduler scheduler = new TrackScheduler(player);
        commands.put("play", event -> Mono.justOrEmpty(event.getMessage().getContent())
                .map(content -> Arrays.asList(content.split(" ")))
                .doOnNext(command -> playerManager.loadItem(command.get(1), scheduler))
                .then());

        final GatewayDiscordClient client = DiscordClientBuilder.create(args[0]).build().login().block();
        commands.put("disconnect", event ->
                Mono.justOrEmpty(event.getMember())
                        .flatMap(Member::getVoiceState)
                        .flatMap(vs -> client.getVoiceConnectionRegistry()
                                .getVoiceConnection(vs.getGuildId())
                                .doOnSuccess(vc -> {
                                    if (vc == null) {
//                                        log.info("No voice connection to leave!");
                                    }
                                }))
                        .flatMap(VoiceConnection::disconnect));


        client.getEventDispatcher().on(MessageCreateEvent.class)
            .flatMap(event -> Mono.just(event.getMessage().getContent())
                .flatMap(content -> Flux.fromIterable(commands.entrySet())
                    .filter(entry -> content.startsWith('!' + entry.getKey()))
                    .flatMap(entry -> entry.getValue().execute(event))
                    .next()))
            .subscribe();

        client.onDisconnect().block();
    }
}
