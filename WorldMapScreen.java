import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

public class WorldMapScreen extends Screen {
    private ModNetworking.CountrySyncData selectedCountry = null;
    private ButtonWidget allyBtn, enemyBtn, warBtn, callAllyBtn;

    public WorldMapScreen() {
        super(Text.literal("Dünya Siyasi Haritası"));
    }

    @Override
    protected void init() {
        ClientPlayNetworking.send(new ModNetworking.RequestSyncPayload());
        int panelX = this.width - 150;
        int panelY = 60;

        allyBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal("Müttefik (+100 Altın)"), b -> sendDiplo("ALLY"))
            .dimensions(panelX, panelY + 50, 135, 20).build());
        enemyBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal("Düşman"), b -> sendDiplo("ENEMY"))
            .dimensions(panelX, panelY + 75, 135, 20).build());
        warBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal("Savaş İlan Et"), b -> sendDiplo("WAR"))
            .dimensions(panelX, panelY + 100, 135, 20).build());
        callAllyBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal("Müttefiki Savaşa Çağır"), b -> sendDiplo("CALL_ALLY"))
            .dimensions(panelX, panelY + 125, 135, 20).build());

        updateButtons();
    }

    private void sendDiplo(String action) {
        if (selectedCountry != null) {
            ClientPlayNetworking.send(new ModNetworking.DiplomacyActionPayload(selectedCountry.id(), action));
        }
    }

    private void updateButtons() {
        boolean visible = selectedCountry != null && !selectedCountry.id().equals(ClientCache.playerCountryId);
        allyBtn.visible = visible;
        enemyBtn.visible = visible;
        warBtn.visible = visible;
        callAllyBtn.visible = visible && ClientCache.inWar && "ALLY".equals(selectedCountry.relationToPlayer());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int centerX = this.width / 2 - 40;
        int centerY = this.height / 2;
        int cellSize = 6;

        ChunkPos playerChunk = (client != null && client.player != null) ? client.player.getChunkPos() : new ChunkPos(0, 0);

        for (ModNetworking.CountrySyncData c : ClientCache.countries) {
            boolean isMine = c.id().equals(ClientCache.playerCountryId);
            int fillColor = isMine ? 0xAA00FF00 : ((c.color() & 0x00FFFFFF) | 0x88000000);

            for (long l : c.chunks()) {
                ChunkPos cp = new ChunkPos(l);
                int drawX = centerX + (cp.x - playerChunk.x) * cellSize;
                int drawY = centerY + (cp.z - playerChunk.z) * cellSize;

                context.fill(drawX, drawY, drawX + cellSize, drawY + cellSize, fillColor);
                if (isMine) {
                    context.drawBorder(drawX, drawY, cellSize, cellSize, 0xFFFFFFFF);
                }
            }
        }

        context.fill(centerX - 2, centerY - 2, centerX + 2, centerY + 2, 0xFFFF0000);
        context.drawCenteredTextWithShadow(this.textRenderer, "Siyasi Harita (Ülke sınırına tıkla)", centerX, 15, 0xFFFFFF);

        if (selectedCountry != null) {
            int px = this.width - 155;
            context.fill(px - 5, 30, this.width - 5, 220, 0xCC111111);
            context.drawItem(selectedCountry.banner(), px + 55, 38);
            context.drawTextWithShadow(this.textRenderer, selectedCountry.name(), px, 60, 0xFFFF55);
            context.drawTextWithShadow(this.textRenderer, "Lider: " + selectedCountry.leader(), px, 72, 0xCCCCCC);
            context.drawTextWithShadow(this.textRenderer, "Durum: " + selectedCountry.relationToPlayer(), px, 84, 0x55FFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int centerX = this.width / 2 - 40;
        int centerY = this.height / 2;
        int cellSize = 6;
        ChunkPos playerChunk = (client != null && client.player != null) ? client.player.getChunkPos() : new ChunkPos(0, 0);

        int clickedChunkX = playerChunk.x + (int) Math.floor((mouseX - centerX) / cellSize);
        int clickedChunkZ = playerChunk.z + (int) Math.floor((mouseY - centerY) / cellSize);
        long targetLong = ChunkPos.toLong(clickedChunkX, clickedChunkZ);

        for (ModNetworking.CountrySyncData c : ClientCache.countries) {
            if (c.chunks().contains(targetLong)) {
                selectedCountry = c;
                updateButtons();
                return true;
            }
        }
        return false;
    }
}
