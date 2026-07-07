package com.anonyser.itemid;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "Item ID",
	description = "Shows an item's ID in parentheses when you hover over it",
	tags = {"item", "id", "ids", "lookup", "wiki", "dev"}
)
public class ItemIdPlugin extends Plugin
{
	// White so the appended id stands out from the item's own colour in the hover text.
	private static final String ID_COLOR = "<col=ffffff>";

	@Inject
	private ItemIdConfig config;

	@Provides
	ItemIdConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ItemIdConfig.class);
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
}
