package com.alikuxac.machinemetrics.platform.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class NetworkPlatform {

    public interface Context {
        ServerPlayer player();

        void enqueueWork(Runnable task);

        void reply(CustomPacketPayload payload);
    }

    public interface Registrar {
        <T extends CustomPacketPayload> void playToServer(Class<T> type, CustomPacketPayload.Type<T> packetType,
                                                          StreamCodec<? super FriendlyByteBuf, T> codec,
                                                          Handler<T> handler);

        <T extends CustomPacketPayload> void playToClient(Class<T> type, CustomPacketPayload.Type<T> packetType,
                                                          StreamCodec<? super FriendlyByteBuf, T> codec,
                                                          Handler<T> handler);
    }

    @FunctionalInterface
    public interface Handler<T extends CustomPacketPayload> {
        void handle(T payload, Context context);
    }

    public interface RegisterEvent {
        Registrar registrar(String version);
    }

    public interface Sender {
        void sendToServer(CustomPacketPayload payload);
    }

    public interface RegistrationHook {
        void register(RegisterEvent event);
    }

    private static RegistrationHook registrationHook;
    private static Sender clientSender = payload -> {};

    private NetworkPlatform() {}

    public static void setRegistrationHook(RegistrationHook hook) {
        registrationHook = hook;
    }

    public static void setClientSender(Sender sender) {
        clientSender = sender;
    }

    public static RegistrationHook registrationHook() {
        return registrationHook;
    }

    public static void sendToServer(CustomPacketPayload payload) {
        clientSender.sendToServer(payload);
    }
}