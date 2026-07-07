package com.anonyser.itemid;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(
	name = "Item ID and Lookup",
	description = "Show an item's ID on hover, and search items for their ID and GE price",
	tags = {"item", "id", "ids", "lookup", "search", "price", "ge", "wiki"}
)
public class ItemIdPlugin extends Plugin
{
	// White so the appended id stands out from the item's own colour in the hover text.
	private static final String ID_COLOR = "<col=ffffff>";

	@Inject
	private ItemIdConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	private ItemLookupPanel panel;
	private NavigationButton navButton;

	@Provides
	ItemIdConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ItemIdConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new ItemLookupPanel(itemManager);
		navButton = NavigationButton.builder()
			.tooltip("Item lookup")
			.icon(navIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.itemIdOnHover())
		{
			return;
		}
		final MenuEntry entry = event.getMenuEntry();
		final int id = itemId(entry);
		if (id <= 0)
		{
			return;
		}
		entry.setTarget(withId(entry.getTarget(), id));
	}

	/**
	 * The item id an entry refers to, or -1 if it isn't an item. Inventory, bank and worn items
	 * report it via {@link MenuEntry#getItemId()}; ground items carry it in the identifier instead.
	 */
	private static int itemId(MenuEntry entry)
	{
		final int id = entry.getItemId();
		if (id > 0)
		{
			return id;
		}
		switch (entry.getType())
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
				return entry.getIdentifier();
			default:
				return -1;
		}
	}

	/** Appends " (id)" to a menu target, leaving empty targets and non-items untouched. */
	static String withId(String target, int id)
	{
		if (target == null || target.isEmpty() || id <= 0)
		{
			return target;
		}
		return target + ID_COLOR + " (" + id + ")";
	}

	/** A small magnifying-glass icon for the side-panel toolbar button. */
	private static BufferedImage navIcon()
	{
		final BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(220, 220, 220));
		g.setStroke(new BasicStroke(2f));
		g.drawOval(4, 4, 11, 11);
		g.drawLine(14, 14, 20, 20);
		g.dispose();
		return img;
	}
}
