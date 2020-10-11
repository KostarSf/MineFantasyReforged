package minefantasy.mfr.api.knowledge.client;

import minefantasy.mfr.MineFantasyReborn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public class EntryPageRecipeBloom extends EntryPage {
    private Minecraft mc = Minecraft.getMinecraft();
    private ItemStack input, output;

    public EntryPageRecipeBloom(ItemStack in, ItemStack out) {
        this.input = in;
        this.output = out;
    }

    public EntryPageRecipeBloom(Item in, Item out) {
        this(new ItemStack(in), new ItemStack(out));
    }

    @Override
    public void render(GuiScreen parent, int x, int y, float f, int posX, int posY, boolean onTick) {
        this.mc.getTextureManager().bindTexture(new ResourceLocation(MineFantasyReborn.MOD_ID, "textures/gui/knowledge/bloomery_grid.png"));
        parent.drawTexturedModalRect(posX, posY, 0, 0, this.universalBookImageWidth, this.universalBookImageHeight);

        String cft = "<" + I18n.format("method.bloomery") + ">";
        mc.fontRenderer.drawSplitString(cft,
                posX + (universalBookImageWidth / 2) - (mc.fontRenderer.getStringWidth(cft) / 2), posY + 150, 117, 0);

        renderRecipe(parent, x, y, f, posX, posY);
    }

    private void renderRecipe(GuiScreen parent, int mx, int my, float f, int posX, int posY) {
        renderResult(parent, output, false, posX, posY + 74, mx, my);
        renderResult(parent, input, false, posX, posY, mx, my);
    }

    public void renderResult(GuiScreen gui, ItemStack stack, boolean accountForContainer, int xOrigin, int yOrigin, int mx, int my) {
        if (stack.isEmpty())
            return;
        stack = stack.copy();

        if (stack.getItemDamage() == Short.MAX_VALUE)
            stack.setItemDamage(0);

        int xPos = xOrigin + 80;
        int yPos = yOrigin + 41;
        ItemStack stack1 = stack.copy();
        if (stack1.getItemDamage() == -1)
            stack1.setItemDamage(0);

        renderItem(gui, xPos, yPos, stack1, accountForContainer, mx, my);
    }


    @Override
    public void preRender(GuiScreen parent, int x, int y, float f, int posX, int posY, boolean onTick) {
    }
}
