package com.anonyser.itemid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Repair-on-death (broken) costs for untradeables, looked up by item name. The data is the same
 * OSRS-wiki-verified list the PvP profit tracker uses; here it just adds a "repair" figure to the
 * item lookup. Lines are "item name,cost"; blank lines and lines starting with # are ignored.
 */
class RepairCosts
{
	private final Map<String, Integer> costs = new HashMap<>();

	static RepairCosts load()
	{
		final RepairCosts r = new RepairCosts();
		try (InputStream in = RepairCosts.class.getResourceAsStream("/com/anonyser/itemid/repair-costs.csv"))
		{
			if (in != null)
			{
				try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
				{
					String line;
					while ((line = br.readLine()) != null)
					{
						r.add(line);
					}
				}
			}
		}
		catch (IOException e)
		{
			// leave whatever parsed; a missing repair figure just isn't shown
		}
		return r;
	}

	void add(String line)
	{
		if (line == null)
		{
			return;
		}
		final String trimmed = line.trim();
		if (trimmed.isEmpty() || trimmed.startsWith("#"))
		{
			return;
		}
		final int comma = trimmed.lastIndexOf(',');
		if (comma <= 0 || comma == trimmed.length() - 1)
		{
			return;
		}
		final String name = trimmed.substring(0, comma).trim().toLowerCase();
		try
		{
			costs.put(name, Integer.parseInt(trimmed.substring(comma + 1).trim()));
		}
		catch (NumberFormatException e)
		{
			// skip a malformed row rather than fail the whole file
		}
	}

	/** The repair cost for an item name, or null if it isn't a known repairable untradeable. */
	Integer cost(String itemName)
	{
		return itemName == null ? null : costs.get(itemName.toLowerCase());
	}

	int size()
	{
		return costs.size();
	}
}
