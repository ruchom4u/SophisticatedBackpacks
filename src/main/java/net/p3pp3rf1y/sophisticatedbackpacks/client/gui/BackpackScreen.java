package net.p3pp3rf1y.sophisticatedbackpacks.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Rectangle2d;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.p3pp3rf1y.sophisticatedbackpacks.Config;
import net.p3pp3rf1y.sophisticatedbackpacks.client.gui.controls.Button;
import net.p3pp3rf1y.sophisticatedbackpacks.client.gui.controls.ButtonDefinitions;
import net.p3pp3rf1y.sophisticatedbackpacks.client.gui.controls.ToggleButton;
import net.p3pp3rf1y.sophisticatedbackpacks.client.gui.utils.GuiHelper;
import net.p3pp3rf1y.sophisticatedbackpacks.client.gui.utils.Position;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackInventorySlot;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.SortBy;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackOpenMessage;
import net.p3pp3rf1y.sophisticatedbackpacks.network.PacketHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.network.TransferFullSlotMessage;
import net.p3pp3rf1y.sophisticatedbackpacks.network.WindowClickMessage;
import net.p3pp3rf1y.sophisticatedbackpacks.upgrades.crafting.ICraftingUIPart;
import net.p3pp3rf1y.sophisticatedbackpacks.util.CountAbbreviator;

import javax.annotation.Nullable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static net.p3pp3rf1y.sophisticatedbackpacks.client.gui.utils.GuiHelper.GUI_CONTROLS;

public class BackpackScreen extends ContainerScreen<BackpackContainer> {
	private static final int DISABLED_SLOT_COLOR = -1072689136;
	private static final int UPGRADE_TOP_HEIGHT = 7;
	private static final int UPGRADE_SLOT_HEIGHT = 18;
	private static final int UPGRADE_SPACE_BETWEEN_SLOTS = 4;
	private static final int UPGRADE_BOTTOM_HEIGHT = 7;
	private static final int TOTAL_UPGRADE_GUI_HEIGHT = 252;
	public static final int UPGRADE_INVENTORY_OFFSET = 26;
	static final int DISABLED_SLOT_X_POS = -1000;
	static final int SLOTS_Y_OFFSET = 17;
	static final int SLOTS_X_OFFSET = 7;

	public static BackpackScreen constructScreen(BackpackContainer screenContainer, PlayerInventory inv, ITextComponent title) {
		return new BackpackScreen(screenContainer, inv, title);
	}

	private UpgradeSettingsTabControl settingsTabControl;
	private final int numberOfUpgradeSlots;
	@Nullable
	private Button sortButton = null;
	@Nullable
	private ToggleButton<SortBy> sortByButton = null;
	private final Set<ToggleButton<Boolean>> upgradeSwitches = new HashSet<>();

	private final Map<Integer, UpgradeInventoryPartBase<?>> inventoryParts = new LinkedHashMap<>();

	private static ICraftingUIPart craftingUIPart = ICraftingUIPart.NOOP;

	public static void setCraftingUIPart(ICraftingUIPart part) {
		craftingUIPart = part;
	}

	public BackpackScreen(BackpackContainer screenContainer, PlayerInventory inv, ITextComponent titleIn) {
		super(screenContainer, inv, titleIn);
		ySize = 114 + getContainer().getNumberOfRows() * 18;
		xSize = getContainer().getBackpackBackgroundProperties().getSlotsOnLine() * 18 + 14;
		playerInventoryTitleY = ySize - 94;
		playerInventoryTitleX = 8 + getContainer().getBackpackBackgroundProperties().getPlayerInventoryXOffset();
		numberOfUpgradeSlots = getContainer().getNumberOfUpgradeSlots();
		passEvents = true;
	}

	public ICraftingUIPart getCraftingUIAddition() {
		return craftingUIPart;
	}

	@Override
	protected void init() {
		super.init();
		craftingUIPart.setBackpackScreen(this);
		initUpgradeSettingsControl();
		initUpgradeInventoryParts();
		addUpgradeSwitches();
		getContainer().setUpgradeChangeListener(c -> {
			children.remove(settingsTabControl);
			craftingUIPart.onCraftingSlotsHidden();
			initUpgradeSettingsControl();
			initUpgradeInventoryParts();
			addUpgradeSwitches();
		});
		addSortButtons();
	}

	private void initUpgradeInventoryParts() {
		inventoryParts.clear();
		if (getContainer().getColumnsTaken() == 0) {
			return;
		}

		AtomicReference<Position> pos = new AtomicReference<>(new Position(SLOTS_X_OFFSET + container.getSlotsOnLine() * 18, SLOTS_Y_OFFSET));
		int height = container.getNumberOfRows() * 18;
		for (Map.Entry<Integer, UpgradeContainerBase<?, ?>> entry : getContainer().getUpgradeContainers().entrySet()) {
			UpgradeContainerBase<?, ?> container = entry.getValue();
			UpgradeGuiManager.getInventoryPart(entry.getKey(), container, pos.get(), height, this).ifPresent(part -> {
				inventoryParts.put(entry.getKey(), part);
				pos.set(new Position(pos.get().getX() + 36, pos.get().getY()));
			});
		}
	}

	private void addUpgradeSwitches() {
		upgradeSwitches.clear();
		int switchTop = guiTop + getUpgradeTop() + 10;
		for (int slot = 0; slot < numberOfUpgradeSlots; slot++) {
			if (container.canDisableUpgrade(slot)) {
				int finalSlot = slot;
				ToggleButton<Boolean> upgradeSwitch = new ToggleButton<>(new Position(guiLeft - 22, switchTop), ButtonDefinitions.UPGRADE_SWITCH,
						button -> getContainer().setUpgradeEnabled(finalSlot, !getContainer().getUpgradeEnabled(finalSlot)), () -> getContainer().getUpgradeEnabled(finalSlot));
				addListener(upgradeSwitch);
				upgradeSwitches.add(upgradeSwitch);
			}
			switchTop += 22;
		}
	}

	private void addSortButtons() {
		SortButtonsPosition sortButtonsPosition = Config.CLIENT.sortButtonsPosition.get();
		if (sortButtonsPosition == SortButtonsPosition.HIDDEN) {
			return;
		}

		Position pos = getSortButtonsPosition(sortButtonsPosition);

		sortButton = new Button(new Position(pos.getX(), pos.getY()), ButtonDefinitions.SORT, button -> {
			if (button == 0) {
				getContainer().sort();
				Minecraft.getInstance().player.sendStatusMessage(new StringTextComponent("Sorted"), true);
			}
		});
		addListener(sortButton);
		sortByButton = new ToggleButton<>(new Position(pos.getX() + 14, pos.getY()), ButtonDefinitions.SORT_BY, button -> {
			if (button == 0) {
				getContainer().setSortBy(getContainer().getSortBy().next());
			}
		}, () -> getContainer().getSortBy());
		addListener(sortByButton);

	}

	@Override
	public void onClose() {
		super.onClose();
		if (!getContainer().isFirstLevelBackpack()) {
			PacketHandler.sendToServer(new BackpackOpenMessage());
		}
	}

	private Position getSortButtonsPosition(SortButtonsPosition sortButtonsPosition) {
		switch (sortButtonsPosition) {
			case ABOVE_UPGRADES:
				return new Position(guiLeft - UPGRADE_INVENTORY_OFFSET - 2, guiTop + getUpgradeTop() - 14);
			case BELOW_UPGRADES:
				return new Position(guiLeft - UPGRADE_INVENTORY_OFFSET - 2, guiTop + getUpgradeTop() + getUpgradeHeightWithoutBottom() + UPGRADE_BOTTOM_HEIGHT + 2);
			case BELOW_UPGRADE_TABS:
				return settingsTabControl == null ? new Position(0, 0) : new Position(settingsTabControl.getX() + 2, settingsTabControl.getY() + Math.max(0, settingsTabControl.getHeight() + 2));
			case TITLE_LINE_RIGHT:
			default:
				return new Position(guiLeft + xSize - 34, guiTop + 4);
		}
	}

	public Optional<Rectangle2d> getSortButtonsRectangle() {
		return sortButton == null || sortByButton == null ? Optional.empty() : Optional.of(new Rectangle2d(sortButton.getX(), sortButton.getY(),
				sortByButton.getX() + sortByButton.getWidth() - sortButton.getX(), sortByButton.getY() + sortByButton.getHeight() - sortButton.getY()));
	}

	private void initUpgradeSettingsControl() {
		settingsTabControl = new UpgradeSettingsTabControl(new Position(guiLeft + xSize, guiTop + 4), this);
		addListener(settingsTabControl);
	}

	@Override
	public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		container.detectSettingsChangeAndReload();
		renderBackground(matrixStack);
		settingsTabControl.render(matrixStack, mouseX, mouseY, partialTicks);
		matrixStack.translate(0, 0, 200);
		super.render(matrixStack, mouseX, mouseY, partialTicks);
		settingsTabControl.afterScreenRender(matrixStack, mouseX, mouseY, partialTicks);
		if (sortButton != null && sortByButton != null) {
			sortButton.render(matrixStack, mouseX, mouseY, partialTicks);
			sortByButton.render(matrixStack, mouseX, mouseY, partialTicks);
		}
		upgradeSwitches.forEach(us -> us.render(matrixStack, mouseX, mouseY, partialTicks));
		renderErrorOverlay(matrixStack);
		renderHoveredTooltip(matrixStack, mouseX, mouseY);
	}

	@Override
	protected void drawGuiContainerForegroundLayer(MatrixStack matrixStack, int mouseX, int mouseY) {
		super.drawGuiContainerForegroundLayer(matrixStack, mouseX, mouseY);
		renderUpgradeInventoryParts(matrixStack, mouseX, mouseY);
		renderUpgradeSlots(matrixStack, mouseX, mouseY);
		renderRealInventorySlots(matrixStack, mouseX, mouseY);
	}

	private void renderUpgradeInventoryParts(MatrixStack matrixStack, int mouseX, int mouseY) {
		inventoryParts.values().forEach(ip -> ip.render(matrixStack, mouseX, mouseY));
	}

	private void renderRealInventorySlots(MatrixStack matrixStack, int mouseX, int mouseY) {
		for (int slotId = 0; slotId < container.realInventorySlots.size(); ++slotId) {
			Slot slot = container.realInventorySlots.get(slotId);
			moveItems(matrixStack, slot);

			if (isSlotSelected(slot, mouseX, mouseY) && slot.isEnabled()) {
				hoveredSlot = slot;
				renderSlotOverlay(matrixStack, slot, getSlotColor(slotId));
			}
		}
	}

	private void renderUpgradeSlots(MatrixStack matrixStack, int mouseX, int mouseY) {
		for (int slotId = 0; slotId < container.upgradeSlots.size(); ++slotId) {
			Slot slot = container.upgradeSlots.get(slotId);
			if (slot.xPos != DISABLED_SLOT_X_POS) {
				moveItems(matrixStack, slot);
				if (!slot.isEnabled()) {
					renderSlotOverlay(matrixStack, slot, DISABLED_SLOT_COLOR);
				}
			}

			if (isSlotSelected(slot, mouseX, mouseY) && slot.isEnabled()) {
				hoveredSlot = slot;
				renderSlotOverlay(matrixStack, slot, getSlotColor(slotId));
			}
		}
	}

	@Override
	protected void moveItems(MatrixStack matrixStack, Slot slot) {
		int i = slot.xPos;
		int j = slot.yPos;
		ItemStack itemstack = slot.getStack();
		boolean flag = false;
		boolean rightClickDragging = slot == clickedSlot && !draggedStack.isEmpty() && !isRightMouseClick;
		//noinspection ConstantConditions - player is not null at this point for sure
		ItemStack itemstack1 = minecraft.player.inventory.getItemStack();
		String stackCountText = null;
		if (slot == clickedSlot && !draggedStack.isEmpty() && isRightMouseClick && !itemstack.isEmpty()) {
			itemstack = itemstack.copy();
			itemstack.setCount(itemstack.getCount() / 2);
		} else if (dragSplitting && dragSplittingSlots.contains(slot) && !itemstack1.isEmpty()) {
			if (dragSplittingSlots.size() == 1) {
				return;
			}

			if (BackpackContainer.canMergeItemToSlot(slot, itemstack1) && container.canDragIntoSlot(slot)) {
				itemstack = itemstack1.copy();
				flag = true;
				Container.computeStackSize(dragSplittingSlots, dragSplittingLimit, itemstack, slot.getStack().isEmpty() ? 0 : slot.getStack().getCount());
				int slotLimit = slot.getItemStackLimit(itemstack);
				if (itemstack.getCount() > slotLimit) {
					stackCountText = TextFormatting.YELLOW.toString() + CountAbbreviator.abbreviate(slotLimit);
					itemstack.setCount(slotLimit);
				}
			} else {
				dragSplittingSlots.remove(slot);
				updateDragSplitting();
			}
		}

		setBlitOffset(100);
		itemRenderer.zLevel = 100.0F;
		if (itemstack.isEmpty() && slot.isEnabled()) {
			Pair<ResourceLocation, ResourceLocation> pair = slot.getBackground();
			if (pair != null) {
				TextureAtlasSprite textureatlassprite = minecraft.getAtlasSpriteGetter(pair.getFirst()).apply(pair.getSecond());
				minecraft.getTextureManager().bindTexture(textureatlassprite.getAtlasTexture().getTextureLocation());
				blit(matrixStack, i, j, getBlitOffset(), 16, 16, textureatlassprite);
				rightClickDragging = true;
			}
		}

		if (!rightClickDragging) {
			if (flag) {
				fill(matrixStack, i, j, i + 16, j + 16, -2130706433);
			}

			RenderSystem.enableDepthTest();
			itemRenderer.renderItemAndEffectIntoGUI(minecraft.player, itemstack, i, j);
			if (shouldUseSpecialCountRender(itemstack)) {
				itemRenderer.renderItemOverlayIntoGUI(font, itemstack, i, j, "");
				if (stackCountText == null) {
					stackCountText = CountAbbreviator.abbreviate(itemstack.getCount());
				}
				renderStackCount(stackCountText, i, j);
			} else {
				itemRenderer.renderItemOverlayIntoGUI(font, itemstack, i, j, stackCountText);
			}
		}

		itemRenderer.zLevel = 0.0F;
		setBlitOffset(0);
	}

	private boolean shouldUseSpecialCountRender(ItemStack itemstack) {
		return itemstack.getCount() > 99;
	}

	private void renderSlotOverlay(MatrixStack matrixStack, Slot slot, int slotColor) {
		renderOverlay(matrixStack, slotColor, slot.xPos, slot.yPos, 16, 16);
	}

	public void renderOverlay(MatrixStack matrixStack, int slotColor, int xPos, int yPos, int width, int height) {
		RenderSystem.disableDepthTest();
		RenderSystem.colorMask(true, true, true, false);
		fillGradient(matrixStack, xPos, yPos, xPos + width, yPos + height, slotColor, slotColor);
		RenderSystem.colorMask(true, true, true, true);
		RenderSystem.enableDepthTest();
	}

	protected void drawGuiContainerBackgroundLayer(MatrixStack matrixStack, float partialTicks, int x, int y) {
		drawInventoryBackground(matrixStack);
		drawUpgradeBackground(matrixStack);
	}

	@Override
	protected void renderHoveredTooltip(MatrixStack matrixStack, int x, int y) {
		if (minecraft.player.inventory.getItemStack().isEmpty() && hoveredSlot != null) {
			if (hoveredSlot.getHasStack()) {
				renderTooltip(matrixStack, hoveredSlot.getStack(), x, y);
			} else if (hoveredSlot instanceof INameableEmptySlot) {
				INameableEmptySlot emptySlot = (INameableEmptySlot) hoveredSlot;
				if (emptySlot.hasEmptyTooltip()) {
					renderWrappedToolTip(matrixStack, Collections.singletonList(emptySlot.getEmptyTooltip()), x, y, font);
				}
			}
		}
		GuiHelper.renderTooltip(minecraft, matrixStack, x, y);
	}

	@Override
	public List<ITextComponent> getTooltipFromItem(ItemStack itemStack) {
		List<ITextComponent> ret = super.getTooltipFromItem(itemStack);
		if (itemStack.getCount() > 999) {
			ret.add(new TranslationTextComponent("gui.sophisticatedbackpacks.tooltip.stack_count",
					new StringTextComponent(NumberFormat.getNumberInstance().format(itemStack.getCount())).mergeStyle(TextFormatting.DARK_AQUA))
					.mergeStyle(TextFormatting.GRAY)
			);
		}
		return ret;
	}

	private void drawInventoryBackground(MatrixStack matrixStack) {
		BackpackBackgroundProperties backpackBackgroundProperties = getContainer().getBackpackBackgroundProperties();
		BackpackGuiHelper.renderBackpackBackground(new Position((width - xSize) / 2, (height - ySize) / 2), matrixStack, getContainer().getNumberOfSlots(), getContainer().getSlotsOnLine(), backpackBackgroundProperties.getTextureName(), xSize, minecraft, container.getNumberOfRows());

		RenderSystem.pushMatrix();
		RenderSystem.translatef(getGuiLeft(), (float) getGuiTop(), 0.0F);
		for (int slotNumber = 0; slotNumber < container.getNumberOfSlots(); slotNumber++) {
			List<Integer> colors = container.getSlotOverlayColors(slotNumber);
			if (!colors.isEmpty()) {
				renderSlotOverlay(matrixStack, container.getSlot(slotNumber), colors.get(0) | (80 << 24)); //TODO needs to support more colors later
			}
		}
		RenderSystem.popMatrix();
	}

	private void drawUpgradeBackground(MatrixStack matrixStack) {
		if (numberOfUpgradeSlots == 0) {
			return;
		}

		RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		minecraft.getTextureManager().bindTexture(GUI_CONTROLS);

		int firstHalfHeight = getUpgradeHeightWithoutBottom();

		blit(matrixStack, guiLeft - UPGRADE_INVENTORY_OFFSET, guiTop + getUpgradeTop(), 0, 0, 29, firstHalfHeight, 256, 256);
		blit(matrixStack, guiLeft - UPGRADE_INVENTORY_OFFSET, guiTop + getUpgradeTop() + firstHalfHeight, 0, (float) TOTAL_UPGRADE_GUI_HEIGHT - UPGRADE_BOTTOM_HEIGHT, 29, UPGRADE_BOTTOM_HEIGHT, 256, 256);
	}

	public int getUpgradeTop() {
		return ySize - 94 - getUpgradeHeight();
	}

	public int getUpgradeHeight() {
		return getUpgradeHeightWithoutBottom() + UPGRADE_TOP_HEIGHT;
	}

	private int getUpgradeHeightWithoutBottom() {
		return UPGRADE_BOTTOM_HEIGHT + numberOfUpgradeSlots * UPGRADE_SLOT_HEIGHT + (numberOfUpgradeSlots - 1) * UPGRADE_SPACE_BETWEEN_SLOTS;
	}

	public UpgradeSettingsTabControl getUpgradeSettingsControl() {
		if (settingsTabControl == null) {
			settingsTabControl = new UpgradeSettingsTabControl(new Position(guiLeft + xSize, guiTop + 4), this);
		}
		return settingsTabControl;
	}

	@Nullable
	@Override
	protected Slot getSelectedSlot(double mouseX, double mouseY) {
		for (int i = 0; i < container.upgradeSlots.size(); ++i) {
			Slot slot = container.upgradeSlots.get(i);
			if (isSlotSelected(slot, mouseX, mouseY) && slot.isEnabled()) {
				return slot;
			}
		}

		for (int i = 0; i < container.realInventorySlots.size(); ++i) {
			Slot slot = container.realInventorySlots.get(i);
			if (isSlotSelected(slot, mouseX, mouseY) && slot.isEnabled()) {
				return slot;
			}
		}

		return super.getSelectedSlot(mouseX, mouseY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		for (UpgradeInventoryPartBase<?> inventoryPart : inventoryParts.values()) {
			if (inventoryPart.handleMouseReleased(mouseX, mouseY, button)) {
				return true;
			}
		}

		Slot slot = getSelectedSlot(mouseX, mouseY);
		if (doubleClick && slot != null && button == 0 && container.canMergeSlot(ItemStack.EMPTY, slot) && hasShiftDown() && !shiftClickedSlot.isEmpty()) {
			for (Slot slot2 : container.upgradeSlots) {
				if (slot2 != null && slot2.canTakeStack(minecraft.player) && slot2.getHasStack() && slot2.isSameInventory(slot) && BackpackContainer.canMergeItemToSlot(slot2, shiftClickedSlot)) {
					handleMouseClick(slot2, slot2.slotNumber, button, ClickType.QUICK_MOVE);
					return true;
				}
			}
		}

		if (doubleClick && slot != null && button == 0 && container.canMergeSlot(ItemStack.EMPTY, slot) && hasShiftDown() && !shiftClickedSlot.isEmpty()) {
			for (Slot slot2 : container.realInventorySlots) {
				if (slot2 != null && slot2.canTakeStack(minecraft.player) && slot2.getHasStack() && slot2.isSameInventory(slot) && Container.canAddItemToSlot(slot2, shiftClickedSlot, true)) {
					handleMouseClick(slot2, slot2.slotNumber, button, ClickType.QUICK_MOVE);
				}
			}
		}

		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	protected void handleMouseClick(Slot slot, int slotNumber, int mouseButton, ClickType type) {
		if (type == ClickType.PICKUP_ALL && !container.getSlotUpgradeContainer(slot).map(c -> c.allowsPickupAll(slot)).orElse(true)) {
			type = ClickType.PICKUP;
		}
		if (slot != null) {
			slotNumber = slot.slotNumber;
		}
		ClientPlayerEntity player = minecraft.player;

		short nextTransId = player.openContainer.getNextTransactionID(player.inventory);
		ItemStack itemstack = player.openContainer.slotClick(slotNumber, mouseButton, type, player);
		PacketHandler.sendToServer(new WindowClickMessage(container.windowId, slotNumber, mouseButton, type, itemstack, nextTransId));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		Slot slot = getSelectedSlot(mouseX, mouseY);
		if (hasShiftDown() && hasControlDown() && slot instanceof BackpackInventorySlot && button == 0) {
			PacketHandler.sendToServer(new TransferFullSlotMessage(slot.slotNumber));
			return true;
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		for (IGuiEventListener child : children) {
			if (child.isMouseOver(mouseX, mouseY) && child.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
				return true;
			}
		}
		Slot slot = getSelectedSlot(mouseX, mouseY);
		ItemStack itemstack = minecraft.player.inventory.getItemStack();
		if (dragSplitting && slot != null && !itemstack.isEmpty()
				&& (itemstack.getCount() > dragSplittingSlots.size() || dragSplittingLimit == 2)
				&& BackpackContainer.canMergeItemToSlot(slot, itemstack) && slot.isItemValid(itemstack)
				&& container.canDragIntoSlot(slot)) {
			dragSplittingSlots.add(slot);
			updateDragSplitting();
		}

		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeftIn, int guiTopIn, int mouseButton) {
		return super.hasClickedOutside(mouseX, mouseY, guiLeftIn, guiTopIn, mouseButton) && hasClickedOutsideOfUpgradeSlots(mouseX, mouseY)
				&& hasClickedOutsideOfUpgradeSettings(mouseX, mouseY);
	}

	private boolean hasClickedOutsideOfUpgradeSettings(double mouseX, double mouseY) {
		return settingsTabControl.getTabRectangles().stream().noneMatch(r -> r.contains((int) mouseX, (int) mouseY));
	}

	private boolean hasClickedOutsideOfUpgradeSlots(double mouseX, double mouseY) {
		return !getUpgradeSlotsRectangle().map(r -> r.contains((int) mouseX, (int) mouseY)).orElse(false);
	}

	public Optional<Rectangle2d> getUpgradeSlotsRectangle() {
		return numberOfUpgradeSlots == 0 ? Optional.empty() : Optional.of(new Rectangle2d(guiLeft - BackpackScreen.UPGRADE_INVENTORY_OFFSET, guiTop + getUpgradeTop(), 32, getUpgradeHeight()));
	}

	private void renderStackCount(String count, int x, int y) {
		MatrixStack matrixStack = new MatrixStack();
		matrixStack.translate(0.0D, 0.0D, itemRenderer.zLevel + 200.0F);
		IRenderTypeBuffer.Impl renderBuffer = IRenderTypeBuffer.getImpl(Tessellator.getInstance().getBuffer());

		RenderSystem.pushMatrix();
		float scale = Math.min(1f, (float) 16 / font.getStringWidth(count));
		if (scale < 1f) {
			RenderSystem.scalef(scale, scale, 1.0F);
		}
		font.renderString(count, (x + 19 - 2 - (font.getStringWidth(count) * scale)) / scale,
				(y + 6 + 3 + (1 / (scale * scale) - 1)) / scale, 16777215, true, matrixStack.getLast().getMatrix(), renderBuffer, false, 0, 15728880);
		renderBuffer.finish();
		RenderSystem.popMatrix();
	}

	@Override
	protected void updateDragSplitting() {
		//noinspection ConstantConditions - can't happen here as player is definitely known
		ItemStack cursorStack = minecraft.player.inventory.getItemStack();
		if (!cursorStack.isEmpty() && dragSplitting) {
			if (dragSplittingLimit == 2) {
				dragSplittingRemnant = cursorStack.getMaxStackSize();
			} else {
				dragSplittingRemnant = cursorStack.getCount();

				for (Slot slot : dragSplittingSlots) {
					ItemStack itemstack1 = cursorStack.copy();
					ItemStack slotStack = slot.getStack();
					int slotStackCount = slotStack.isEmpty() ? 0 : slotStack.getCount();
					Container.computeStackSize(dragSplittingSlots, dragSplittingLimit, itemstack1, slotStackCount);
					int j = slot.getItemStackLimit(itemstack1);
					if (itemstack1.getCount() > j) {
						itemstack1.setCount(j);
					}

					dragSplittingRemnant -= itemstack1.getCount() - slotStackCount;
				}
			}
		}
	}

	public static final int ERROR_BACKGROUND_COLOR = 0xF0100010;
	public static final int ERROR_BORDER_COLOR = DyeColor.RED.getColorValue() | 0xFF000000;

	private void renderErrorOverlay(MatrixStack matrixStack) {
		container.getErrorUpgradeSlotChangeResult().ifPresent(upgradeSlotChangeResult -> upgradeSlotChangeResult.getErrorMessage().ifPresent(overlayErrorMessage -> {
			RenderSystem.pushMatrix();
			RenderSystem.translatef(getGuiLeft(), (float) getGuiTop(), 0.0F);
			upgradeSlotChangeResult.getErrorUpgradeSlots().forEach(slotIndex -> renderSlotOverlay(matrixStack, container.getSlot(container.getFirstUpgradeSlot() + slotIndex), DyeColor.RED.getColorValue() | 0xAA000000));
			upgradeSlotChangeResult.getErrorInventorySlots().forEach(slotIndex -> renderSlotOverlay(matrixStack, container.getSlot(slotIndex), DyeColor.RED.getColorValue() | 0xAA000000));
			upgradeSlotChangeResult.getErrorInventoryParts().forEach(slotIndex -> inventoryParts.get(slotIndex).renderErrorOverlay(matrixStack));
			RenderSystem.popMatrix();

			renderErrorMessage(matrixStack, overlayErrorMessage);
		}));
	}

	private void renderErrorMessage(MatrixStack matrixStack, ITextComponent overlayErrorMessage) {
		RenderSystem.pushMatrix();
		RenderSystem.disableDepthTest();
		RenderSystem.translatef((float) width / 2, guiTop + playerInventoryTitleY + 4, 300F);
		FontRenderer fontrenderer = Minecraft.getInstance().fontRenderer;

		int tooltipWidth = font.getStringPropertyWidth(overlayErrorMessage);

		List<ITextProperties> wrappedTextLines = new ArrayList<>();
		int maxLineWidth = 260;
		if (tooltipWidth > maxLineWidth) {
			int wrappedTooltipWidth = 0;
			List<ITextProperties> wrappedLine = font.getCharacterManager().func_238362_b_(overlayErrorMessage, maxLineWidth, Style.EMPTY);

			for (ITextProperties line : wrappedLine) {
				int lineWidth = font.getStringPropertyWidth(line);
				if (lineWidth > wrappedTooltipWidth) { wrappedTooltipWidth = lineWidth; }
				wrappedTextLines.add(line);
			}
			tooltipWidth = wrappedTooltipWidth;
		} else {
			wrappedTextLines.add(overlayErrorMessage);
		}

		int tooltipHeight = 8;
		if (wrappedTextLines.size() > 1) {
			tooltipHeight += 2 + (wrappedTextLines.size() - 1) * 10;
		}

		Matrix4f matrix4f = matrixStack.getLast().getMatrix();
		float leftX = (float) -tooltipWidth / 2;

		GuiHelper.renderTooltipBackground(matrix4f, tooltipWidth, (int) leftX, 0, tooltipHeight, ERROR_BACKGROUND_COLOR, ERROR_BORDER_COLOR, ERROR_BORDER_COLOR);
		IRenderTypeBuffer.Impl renderTypeBuffer = IRenderTypeBuffer.getImpl(Tessellator.getInstance().getBuffer());
		matrixStack.translate(0.0D, 0.0D, 400.0D);
		GuiHelper.writeTooltipLines(wrappedTextLines, fontrenderer, leftX, 0, matrix4f, renderTypeBuffer, DyeColor.RED.getColorValue());
		renderTypeBuffer.finish();
		RenderSystem.popMatrix();
	}
}
