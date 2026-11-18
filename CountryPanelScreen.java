import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.BannerItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class CountryPanelScreen extends Screen {
    private TextFieldWidget countryNameField;
    private TextFieldWidget leaderNameField;
    private ItemStack selectedBanner = new ItemStack(Items.WHITE_BANNER);

    public CountryPanelScreen() {
        super(Text.literal("Ülke Yönetim Paneli"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (client != null && client.player != null) {
            ItemStack mainHand = client.player.getMainHandStack();
            if (mainHand.getItem() instanceof BannerItem) {
                selectedBanner = mainHand.copy();
            }
        }

        boolean hasCountry = ClientCache.hasCountry();

        if (!hasCountry) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Flama Koy"), btn -> {
                if (client != null && client.player != null && client.player.getMainHandStack().getItem() instanceof BannerItem) {
                    selectedBanner = client.player.getMainHandStack().copy();
                }
            }).dimensions(centerX - 135, centerY - 25, 65, 20).build());

            countryNameField = new TextFieldWidget(this.textRenderer, centerX - 60, centerY - 45, 150, 20, Text.literal("Ülke Adı"));
            countryNameField.setPlaceholder(Text.literal("Ülke Adı..."));
            this.addSelectableChild(countryNameField);

            leaderNameField = new TextFieldWidget(this.textRenderer, centerX - 60, centerY - 15, 150, 20, Text.literal("Lider Adı"));
            leaderNameField.setPlaceholder(Text.literal("Lider Adı..."));
            this.addSelectableChild(leaderNameField);

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Ülke Kur (25 Altın)"), btn -> {
                ClientPlayNetworking.send(new ModNetworking.CreateCountryPayload(
                    countryNameField.getText(),
                    leaderNameField.getText(),
                    selectedBanner
                ));
                this.close();
            }).dimensions(centerX - 60, centerY + 20, 150, 20).build());
        } else {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Claim (1 Altın)"), btn -> {
                ClientPlayNetworking.send(new ModNetworking.ClaimActionPayload(true));
                this.close();
            }).dimensions(centerX - 75, centerY - 20, 150, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Unclaim (Toprak Bırak)"), btn -> {
                ClientPlayNetworking.send(new ModNetworking.ClaimActionPayload(false));
                this.close();
            }).dimensions(centerX - 75, centerY + 10, 150, 20).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, centerY - 75, 0xFFFFFF);

        if (!ClientCache.hasCountry()) {
            context.fill(centerX - 115, centerY - 52, centerX - 91, centerY - 28, 0xFF333333);
            context.drawItem(selectedBanner, centerX - 111, centerY - 48);
            countryNameField.render(context, mouseX, mouseY, delta);
            leaderNameField.render(context, mouseX, mouseY, delta);
        } else {
            var my = ClientCache.getMyCountry();
            if (my != null) {
                context.drawItem(my.banner(), centerX - 8, centerY - 55);
                context.drawCenteredTextWithShadow(this.textRenderer, "Ülke: " + my.name() + " | Lider: " + my.leader(), centerX, centerY - 35, 0x55FF55);
            }
        }
    }
}
