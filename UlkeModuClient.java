import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class UlkeModuClient implements ClientModInitializer {

    private static KeyBinding countryMenuKey;
    private static KeyBinding mapMenuKey;

    @Override
    public void onInitializeClient() {
        countryMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Ülke Paneli (N)",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "Ülke Modu"
        ));

        mapMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Ülke Haritası (Ö)",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_SEMICOLON,
            "Ülke Modu"
        ));

        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncMapPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientCache.countries = payload.countries();
                ClientCache.playerCountryId = payload.playerCountryId();
                ClientCache.warDeaths = payload.warDeaths();
                ClientCache.maxWarDeaths = payload.maxWarDeaths();
                ClientCache.inWar = payload.inWar();
                ClientCache.prepSecondsLeft = payload.prepSecondsLeft();
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (countryMenuKey.wasPressed()) {
                ClientPlayNetworking.send(new ModNetworking.RequestSyncPayload());
                client.setScreen(new CountryPanelScreen());
            }
            while (mapMenuKey.wasPressed()) {
                ClientPlayNetworking.send(new ModNetworking.RequestSyncPayload());
                client.setScreen(new WorldMapScreen());
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && ClientCache.inWar) {
                String text = ClientCache.prepSecondsLeft > 0
                    ? "Savaş Hazırlık Süresi: " + ClientCache.prepSecondsLeft + " sn"
                    : "Ölüm: " + ClientCache.warDeaths + "/" + ClientCache.maxWarDeaths;
                drawContext.drawTextWithShadow(client.textRenderer, Text.literal(text), 10, 10, 0xFFFF5555);
            }
        });
    }
}
