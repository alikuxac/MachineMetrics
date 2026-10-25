package com.alikuxac.machinemetrics.neoforge.platform;

import com.alikuxac.machinemetrics.platform.network.NetworkPlatform;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NeoForgeNetworkPlatform {

    public static void install() {
        NetworkPlatform.setClientSender(PacketDistributor::sendToServer);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        NetworkPlatform.RegistrationHook hook = NetworkPlatform.registrationHook();
        if (hook == null) {
            return;
        }
        hook.register(new NetworkPlatform.RegisterEvent() {
            @Override
            public NetworkPlatform.Registrar registrar(String version) {
                var registrar = event.registrar(version).optional();
                return new NetworkPlatform.Registrar() {
                    @Override
                    public <T extends CustomPacketPayload> void playToServer(Class<T> type, CustomPacketPayload.Type<T> packetType,
                                                                             StreamCodec<? super FriendlyByteBuf, T> codec,
                                                                             NetworkPlatform.Handler<T> handler) {
                        registrar.playToServer(packetType, codec,
                                (payload, ctx) -> handler.handle(payload, wrap(ctx)));
                    }

                    @Override
                    public <T extends CustomPacketPayload> void playToClient(Class<T> type, CustomPacketPayload.Type<T> packetType,
                                                                             StreamCodec<? super FriendlyByteBuf, T> codec,
                                                                             NetworkPlatform.Handler<T> handler) {
                        registrar.playToClient(packetType, codec,
                                (payload, ctx) -> handler.handle(payload, wrap(ctx)));
                    }
                };
            }
        });
    }

    private static NetworkPlatform.Context wrap(IPayloadContext ctx) {
        return new NetworkPlatform.Context() {
            @Override
            public ServerPlayer player() {
                return ctx.player() instanceof ServerPlayer sp ? sp : null;
            }

            @Override
            public void enqueueWork(Runnable task) {
                ctx.enqueueWork(task);
            }

            @Override
            public void reply(CustomPacketPayload payload) {
                ctx.reply(payload);
            }
        };
    }
}