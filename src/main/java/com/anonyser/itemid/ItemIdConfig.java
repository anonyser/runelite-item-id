package com.anonyser.itemid;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ItemIdConfig.GROUP)
public interface ItemIdConfig extends Config
{
	String GROUP = "itemid";

	@ConfigItem(
		keyName = "openSearchPanel",
		name = "Open item search panel",
		description = "Tick to open the item search panel on the right<br>"
			+ "(the magnifier button in the sidebar). It opens<br>"
			+ "the panel, then unticks itself.",
		position = 0
	)
	default boolean openSearchPanel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "itemIdOnHover",
		name = "Item ID on hover",
		description = "Add each item's ID in parentheses to its hover text,<br>"
			+ "for example Dragon dagger (1215).<br>"
			+ "Item search is in the side panel (magnifier icon).",
		position = 1
	)
	default boolean itemIdOnHover()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showWikiDescription",
		name = "Wiki description in lookup",
		description = "In the search panel, fetch a short description of<br>"
			+ "the item from the OSRS wiki when you click it.<br>"
			+ "This makes a web request per item; turn it off<br>"
			+ "to keep the lookup fully offline.",
		position = 2
	)
	default boolean showWikiDescription()
	{
		return true;
	}
}
