package com.anonyser.itemid;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ItemIdConfig.GROUP)
public interface ItemIdConfig extends Config
{
	String GROUP = "itemid";

	@ConfigItem(
		keyName = "itemIdOnHover",
		name = "Item ID on hover",
		description = "Add each item's ID in parentheses to its hover text,<br>"
			+ "for example Dragon dagger (1215).<br>"
			+ "Item search is in the side panel (magnifier icon)."
	)
	default boolean itemIdOnHover()
	{
		return true;
	}
}
