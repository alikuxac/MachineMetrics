package com.alikuxac.machinemetrics.forge.platform;

import com.alikuxac.machinemetrics.platform.network.NetworkPlatform;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

public final class ForgeNetworkPlatform {

    private static SimpleChannel channel;

    public static void install() {
        channel = ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath("machinemetrics", "main"))
                .networkProtocolVersion(1)
                .acceptedVersions((status, version) -> true)
                .simpleChannel();

        NetworkPlatform.setClientSender(payload ->
                channel.send(payload, PacketDistributor.SERVER.noArg()));

        NetworkPlatform.RegistrationHook hook = NetworkPlatform.registrationHook();
        if (hook != null) {
            hook.register(new NetworkPlatform.RegisterEvent() {
                @Override
                public NetworkPlatform.Registrar registrar(String version) {
                    return new NetworkPlatform.Registrar() {
                        @Override
                        public <T extends CustomPacketPayload> void playToServer(Class<T> type, CustomPacketPayload.Type<T> packetType,
                                                                                 StreamCodec<? super FriendlyByteBuf, T> codec,
                                                                                 NetworkPlatform.Handler<T> handler) {
                            register(type, codec, handler, PacketFlow.SERVERBOUND);
                        }

                        @Override
                        public <T extends CustomPacketPayload> void playToClient(Class<T> type, CustomPacketPayload.Type<T> packetType,
                                                                                 StreamCodec<? super FriendlyByteBuf, T> codec,
                                                                                 NetworkPlatform.Handler<T> handler) {
                            register(type, codec, handler, PacketFlow.CLIENTBOUND);
                        }
                    };
                }
            });
        }
        channel.build();
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void register(Class<T> type, StreamCodec<? super FriendlyByteBuf, T> codec,
                                                                 NetworkPlatform.Handler<T> handler, PacketFlow flow) {
        channel.messageBuilder(type)
                .codec((StreamCodec<FriendlyByteBuf, T>) codec)
                .direction(flow)
                .consumerMainThread((payload, ctx) -> handler.handle(payload, wrap(ctx)))
                .add();
    }

    private static NetworkPlatform.Context wrap(CustomPayloadEvent.Context ctx) {
        return new NetworkPlatform.Context() {
            @Override
            public ServerPlayer player() {
                return ctx.getSender();
            }

            @Override
            public void enqueueWork(Runnable task) {
                ctx.enqueueWork(task);
            }

            @Override
            public void reply(CustomPacketPayload payload) {
                ServerPlayer sender = ctx.getSender();
                if (sender != null) {
                    channel.send(payload, PacketDistributor.PLAYER.with(sender));
                }
            }
        };
    }
}